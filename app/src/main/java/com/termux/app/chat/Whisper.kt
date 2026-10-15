package com.termux.app.chat

import android.content.Context
import android.util.Log
import com.termux.app.install.TermuxPaths
import com.termux.app.shell.TermuxShellEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs `whisper.cpp` inside the Debian container and returns the transcript.
 *
 * Invoked the same way `serve-web.sh` starts the dashboard: a `ProcessBuilder` against the Termux
 * bash, which then enters the container through proot-distro. That keeps the model and the binary
 * inside the chroot rather than shipping ~150 MB inside the APK.
 *
 * The platform recogniser is kept as the fallback — Whisper is an optional provisioning step, and a
 * device that never ran it must still be able to dictate.
 */
object Whisper {
    private const val TAG = "Whisper"
    private const val RUNNER = "/root/.whisper/run.sh"
    private const val TIMEOUT_SECONDS = 180L

    /** True once the runner script and its model are present in the container. */
    fun isInstalled(): Boolean = File(containerRoot(), "root/.whisper/run.sh").isFile

    /**
     * Transcribes [wav], returning the text or null if Whisper is not installed or the run failed.
     *
     * Blocking — call from a worker thread.
     */
    @Synchronized
    fun transcribe(wav: File): String? {
        if (!wav.isFile) return null
        if (!isInstalled()) {
            Log.i(TAG, "Whisper is not installed; caller should fall back")
            return null
        }

        // The audio has to be visible inside the container. --shared-tmp already makes Termux's
        // tmp the same directory as Debian's, so writing there is enough.
        val sharedTmp = File(TermuxPaths.TMP)
        if (!sharedTmp.isDirectory && !sharedTmp.mkdirs()) return null
        val target = File(sharedTmp, "dictation-${System.currentTimeMillis()}.wav")
        return try {
            wav.copyTo(target, overwrite = true)

            val pb = ProcessBuilder(
                listOf(
                    TermuxPaths.BASH, "-c",
                    "proot-distro login debian --shared-tmp -- $RUNNER /tmp/${target.name}",
                ),
            )
            pb.directory(File(TermuxPaths.HOME))
            pb.redirectErrorStream(false)
            val env = HashMap<String, String>()
            for (entry in TermuxShellEnvironment.build(TermuxPaths.HOME)) {
                val idx = entry.indexOf('=')
                if (idx > 0) env[entry.substring(0, idx)] = entry.substring(idx + 1)
            }
            pb.environment().putAll(env)

            val process = pb.start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Log.w(TAG, "whisper-cli timed out")
                return null
            }
            if (process.exitValue() != 0) {
                Log.w(TAG, "whisper-cli failed: ${stderr.take(300)}")
                return null
            }
            parse(stdout)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not run whisper-cli", t)
            null
        } finally {
            runCatching { target.delete() }
        }
    }

    /**
     * Pulls the text out of whisper.cpp's output.
     *
     * With `-nt -np` the lines carry no timestamps or probabilities, but the header and footer still
     * come through, so anything bracketed or obviously non-prose is dropped rather than typed into
     * the composer.
     */
    private fun parse(output: String): String? {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("[") || it.startsWith("whisper_") || it.startsWith("main:") }
            .filterNot { it.startsWith("system_info") }
            .toList()
        val text = lines.joinToString(" ").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun containerRoot(): File = File(TermuxPaths.DEBIAN_CONTAINER_ROOTFS)
}
