package com.termux.app.shell

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs a non-interactive command inside the Termux rootfs using [ProcessBuilder], streaming the
 * merged stdout/stderr line by line. Used for environment setup (apt, proot-distro, opencode).
 */
object ShellRunner {
    private const val TAG = "ShellRunner"

    suspend fun run(
        command: List<String>,
        env: Array<String>,
        workingDirectory: String,
        onLine: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)
        val dir = File(workingDirectory)
        if (!dir.isDirectory) dir.mkdirs()
        processBuilder.directory(dir)

        val environment = processBuilder.environment()
        environment.clear()
        for (entry in env) {
            val index = entry.indexOf('=')
            if (index > 0) environment[entry.substring(0, index)] = entry.substring(index + 1)
        }

        Log.i(TAG, "Running: ${command.joinToString(" ")}")
        val process = processBuilder.start()
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
            }
            process.waitFor()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.e(TAG, "Command failed", t)
            onLine("! ${t.message}")
            if (process.isAlive) process.destroy()
            -1
        }
    }
}
