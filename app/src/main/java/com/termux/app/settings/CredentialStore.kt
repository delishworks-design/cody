package com.termux.app.settings

import android.content.Context
import android.system.Os
import android.util.Log
import com.termux.app.install.TermuxPaths
import org.json.JSONObject
import java.io.File

/**
 * Stores the user's service keys and hands them to the agent.
 *
 * ## Where the keys live, and why
 *
 * Two copies, deliberately, because two different things need to read them:
 *
 *  - **Here**, in the app's private preferences, so the Settings screen can show what is set
 *    without ever displaying it.
 *  - **In the Debian container**, as `/root/.cody-credentials.env` with mode 600, because that is
 *    the only place the agent and the CLIs can both reach.
 *
 * The container copy is plaintext, and that is not an oversight. The agent has to read it with
 * `bash`, and `gh` has to read `GH_TOKEN` from the environment; either one needs the real bytes.
 * Encrypting it would mean shipping a passphrase to the agent in the same message, which protects
 * nothing.
 *
 * That is also why the app's own copy is not Keystore-wrapped. The rootfs sits in app-private
 * storage in the clear, so wrapping this file would raise the cost of reading it at rest while
 * leaving a plaintext copy in the container right beside it — and Keystore-backed preferences have
 * their own failure mode: change the screen lock and the key is invalidated, silently losing every
 * key the user entered. A plain file that keeps working is the better trade.
 *
 * The keys are never logged, never put in a prompt, and `allowBackup` is already false, so they are
 * not in cloud backup either.
 */
object CredentialStore {

    private const val TAG = "CredentialStore"

    private const val PREFS = "cody_credentials"
    private const val ENTRY_PREFIX = "entry:"

    /** 0600 inside the container: owner read/write only. */
    private const val CONTAINER_MODE = 0x180

    /** Container-relative location. Under /root, so the agent is already there. */
    private const val CONTAINER_RELATIVE = "root/.cody-credentials.env"

    /** Absolute path as the agent sees it, for telling it where the file is. */
    const val CONTAINER_PATH = "/$CONTAINER_RELATIVE"

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Every credential: builtins in catalogue order, then custom entries alphabetically.
     *
     * A builtin with nothing stored is still returned, with an empty value — it is a row in the
     * Settings list either way, and whether it holds a key is a separate question the screen asks
     * through [secret]. An entry whose metadata cannot be parsed is skipped rather than thrown: one
     * malformed preference must not make the whole screen unusable.
     */
    fun all(context: Context): List<Credential> {
        val stored = HashMap<String, Credential>()
        for ((key, raw) in prefs(context).all) {
            if (!key.startsWith(ENTRY_PREFIX)) continue
            val id = key.substring(ENTRY_PREFIX.length)
            parse(id, raw as? String ?: continue)?.let { stored[id] = it }
        }

        val builtins = CredentialCatalog.BUILTINS.map { stored[it.id] ?: it }
        val custom = stored.values
            .filter { !it.builtin }
            .sortedBy { it.label.lowercase() }
        return builtins + custom
    }

    /** The stored value for [id], or null when nothing is set. */
    fun secret(context: Context, id: String): String? =
        prefs(context).getString(ENTRY_PREFIX + id, null)?.let { stored ->
            runCatching { JSONObject(stored).optString("value") }.getOrNull()
        }?.takeIf { it.isNotBlank() }

    fun isSet(context: Context, id: String): Boolean = secret(context, id) != null

    /** How many keys hold a value right now. */
    fun setCount(context: Context): Int = all(context).count { isSet(context, it.id) }

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * Stores [value] under [credential], then re-exports to the container.
     *
     * An empty or blank value clears the entry, so clearing a field is just emptying it.
     */
    fun save(context: Context, credential: Credential, value: String) {
        val key = ENTRY_PREFIX + credential.id
        if (value.isBlank()) {
            prefs(context).edit().remove(key).apply()
        } else {
            prefs(context).edit()
                .putString(
                    key,
                    JSONObject()
                        .put("label", credential.label)
                        .put("envVar", credential.envVar)
                        .put("url", credential.url)
                        .put("builtin", credential.builtin)
                        .put("value", value)
                        .toString(),
                )
                .apply()
        }
        exportToContainer(context)
    }

    fun remove(context: Context, id: String) {
        prefs(context).edit().remove(ENTRY_PREFIX + id).apply()
        exportToContainer(context)
    }

