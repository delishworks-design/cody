package com.termux.app.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.termux.app.install.TermuxPaths
import java.io.File
import android.webkit.MimeTypeMap

/**
 * One artifact the agent produced that the user can take away.
 *
 * The Debian container is a proot chroot, so its `/root` is just a directory underneath our own
 * app storage — the app can hand the bytes to Android without copying them first.
 */
data class WorkspaceFile(
    val file: File,
    val size: Long,
    val modified: Long,
    val mime: String,
    /** Short label naming which of the browsed roots the file came from. */
    val scope: String,
    val relativePath: String,
) {
    val name: String get() = file.name
}

/**
 * Discovers, shares and saves the files the agent writes.
 *
 * Three roots are browsed: the Termux home, the Debian workspace (`/root` inside the container)
 * and the shared `/tmp` — the last one matters because proot-distro's `--shared-tmp` makes container
 * `/tmp` and `$PREFIX/tmp` the same directory, which is where opencode parks tool sandboxes.
 */
object WorkspaceFiles {
    private const val TAG = "WorkspaceFiles"

    /** Must match the `android:authorities` of the FileProvider declared in the manifest. */
    const val AUTHORITY = "com.termux.fileprovider"

    /** Dependency and VCS directories that would otherwise drown the list. */
    private val SKIP_DIRS = setOf(
        "node_modules", ".git", ".cache", ".npm", ".venv", "venv",
        "__pycache__", ".gradle", ".opencode-provision",
    )

    private const val MAX_DEPTH = 6
    private const val MAX_CANDIDATES = 400

    private fun roots(): List<Pair<String, String>> = listOf(
        "workspace" to TermuxPaths.HOME,
        "debian /root" to TermuxPaths.PREFIX + "/var/lib/proot-distro/containers/debian/rootfs/root",
        "temp" to TermuxPaths.PREFIX + "/tmp",
    )

    /** The workspace directory the agent's cwd points at, re-rooted onto the real filesystem. */
    private val HOME = "opencode-android"

    /** Newest files first, capped so a `node_modules` explosion cannot stall the sheet. */
    fun list(limit: Int = 80): List<WorkspaceFile> {
        val out = ArrayList<WorkspaceFile>()
        for ((label, path) in roots()) {
            val root = File(path)
            if (root.isDirectory) walk(root, root.absolutePath, label, 0, out)
        }
        return out
            .distinctBy { it.file.absolutePath }
            .sortedByDescending { it.modified }
            .take(limit)
    }

    private fun walk(dir: File, root: String, label: String, depth: Int, out: MutableList<WorkspaceFile>) {
        if (depth > MAX_DEPTH || out.size >= MAX_CANDIDATES) return
        val children = try {
            dir.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return

        for (child in children) {
            if (out.size >= MAX_CANDIDATES) return
            val name = child.name
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                if (name in SKIP_DIRS) continue
                walk(child, root, label, depth + 1, out)
            } else if (child.isFile) {
                val absolute = child.absolutePath
                out += WorkspaceFile(
                    file = child,
                    size = child.length(),
                    modified = child.lastModified(),
                    mime = mimeOf(name),
                    scope = label,
                    relativePath = absolute.removePrefix("$root/"),
                )
            }
        }
    }

