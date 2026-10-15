package com.termux.app.chat

import android.content.Context
import android.util.Log
import com.termux.app.install.ProvisionAssets
import com.termux.app.shell.TermuxShellEnvironment
import com.termux.app.install.TermuxPaths
import java.io.File

/**
 * Owns the single `opencode serve` child process that the chat UI talks to.
 *
 * The server is started by running `assets/provision/serve.sh` under the Termux bash, which enters
 * Debian through proot-distro and `exec`s `opencode serve`. It stays alive for the lifetime of the
 * app process; [ensureStarted] is cheap to call before every request because it returns immediately
 * when the server is already healthy.
 */
object OpenCodeServer {
    private const val TAG = "OpenCodeServer"
    private const val SCRIPT = "serve.sh"

    @Volatile
    private var process: Process? = null

    /** True while a start attempt is in flight, so concurrent callers do not spawn two servers. */
    @Volatile
    private var starting = false

    /** Starts the server if it is not already answering health checks. Safe to call repeatedly. */
    @Synchronized
    fun ensureStarted(context: Context) {
        if (OpenCodeApi.health()) return
        if (starting) return
        starting = true
        try {
            // A dead or half-started child would otherwise hold the port forever.
            process?.let { runCatching { it.destroy() } }
            process = null

            val script = ProvisionAssets.script(context, SCRIPT)
            if (!script.isFile) {
                Log.e(TAG, "Missing $script")
                return
            }

            val pb = ProcessBuilder(listOf(TermuxPaths.BASH, script.absolutePath))
            pb.directory(File(TermuxPaths.HOME))
            pb.redirectErrorStream(true)
            pb.redirectOutput(File(TermuxPaths.HOME, "opencode-serve.log"))
            val env = HashMap<String, String>()
            for (entry in TermuxShellEnvironment.build(TermuxPaths.HOME)) {
                val idx = entry.indexOf('=')
                if (idx > 0) env[entry.substring(0, idx)] = entry.substring(idx + 1)
            }
            pb.environment().putAll(env)

            Log.i(TAG, "Starting opencode server via ${script.name}")
            process = pb.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start opencode server", t)
        } finally {
            starting = false
        }
    }

    /** Stops the server. Only used when the app is done with the environment for good. */
    @Synchronized
    fun stop() {
        process?.let { runCatching { it.destroy() } }
        process = null
    }
}