    private val RESERVED_ENV_VARS = setOf(
        "PATH", "LD_PRELOAD", "LD_LIBRARY_PATH", "BASH_ENV", "ENV",
        "HOME", "PREFIX", "TERM", "SHELL", "IFS", "PS1", "PS2",
    )

    /** Adds a user-created entry, or returns null when the name or env var is unusable. */
    fun addCustom(context: Context, label: String, envVar: String, value: String): Credential? {
        val cleanLabel = label.trim()
        val cleanEnv = envVar.trim().uppercase().replace(Regex("[^A-Z0-9_]"), "_").trim('_')
        if (cleanLabel.isEmpty() || cleanEnv.isEmpty()) return null
        if (cleanEnv in RESERVED_ENV_VARS) return null
        if (all(context).any { it.envVar == cleanEnv }) return null

        val credential = Credential(
            id = CredentialCatalog.customId(cleanLabel),
            label = cleanLabel,
            envVar = cleanEnv,
            builtin = false,
        )
        save(context, credential, value)
        return credential
    }

    /** Forgets every credential, in the app and in the container. */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching { containerFile().delete() }
            .onFailure { Log.w(TAG, "Could not remove the container file", it) }
    }

    // ---------------------------------------------------------------------------------------------
    // The container copy
    // ---------------------------------------------------------------------------------------------

    private fun containerFile(): File =
        File(TermuxPaths.DEBIAN_CONTAINER_ROOTFS, CONTAINER_RELATIVE)

    /**
     * Rewrites the container file from the stored values.
     *
     * Rewritten whole rather than appended, so a key the user removed cannot survive in the file.
     * Returns false when the container is not provisioned yet — the keys stay in preferences and the
     * next export picks them up.
     */
    fun exportToContainer(context: Context): Boolean {
        val file = containerFile()
        val values = all(context).mapNotNull { credential ->
            secret(context, credential.id)?.let { credential to it }
        }

        if (values.isEmpty()) {
            return !file.exists() || runCatching { file.delete() }.getOrDefault(false)
        }

        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            Log.w(TAG, "Could not create ${parent.absolutePath}")
            return false
        }

        val body = buildString {
            append("# Written by Cody, from the service keys you entered in Settings.\n")
            append("# Sourced into the shell. Do not commit this file.\n")
            append("# Set: ${values.joinToString(", ") { it.first.label.replace(Regex("[\\r\\n]"), " ") }}\\n\\n")
            for ((credential, value) in values) {
                append("export ").append(credential.envVar).append('=').append(shellQuote(value))
                append('\n')
            }
        }

        return try {
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(body)
            runCatching { Os.chmod(tmp.absolutePath, CONTAINER_MODE) }
                .onFailure { Log.w(TAG, "Could not chmod the credential file", it) }
            if (!tmp.renameTo(file)) {
                file.writeText(body)
                tmp.delete()
            }
            Log.i(TAG, "Exported ${values.size} credential(s) to the container")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not write the credential file", t)
            return false
        }
    }

    /**
     * Single-quotes a value for `source`.
     *
     * Single quotes suppress expansion, so a key containing `$` or a backtick cannot execute
     * anything when the file is sourced. Only the quote itself needs care.
     */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun parse(id: String, raw: String): Credential? = try {
        val json = JSONObject(raw)
        Credential(
            id = id,
            label = json.optString("label"),
            envVar = json.optString("envVar"),
            url = json.optString("url"),
            builtin = json.optBoolean("builtin", false),
        ).takeIf { it.label.isNotEmpty() && it.envVar.isNotEmpty() }
    } catch (t: Throwable) {
        Log.w(TAG, "Skipping unreadable credential entry: $id", t)
        null
    }

    // ---------------------------------------------------------------------------------------------
    // Telling the agent
    // ---------------------------------------------------------------------------------------------

    /**
     * The block appended to a prompt when credentials exist. Names only, never values: the agent
     * learns which variables it can source and where the file is, and is told not to print them. It
     * can still read a value when a task genuinely needs one — that is the point of storing it — but
     * the value is never pushed into the conversation.
     */
    fun agentNote(context: Context): String? {
        val set = all(context).filter { isSet(context, it.id) }
        if (set.isEmpty()) return null

        return buildString {
            append("The user has service credentials configured in Cody Settings: ")
            append(set.joinToString(", ") { "${it.label} (${it.envVar})" })
            append(".\n\n")
            append("They are exported into this shell, and also written to ")
            append(CONTAINER_PATH)
            append(" (mode 600). If a command needs one, run `source ")
            append(CONTAINER_PATH)
            append("` first. Never print a key's value back to the user.")
        }
    }
}