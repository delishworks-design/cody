package com.termux.app.chat

import android.content.Context
import android.util.Log
import com.termux.app.install.ProvisionAssets
import com.termux.app.shell.TermuxShellEnvironment
import com.termux.app.install.TermuxPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the `opencode web` child process that backs the in-app WebView dashboard.
 *
 * It is separate from [OpenCodeServer] because the two subcommands are independent processes with
 * their own ports; only one of each is ever needed, and both are started lazily.
 */
object OpenCodeWeb {
    private const val TAG = "OpenCodeWeb"
    private const val SCRIPT = "serve-web.sh"

    const val PORT = 4173
    const val URL = "http://127.0.0.1:$PORT"

    @Volatile
    private var process: Process? = null

    @Synchronized
    fun ensureStarted(context: Context) {
        if (isReady()) return
        process?.let { runCatching { it.destroy() } }
        process = null

        val script = ProvisionAssets.script(context, SCRIPT)
        if (!script.isFile) {
            Log.e(TAG, "Missing $script")
            return
        }
        try {
            val pb = ProcessBuilder(listOf(TermuxPaths.BASH, script.absolutePath, PORT.toString()))
            pb.directory(File(TermuxPaths.HOME))
            pb.redirectErrorStream(true)
            pb.redirectOutput(File(TermuxPaths.HOME, "opencode-web.log"))
            val env = HashMap<String, String>()
            for (entry in TermuxShellEnvironment.build(TermuxPaths.HOME)) {
                val idx = entry.indexOf('=')
                if (idx > 0) env[entry.substring(0, idx)] = entry.substring(idx + 1)
            }
            pb.environment().putAll(env)
            process = pb.start()
            Log.i(TAG, "Started opencode web on port $PORT")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start opencode web", t)
        }
    }

    /** True once the dashboard answers; loopback cleartext is explicitly allowed for this. */
    fun isReady(timeoutMs: Int = 2_000): Boolean = try {
        val conn = URL(URL + "/").openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        try {
            conn.responseCode in 100..499
        } finally {
            conn.disconnect()
        }
    } catch (t: Throwable) {
        false
    }

    /** Starts the dashboard if needed and suspends until it actually answers. */
    suspend fun awaitReady(context: Context, attempts: Int = 30): Boolean = withContext(Dispatchers.IO) {
        ensureStarted(context)
        repeat(attempts) {
            if (isReady()) return@withContext true
            delay(1_000)
        }
        isReady()
    }

    @Synchronized
    fun stop() {
        process?.let { runCatching { it.destroy() } }
        process = null
    }
}
