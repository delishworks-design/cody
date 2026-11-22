package com.termux.app.features

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.termux.app.install.TermuxPaths
import com.termux.app.shell.ShellRunner
import com.termux.app.shell.TermuxShellEnvironment
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    private const val TAR_NAME = "cody-backup.tgz"
    private const val PREFS_NAME = "cody-prefs.json"
    private val PREFS = listOf("cody_features", "cody_credentials", "opencode_titles")
    private val UNSAFE_CHARS = setOf('\'', '"', '\\', '$', '`', ';', '&', '|', '*', '?', '[', ']', '<', '>', '(', ')')

    suspend fun export(context: Context, onLine: (String) -> Unit): Boolean {
        val work = File(context.cacheDir, "cody-backup-${System.currentTimeMillis()}-${(Math.random() * 1_000_000).toInt()}").apply { mkdirs() }
        val sharedDir = File(TermuxPaths.TMP).apply { mkdirs() }
        val sharedTar = File(sharedDir, TAR_NAME)
        val sharedPrefs = File(sharedDir, PREFS_NAME)
        return try {
            onLine("Saving app settings")
            val prefs = File(work, PREFS_NAME)
            prefs.writeText(prefsJson(context))
            prefs.copyTo(sharedPrefs, overwrite = true)

            val script = exportScript()
            onLine("Packing the container and workspace")
            val rc = ShellRunner.run(command(script), env(), TermuxPaths.HOME, onLine)
            if (rc != 0 && rc != 1) {
                onLine("! archive failed (exit $rc)")
                return false
            }
            if (!sharedTar.isFile || sharedTar.length() == 0L) {
                onLine("! no archive was produced")
                return false
            }

            val local = File(work, TAR_NAME)
            sharedTar.copyTo(local, overwrite = true)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val name = "cody-backup-$stamp.tar.gz"
            onLine("Saving $name to Downloads")
            val saved = saveToDownloads(context, local, name)
            if (saved) onLine("Settings are included in the archive") else onLine("! could not write to Downloads")
            saved
        } catch (e: Exception) {
            onLine("! ${e.message}")
            false
        } finally {
            sharedTar.delete()
            sharedPrefs.delete()
            work.deleteRecursively()
        }
    }

    suspend fun restore(context: Context, uri: Uri, onLine: (String) -> Unit): Boolean {
        val work = File(context.cacheDir, "cody-restore-${System.currentTimeMillis()}-${(Math.random() * 1_000_000).toInt()}").apply { mkdirs() }
        val sharedTar = File(File(TermuxPaths.TMP).apply { mkdirs() }, TAR_NAME)
        return try {
            onLine("Copying the backup")
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                onLine("! could not read the chosen file")
                return false
            }
            val local = File(work, TAR_NAME)
            input.use { source -> FileOutputStream(local).use { out -> source.copyTo(out) } }

            val magic = local.inputStream().use { s -> intArrayOf(s.read(), s.read()) }
            if (magic[0] != 0x1f || magic[1] != 0x8b) {
                onLine("! not a gzip archive")
                return false
            }

            local.copyTo(sharedTar, overwrite = true)
            onLine("Unpacking into the container")
            val script = "tar -xzf ${shellPath("/tmp/$TAR_NAME")} -C /"
            val rc = ShellRunner.run(command(script), env(), TermuxPaths.HOME, onLine)
            if (rc != 0) {
                onLine("! unpack failed (exit $rc)")
                return false
            }
            onLine("Archive unpacked")
            runCatching {
                findPrefs()?.let { file ->
                    applyPrefs(context, file.readText())
                    file.delete()
                    onLine("Settings restored")
                }
            }
            true
        } catch (e: Exception) {
            onLine("! ${e.message}")
            false
        } finally {
            sharedTar.delete()
            work.deleteRecursively()
        }
    }

    private fun command(script: String): List<String> = listOf(
        TermuxPaths.PROOT_DISTRO, "login", "debian", "--shared-tmp", "--", "/bin/bash", "-lc", script,
    )

    private fun env(): Array<String> = TermuxShellEnvironment.build(TermuxPaths.HOME)

    private fun shellPath(path: String): String {
        require(path.startsWith("/") && path.none { it.isWhitespace() || it in UNSAFE_CHARS }) {
            "Unsafe shell path: $path"
        }
        return path
    }

    private fun exportScript(): String {
        val out = shellPath("/tmp/$TAR_NAME")
        val home = shellPath(TermuxPaths.HOME)
        val base = StringBuilder("tar -czf $out")
        base.append(" --exclude='root/.cache' --exclude='root/.npm' --exclude='*/node_modules'")
        base.append(" --exclude='home/.cache' --exclude='home/node_modules'")
        base.append(" -C / root")
        val prefs = " -C /tmp ${shellPath(PREFS_NAME)}"
        return """
            rm -f $out
            if [ -d $home ]; then
              $base -C $home home$prefs
            else
              $base$prefs
            fi
        """.trimIndent()
    }

    private fun prefsJson(context: Context): String {
        val root = JSONObject()
        for (name in PREFS) {
            val obj = JSONObject()
            for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                when (value) {
                    is String -> obj.put(key, "s:$value")
                    is Boolean -> obj.put(key, "b:$value")
                    is Long -> obj.put(key, "l:$value")
                    is Int -> obj.put(key, "i:$value")
                    is Float -> obj.put(key, "f:$value")
                    else -> Unit
                }
            }
            root.put(name, obj)
        }
        return root.toString()
    }

    private fun applyPrefs(context: Context, text: String) {
        val root = JSONObject(text)
        for (name in PREFS) {
            val obj = root.optJSONObject(name) ?: continue
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val raw = obj.optString(key)
                when {
                    raw.startsWith("s:") -> editor.putString(key, raw.substring(2))
                    raw.startsWith("b:") -> editor.putBoolean(key, raw.substring(2).toBoolean())
                    raw.startsWith("l:") -> editor.putLong(key, raw.substring(2).toLongOrNull() ?: continue)
                    raw.startsWith("i:") -> editor.putInt(key, raw.substring(2).toIntOrNull() ?: continue)
                    raw.startsWith("f:") -> editor.putFloat(key, raw.substring(2).toFloatOrNull() ?: continue)
                }
            }
            editor.commit()
        }
    }

    private fun findPrefs(): File? = listOf(
        TermuxPaths.DEBIAN_CONTAINER_ROOTFS,
        TermuxPaths.DEBIAN_LEGACY_ROOTFS,
    ).map { File(it, PREFS_NAME) }.firstOrNull { it.isFile }
        ?: File(TermuxPaths.DEBIAN_CONTAINER_ROOTFS, "root/$PREFS_NAME").takeIf { it.isFile }

    private fun saveToDownloads(context: Context, source: File, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/gzip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cody")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            return try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: return false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Cody").apply { mkdirs() }
        return try {
            source.copyTo(File(dir, name), overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
