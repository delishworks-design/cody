package com.termux.app.features

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Remote-device pairing data layer.
 *
 * Phones, laptops and desktops can all run `opencode serve`. A scanned `cody://connect` link
 * carries the address and the one-time password; this object only validates, parses and stores
 * that information. It never touches the network — the caller does any `OpenCodeApi.probe` on
 * `Dispatchers.IO` before handing a device to [add].
 *
 * State lives in the "cody_features" preferences: a JSON array of devices and one active id.
 * An empty active id means the local phone server. The password is kept in plain preferences,
 * the same way cl0udly keeps it — the rootfs it protects is itself stored in the clear, so
 * wrapping just this copy would raise the cost of reading it at rest while leaving the real
 * secret beside it. `allowBackup` is already false.
 */
object RemoteDevices {
    data class Device(val id: String, val name: String, val url: String, val password: String)

    private const val PREFS = "cody_features"
    private const val KEY_DEVICES = "remote_devices"
    private const val KEY_ACTIVE = "remote_active"
    private const val MAX_DEVICES = 12
    private const val DEFAULT_PORT = 4096

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    /** Every stored device. A malformed preference yields an empty list, never a crash. */
    fun all(context: Context): List<Device> {
        val raw = prefs(context).getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<Device>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val url = obj.optString("url")
                if (id.isEmpty() || url.isEmpty()) continue
                out += Device(
                    id = id,
                    name = obj.optString("name"),
                    url = url,
                    password = obj.optString("password"),
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    /** The selected remote device, or null for the local phone server. */
    fun active(context: Context): Device? {
        val id = prefs(context).getString(KEY_ACTIVE, "").orEmpty()
        if (id.isEmpty()) return null
        return all(context).firstOrNull { it.id == id }
    }

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * Adds [device], replacing any entry with the same url and keeping at most [MAX_DEVICES].
     *
     * The url is normalised before comparison, so `host`, `host:4096` and `http://host:4096`
     * all collide. When replacing, the old id is kept so an active selection does not silently
     * fall back to the local server. New entries go last; the cap drops the oldest ones.
     */
    fun add(context: Context, device: Device) {
        val url = normalizeUrl(device.url) ?: return
        val current = all(context)
        val same = current.firstOrNull { it.url == url }
        val stored = Device(
            id = same?.id ?: device.id.ifBlank { newId() },
            name = device.name.trim().ifBlank { hostOf(url) },
            url = url,
            password = device.password,
        )
        val kept = current.filterNot { it.url == url } + stored
        save(context, if (kept.size > MAX_DEVICES) kept.takeLast(MAX_DEVICES) else kept)
    }

    /** Forgets a device. If it was active, selection returns to the local server. */
    fun remove(context: Context, id: String) {
        if (prefs(context).getString(KEY_ACTIVE, "") == id) select(context, null)
        save(context, all(context).filterNot { it.id == id })
    }

    /** Selects a device by id. Null or empty returns to the local server. */
    fun select(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_ACTIVE, id?.trim().orEmpty()).apply()
    }

    private fun save(context: Context, devices: List<Device>) {
        val array = JSONArray()
        for (device in devices) {
            array.put(
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("url", device.url)
                    .put("password", device.password),
            )
        }
        prefs(context).edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: url.substringAfter("://").substringBefore('/').substringBefore(':')

    // ---------------------------------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------------------------------

    /**
     * Parses `cody://connect?n=name&u=http%3A%2F%2F192.168.1.5%3A4096&p=password`.
     *
     * Only the address is required. The name falls back to the host, and the password may be
     * empty — a caller is expected to probe the server before committing the device.
     */
    fun parseLink(uri: Uri): Device? = runCatching {
        if (!uri.scheme.equals("cody", true) || !uri.host.equals("connect", true)) return null
        val rawUrl = uri.getQueryParameter("u")?.takeIf { it.isNotBlank() } ?: return null
        val url = normalizeUrl(rawUrl) ?: return null
        val name = uri.getQueryParameter("n")?.trim().orEmpty().ifBlank { hostOf(url) }
        Device(newId(), name, url, uri.getQueryParameter("p").orEmpty())
    }.getOrNull()

    /**
     * Canonical form `http://host:4096`, with a missing `http://` and port filled in.
     *
     * An explicit `https://` is preserved rather than silently downgraded. Anything that is
     * not an http(s) host — another scheme, a bad port, whitespace, credentials — is rejected.
     */
    fun normalizeUrl(raw: String): String? {
        var rest = raw.trim().trimEnd('/')
        if (rest.isEmpty()) return null

        var scheme = "http"
        when {
            rest.startsWith("http://", true) -> rest = rest.substring(7)
            rest.startsWith("https://", true) -> {
                scheme = "https"
                rest = rest.substring(8)
            }
            rest.contains("://") -> return null
        }
        if (rest.isEmpty()) return null

        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null

        val host: String
        val port: Int?
        if (authority[0] == '[') {
            val end = authority.indexOf(']')
            if (end < 0) return null
            host = authority.substring(0, end + 1)
            val tail = authority.substring(end + 1)
            when {
                tail.isEmpty() -> port = null
                tail[0] == ':' -> {
                    val parsed = tail.substring(1).toIntOrNull() ?: return null
                    if (parsed !in 1..65535) return null
                    port = parsed
                }
                else -> return null
            }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                host = authority.substring(0, colon)
                val parsed = authority.substring(colon + 1).toIntOrNull() ?: return null
                if (parsed !in 1..65535) return null
                port = parsed
            } else {
                host = authority
                port = null
            }
        }

        if (host.isEmpty()) return null
        if (host.startsWith("[")) {
            if (!host.endsWith("]")) return null
        } else if (host.any { it.isWhitespace() || it in "<>\"'{}|\\^`@:" }) {
            return null
        }
        return "$scheme://$host:${port ?: DEFAULT_PORT}"
    }

    // ---------------------------------------------------------------------------------------------
    // Pairing helper
    // ---------------------------------------------------------------------------------------------

    /**
     * A single POSIX shell line for the computer: generate a fresh password, find its LAN
     * address, print a QR of the connect link, then serve on 0.0.0.0.
     *
     * Adapted from cl0udly's `tools/connect-qr.sh` with the scheme renamed to `cody://`. The
     * password is generated with `od` from `/dev/urandom`, never hardcoded. Nothing is `eval`ed,
     * and the only external input is this machine's own hostname and routing table. Install
     * `qrencode` for a terminal QR; a Python `qrcode` module is the fallback.
     */
    fun inviteCommand(): String {
        val enc = """enc(){ printf %s "${'$'}1" | od -An -tx1 -v | tr -d '\n' | sed 's/ \{1,\}/%/g; s/^%//; s/^/%/'; }; """
        val address = """IP=${'$'}(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<NF;i++)if(${'$'}i=="src"){print ${'$'}(i+1);exit}}'); """
        val fallback = """[ -n "${'$'}IP" ] || IP=${'$'}(hostname -I 2>/dev/null | awk '{print ${'$'}1}'); """
        val guard = """[ -n "${'$'}IP" ] || { echo "No LAN address found"; exit 1; }; """
        val secret = """PASS=${'$'}(od -An -N12 -tx1 /dev/urandom | tr -d ' \n'); """
        val link = """LINK="cody://connect?n=${'$'}(enc "${'$'}NAME")&u=${'$'}(enc "http://${'$'}IP:${'$'}PORT")&p=${'$'}PASS"; """
        val qr = """if command -v qrencode >/dev/null 2>&1; then qrencode -t ansiutf8 -m 2 "${'$'}LINK"; elif command -v python3 >/dev/null 2>&1 && python3 -c 'import qrcode' 2>/dev/null; then python3 -c 'import sys,qrcode; q=qrcode.QRCode(border=2); q.add_data(sys.argv[1]); q.print_ascii(invert=True)' "${'$'}LINK"; else echo "Install qrencode to show a QR. Link: ${'$'}LINK"; fi; """
        val serve = """OPENCODE_SERVER_PASSWORD="${'$'}PASS" exec opencode serve --hostname 0.0.0.0 --port "${'$'}PORT""""

        return "PORT=$DEFAULT_PORT; " +
            """NAME=${'$'}(hostname 2>/dev/null || echo Computer); """ +
            enc + address + fallback + guard + secret + link + qr +
            """echo "Cody: Remote devices > Add device > Scan QR"; """ +
            serve
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)
}
