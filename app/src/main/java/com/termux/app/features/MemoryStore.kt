package com.termux.app.features

import android.content.Context
import android.util.Base64
import com.termux.app.install.TermuxPaths
import com.termux.app.shell.ShellRunner
import com.termux.app.shell.TermuxShellEnvironment

/**
 * Persistent memory as plain markdown files in [DIR] inside the Debian proot container. The agent
 * lives in the container, not in Termux's rootfs, so every operation shells in through
 * `proot-distro login debian` and moves file content across as base64.
 */
object MemoryStore {
    const val DIR = "/root/memory"

    private const val MAX_READ_BYTES = 1024 * 1024
    private const val MAX_B64_CHARS = (MAX_READ_BYTES / 3 + 2) * 4 + 16
    private val SAFE_NAME = Regex("^[A-Za-z0-9._-]+$")

    private fun validName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 255 && SAFE_NAME.matches(name) &&
            name != "." && !name.contains("..")

    /** Short instruction appended to user messages by the caller. */
    fun prompt(): String = """
        # Memory

        You have persistent memory in $DIR. Read $DIR/MEMORY.md (the index) and the shelf files it points to, then use what you find to answer. Use it quietly, without announcing it.

        When the user tells you something lasting (preferences, people, projects, goals, routines, decisions) or asks you to remember, write it to a file in $DIR in the same turn with your file tools. Never just say you will remember. Keep one file per topic with a one-line description at the top, and keep MEMORY.md a short index with one line per shelf: "- [Title](file.md) — what is inside". Correct or delete outdated facts, and when asked to forget, delete the fact. After changing memory, say in one short line what you saved or removed. Do not store one-off details or sensitive data unless asked.
    """.trimIndent()

    private fun command(script: String): List<String> = listOf(
        TermuxPaths.PROOT_DISTRO,
        "login", "debian", "--shared-tmp", "--",
        "/bin/bash", "-lc", script,
    )

    private suspend fun execute(script: String, onLine: (String) -> Unit): Int =
        ShellRunner.run(
            command(script),
            TermuxShellEnvironment.build(TermuxPaths.HOME),
            TermuxPaths.HOME,
            onLine,
        )

    /** Filenames present in the memory directory. */
    suspend fun list(context: Context, onLine: (String) -> Unit): List<String> {
        val files = mutableListOf<String>()
        val script = "mkdir -p '$DIR'; find '$DIR' -maxdepth 1 -type f -printf '%f\\n' 2>/dev/null || ls -1 '$DIR'"
        execute(script) { line ->
            val name = line.trim()
            if (name.isNotEmpty()) {
                files += name
                onLine(name)
            }
        }
        return files
    }

    /** File contents, base64'd inside the container and decoded here. */
    suspend fun read(context: Context, name: String, onLine: (String) -> Unit): String {
        if (!validName(name)) {
            onLine("! invalid memory name")
            return ""
        }
        val encoded = StringBuilder()
        val script = "test -f '$DIR/$name' && base64 '$DIR/$name' | tr -d '\\n'"
        val exit = execute(script) { line ->
            if (encoded.length + line.length <= MAX_B64_CHARS) encoded.append(line)
        }
        if (exit != 0) {
            onLine("! could not read $name")
            return ""
        }
        val raw = encoded.toString()
        if (raw.isEmpty()) return ""
        if (raw.length > MAX_B64_CHARS) {
            onLine("! memory file too large")
            return ""
        }
        val bytes = try {
            Base64.decode(raw, Base64.DEFAULT)
        } catch (t: Throwable) {
            onLine("! bad base64: ${t.message}")
            return ""
        }
        if (bytes.size > MAX_READ_BYTES) {
            onLine("! memory file too large")
            return ""
        }
        return String(bytes, Charsets.UTF_8)
    }

    /** Overwrite a memory file. True only when the shell reports success. */
    suspend fun write(context: Context, name: String, content: String, onLine: (String) -> Unit): Boolean {
        if (!validName(name)) {
            onLine("! invalid memory name")
            return false
        }
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val script = "mkdir -p '$DIR' && printf %s '$b64' | base64 -d > '$DIR/$name'"
        return execute(script, onLine) == 0
    }

    /** Remove a memory file. True only when the shell reports success. */
    suspend fun delete(context: Context, name: String, onLine: (String) -> Unit): Boolean {
        if (!validName(name)) {
            onLine("! invalid memory name")
            return false
        }
        return execute("rm -f '$DIR/$name'", onLine) == 0
    }
}
