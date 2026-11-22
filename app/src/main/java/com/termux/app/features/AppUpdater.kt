package com.termux.app.features

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {
    const val REPO = "delishworks-design/cody"

    data class Release(val tag: String, val apkUrl: String, val notes: String)

    private const val USER_AGENT = "cody-app-updater/1.0"
    private const val CHUNK = 64 * 1024

    fun check(): Release? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = JSONObject(body)
            val assets: JSONArray = release.optJSONArray("assets") ?: return null

            var first: String? = null
            var arm64: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                if (first == null) first = url
                val lower = name.lowercase()
                val is64 = (lower.contains("arm64") || lower.contains("aarch64")) &&
                    !lower.contains("armv7") && !lower.contains("armeabi")
                if (is64 && arm64 == null) arm64 = url
            }

            Release(
                tag = release.optString("tag_name"),
                apkUrl = arm64 ?: first ?: return null,
                notes = release.optString("body"),
            )
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "latest" }
        val final = File(dir, "cody-$safeTag.apk")
        val part = File(final.absolutePath + ".part")
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val total = connection.contentLength.toLong()
            var done = 0L
            var last = -1
            val input: InputStream = connection.inputStream
            input.use { source ->
                FileOutputStream(part).use { out ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        val pct = if (total > 0) (done * 100L / total).coerceIn(0L, 100L).toInt() else 0
                        if (pct != last) {
                            last = pct
                            onProgress(pct)
                        }
                    }
                    out.flush()
                }
            }
            onProgress(100)
            if (!part.isFile || part.length() == 0L) return null
            if (final.exists() && !final.delete()) return null
            if (!part.renameTo(final)) return null
            final
        } catch (e: Exception) {
            part.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun install(context: Context, apk: File): Boolean {
        if (!apk.isFile || apk.length() == 0L) return false
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { source ->
                        val buffer = ByteArray(CHUNK)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                    session.fsync(out)
                }
                val intent = Intent("com.termux.app.APK_INSTALL_STATUS").setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(status.intentSender)
            }
            true
        } catch (e: Exception) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }
}