    private fun mimeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return mime ?: if (extension == "apk") "application/vnd.android.package-archive"
        else "application/octet-stream"
    }

    /**
     * Turns a `file` part's `url` into a real [File] on Android's filesystem.
     *
     * The agent works inside the Debian chroot, so paths it reports are container paths (`/root/...`)
     * that do not exist as far as the app is concerned — they have to be re-rooted onto the container's
     * directory underneath our own files dir. `file://` URLs are unwrapped first, and a bare filename
     * is looked up in the workspace. When neither the exact path nor the obvious roots match, the
     * workspace is swept for a file with that name, because the agent routinely writes into a
     * subdirectory it picked itself and we only know the basename. Returns null when nothing matches,
     * so the UI can fall back to a chip.
     */
    fun resolve(url: String, filename: String): File? {
        val debianRoot = File(TermuxPaths.DEBIAN_CONTAINER_ROOTFS)
        val allowedRoots = listOf(
            File(TermuxPaths.HOME),
            debianRoot,
            File(TermuxPaths.PREFIX, "tmp"),
        ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }

        fun contained(candidate: File): File? {
            val canon = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
            if (!canon.isFile) return null
            return if (allowedRoots.any { root ->
                    canon.path == root.path || canon.path.startsWith(root.path + File.separator)
                }) canon else null
        }

        fun reRoot(path: String): File? {
            val clean = path.trim()
            if (clean.isEmpty()) return null
            val containerPath = clean.removePrefix("file://").trim()
            if (containerPath.isEmpty()) return null
            // Already an Android-side absolute path: only accept it if it stays inside an allowed root.
            contained(File(containerPath))?.let { return it }
            if (!containerPath.startsWith("/")) return null
            // Container /tmp is shared with the Termux tmp directory.
            if (containerPath == "/tmp" || containerPath.startsWith("/tmp/")) {
                contained(File(TermuxPaths.PREFIX + "/tmp", containerPath.removePrefix("/tmp/").trimStart('/')))
                    ?.let { return it }
            }
            // Container absolute path -> debian rootfs.
            return contained(File(debianRoot, containerPath.trimStart('/')))
        }

        val name = filename.trim().substringAfterLast('/')
        val candidates = ArrayList<File?>()
        if (url.isNotBlank()) candidates += reRoot(url)
        if (name.isNotEmpty()) {
            candidates += reRoot("/root/$name")
            candidates += reRoot("/root/$HOME/$name")
            candidates += contained(File(TermuxPaths.HOME, name))
            candidates += contained(File(TermuxPaths.HOME, HOME + File.separator + name))
        }
        candidates.firstOrNull { it != null }?.let { return it }
        val found = if (name.isEmpty()) null else findByName(name)
        return found?.let { contained(it) }
    }

    /** Bounded sweep of the browsed roots for a file with this exact basename. */
    private fun findByName(name: String): File? {
        for ((_, rootPath) in roots()) {
            val root = File(rootPath)
            if (!root.isDirectory) continue
            var budget = 2_000
            val stack = ArrayDeque<File>()
            stack.addLast(root)
            while (stack.isNotEmpty() && budget-- > 0) {
                val dir = stack.removeLast()
                val children = try {
                    dir.listFiles()
                } catch (t: Throwable) {
                    null
                } ?: continue
                for (child in children) {
                    if (child.name in SKIP_DIRS) continue
                    if (child.isFile) {
                        if (child.name == name) return child
                    } else if (child.isDirectory) {
                        stack.addLast(child)
                    }
                }
            }
        }
        return null
    }

    /**
     * Hands the file to another app through the system share sheet — the one path that works on
     * every Android version regardless of storage permissions, so it is the primary action.
     */
    fun share(context: Context, item: WorkspaceFile): Boolean = try {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, item.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send ${item.file.name}"))
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Share failed for ${item.file}", t)
        false
    }

    /**
     * Copies into the public Downloads collection.
     *
     * Scoped storage covers API 29+ without a permission; older releases write straight to
     * `/sdcard/Download`, which needs `WRITE_EXTERNAL_STORAGE` and raises `SecurityException` when
     * it has not been granted — the caller is expected to fall back to [share].
     */
    fun saveToDownloads(context: Context, item: WorkspaceFile): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, item.file.name)
                    put(MediaStore.Downloads.MIME_TYPE, item.mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                val opened = context.contentResolver.openOutputStream(uri) ?: return false
                opened.use { out -> item.file.inputStream().use { input -> input.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.isDirectory && !dir.mkdirs()) return false
                val target = File(dir, item.file.name)
                item.file.copyTo(target, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(item.mime), null)
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not save ${item.file.name} to Downloads", t)
            false
        }
    }
}
