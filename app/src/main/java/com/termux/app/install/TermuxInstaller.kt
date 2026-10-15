package com.termux.app.install

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Extracts the embedded Termux rootfs bootstrap into the app's private files directory and runs the
 * bootstrap "second stage" (dpkg postinst maintainer scripts). This is what turns the installed APK
 * into a working Termux prefix at /data/data/com.termux/files/usr with an apt/dpkg package manager.
 */
class TermuxInstaller(private val context: Context) {

    companion object {
        private const val TAG = "TermuxInstaller"
        private const val SYMLINK_SEPARATOR = "\u2190" // Leftwards arrow used by Termux's SYMLINKS.txt
    }

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        File(TermuxPaths.BASH).canExecute()
    }

    /** Runs the full first + second stage. Safe to call repeatedly. */
    suspend fun install(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInstalledSync()) {
                onProgress("Termux environment already installed.")
                return@withContext true
            }

            onProgress("Extracting Termux rootfs…")
            deleteRecursively(File(TermuxPaths.STAGING_PREFIX))
            deleteRecursively(File(TermuxPaths.PREFIX))
            File(TermuxPaths.STAGING_PREFIX).mkdirs()
            File(TermuxPaths.HOME).mkdirs()

            val zipBytes = TermuxBootstrapNative.getZip()
                ?: throw IllegalStateException("Could not read embedded Termux bootstrap")

            val symlinks = ArrayList<Pair<String, String>>(64)
            val buffer = ByteArray(8192)

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "SYMLINKS.txt") {
                        // Read the entry contents directly. Do NOT wrap the ZipInputStream in a
                        // reader that closes it, or the rest of the archive cannot be read.
                        val baos = java.io.ByteArrayOutputStream()
                        val tmp = ByteArray(4096)
                        var n: Int
                        while (zip.read(tmp).also { n = it } != -1) baos.write(tmp, 0, n)
                        baos.toString("UTF-8").split("\n").forEach { line ->
                            val parts = line.split(SYMLINK_SEPARATOR)
                            if (parts.size == 2) {
                                val linkPath = TermuxPaths.STAGING_PREFIX + "/" + parts[1].trim()
                                File(linkPath).parentFile?.mkdirs()
                                symlinks.add(parts[0].trim() to linkPath)
                            }
                        }
                    } else {
                        val target = File(TermuxPaths.STAGING_PREFIX, name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                var read: Int
                                while (zip.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                            }
                            if (isExecutableEntry(name)) {
                                Os.chmod(target.absolutePath, 0b111_000_000) // 0700
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (symlinks.isEmpty()) throw IllegalStateException("No SYMLINKS.txt in bootstrap")
            onProgress("Creating ${symlinks.size} symlinks…")
            for ((oldPath, newPath) in symlinks) {
                try {
                    Os.symlink(oldPath, newPath)
                } catch (e: Exception) {
                    Log.w(TAG, "symlink failed $oldPath -> $newPath: ${e.message}")
                }
            }

            onProgress("Finalizing installation…")
            if (!File(TermuxPaths.STAGING_PREFIX).renameTo(File(TermuxPaths.PREFIX))) {
                throw IllegalStateException("Failed to move staging prefix into place")
            }

            runSecondStage(onProgress)
            onProgress("Termux environment ready.")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Bootstrap installation failed", t)
            onProgress("Installation failed: ${t.message}")
            false
        }
    }

    private suspend fun isInstalledSync(): Boolean = File(TermuxPaths.BASH).canExecute()

    private fun isExecutableEntry(name: String): Boolean =
        name.startsWith("bin/") ||
                name.startsWith("libexec") ||
                name.startsWith("lib/apt/apt-helper") ||
                name.startsWith("lib/apt/methods") ||
                name.contains("second-stage/termux-bootstrap-second-stage.sh")

    private suspend fun runSecondStage(onProgress: (String) -> Unit) {
        val script = when {
            File(TermuxPaths.SECOND_STAGE_NEW).exists() -> TermuxPaths.SECOND_STAGE_NEW
            File(TermuxPaths.SECOND_STAGE_OLD).exists() -> TermuxPaths.SECOND_STAGE_OLD
            else -> null
        }
        if (script == null) {
            Log.i(TAG, "No second stage script found; skipping.")
            return
        }
        onProgress("Running bootstrap second stage (postinst scripts)…")
        val env = com.termux.app.shell.TermuxShellEnvironment.build(TermuxPaths.HOME)
        val exit = com.termux.app.shell.ShellRunner.run(
            command = listOf(TermuxPaths.BASH, script),
            env = env,
            workingDirectory = TermuxPaths.HOME,
        ) { line -> if (line.isNotBlank()) Log.i(TAG, "bootstrap: $line") }
        Log.i(TAG, "Second stage exit code: $exit")
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }
}
