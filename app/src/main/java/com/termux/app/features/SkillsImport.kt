package com.termux.app.features

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.termux.app.install.TermuxPaths
import com.termux.app.shell.ShellRunner
import com.termux.app.shell.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Installs a skill from a shared file into the container's opencode skills directory. The source is
 * either a raw SKILL.md or a zip with a SKILL.md at the root or one folder deep. Only the markdown
 * is kept; opencode discovers the new folder on its own, so no agent command is run here.
 */
object SkillsImport {
    private const val DIR = "/root/.config/opencode/skills"
    private const val MAX_BYTES = 10 * 1024 * 1024
    private val SAFE_NAME = Regex("^[A-Za-z0-9._-]+$")

    private fun validName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 48 && SAFE_NAME.matches(name) &&
            name != "." && !name.contains("..")

    private fun slug(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .trim('-')
            .ifBlank { "imported-skill" }

    /** name/description from leading YAML frontmatter, if present. */
    private fun fields(text: String): Pair<String?, String?> {
        val t = text.replace("\r\n", "\n").trimStart('\uFEFF')
        if (!t.startsWith("---")) return null to null
        val firstBreak = t.indexOf('\n').takeIf { it >= 0 } ?: return null to null
        if (t.substring(0, firstBreak).trim() != "---") return null to null
        val end = t.indexOf("\n---", firstBreak + 1).takeIf { it > firstBreak } ?: return null to null
        val head = t.substring(firstBreak + 1, end)
        fun field(key: String): String? {
            val lines = head.lines()
            val i = lines.indexOfFirst { it.startsWith("$key:") }.takeIf { it >= 0 } ?: return null
            var v = lines[i].substringAfter(':').trim()
            if (v == ">" || v == "|" || v == ">-" || v == "|-") {
                v = lines.drop(i + 1).takeWhile { it.startsWith(" ") }.joinToString(" ") { it.trim() }
            }
            return v.removeSurrounding("\"").removeSurrounding("'").takeIf { it.isNotBlank() }
        }
        return field("name") to field("description")
    }

    private fun readCapped(context: Context, uri: Uri, cap: Int, onLine: (String) -> Unit): ByteArray? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: run {
                onLine("! could not open the file")
                return null
            }
            input.use {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > cap) {
                        onLine("! file exceeds 10 MB")
                        return null
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (t: Throwable) {
            onLine("! ${t.message ?: "read failed"}")
            null
        }
    }

    private fun command(script: String): List<String> = listOf(
        TermuxPaths.PROOT_DISTRO,
        "login", "debian", "--shared-tmp", "--",
        "/bin/bash", "-lc", script,
    )

    suspend fun install(context: Context, uri: Uri, onLine: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val bytes = readCapped(context, uri, MAX_BYTES, onLine) ?: return@withContext false
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment.orEmpty()

            val isZip = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            val text: String
            if (isZip) {
                var found: String? = null
                ZipInputStream(bytes.inputStream()).use { z ->
                    while (true) {
                        val entry = z.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val path = entry.name.replace('\\', '/')
                        if (path.startsWith("/") || path.substringBefore('/').contains(':') ||
                            path.split('/').any { it == ".." } || path.startsWith("__MACOSX/")
                        ) continue
                        val parts = path.split('/')
                        val isSkill = (parts.size == 1 && parts[0].equals("SKILL.md", true)) ||
                            (parts.size == 2 && parts[1].equals("SKILL.md", true))
                        if (!isSkill) continue
                        found = String(z.readBytes(), Charsets.UTF_8)
                        break
                    }
                }
                if (found == null) {
                    onLine("! no SKILL.md in archive")
                    return@withContext false
                }
                text = found
            } else {
                text = String(bytes, Charsets.UTF_8)
            }
            if (text.isBlank()) {
                onLine("! empty skill")
                return@withContext false
            }

            val (frontName, _) = fields(text)
            val raw = frontName
                ?: displayName.substringBeforeLast('.')
                    .takeIf { it.isNotBlank() && !it.equals("SKILL", true) }
                ?: "imported-skill"
            val name = slug(raw)
            if (!validName(name)) {
                onLine("! invalid skill name")
                return@withContext false
            }

            onLine("Installing skill $name")
            val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val script = "mkdir -p '$DIR/$name' && printf %s '$b64' | base64 -d > '$DIR/$name/SKILL.md'"
            val exit = ShellRunner.run(
                command(script),
                TermuxShellEnvironment.build(TermuxPaths.HOME),
                TermuxPaths.HOME,
                onLine,
            )
            if (exit == 0) onLine("Installed skill $name") else onLine("! install failed (exit $exit)")
            exit == 0
        }
}
