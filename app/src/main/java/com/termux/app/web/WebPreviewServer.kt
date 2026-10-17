package com.termux.app.web

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Serves an HTML page (and the folder it lives in) from a loopback origin so it can be rendered
 * inside the chat.
 *
 * Why a server rather than `loadDataWithBaseURL` / `file://`:
 *  - a page that pulls in a sibling stylesheet, script or image needs those to resolve, and a
 *    `file://` WebView is not allowed to reach them under the app's own origin;
 *  - `<model-viewer>`-style WebGL assets are blocked outright when fetched cross-origin, so the
 *    document and its assets must share one origin.
 *
 * Only the page's own directory is exposed, only over loopback, and the socket dies with [stop] —
 * this is a preview helper, not a general-purpose web server.
 */
object WebPreviewServer {
    private const val TAG = "WebPreviewServer"
    private const val MAX_BYTES = 24L * 1024 * 1024

    private val workers = Executors.newFixedThreadPool(8) { runnable ->
        Thread(runnable, "web-preview").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var root: File? = null

    /**
     * A second directory the server may serve alongside [root].
     *
     * The agent attaches a picture to `/root/.opencode-attachments/` but writes its page wherever it
     * likes, so a relative `<img src="…">` pointing at a sibling of the *attachment* would 404.
     * Exposing just that one folder as a fallback lets `WebPreviewServer` answer those requests
     * without widening the served area to the whole container.
     */
    @Volatile
    private var secondaryRoot: File? = null

    @Volatile
    var baseUrl: String? = null
        private set

    /** Starts (or re-points) the server so [entry] is reachable, and returns its URL. */
    @Synchronized
    fun start(entry: File): String? {
        if (!entry.isFile) {
            Log.w(TAG, "Not a file: $entry")
            return null
        }
        val dir = entry.parentFile ?: return null
        val existing = baseUrl
        if (existing != null && root?.absolutePath == dir.absolutePath) return existing + entry.name

        stop()
        root = dir
        // Only used when the entry is not already inside it, so serving the same tree twice is
        // impossible.
        secondaryRoot = File(com.termux.app.install.TermuxPaths.DEBIAN_CONTAINER_ROOTFS, "root/.opencode-attachments")
            .takeIf { it.isDirectory && it.absolutePath != dir.absolutePath }

        val socket = try {
            ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        } catch (t: Throwable) {
            Log.e(TAG, "Could not bind a loopback port", t)
            return null
        }
        serverSocket = socket
        val url = "http://127.0.0.1:${socket.localPort}/"
        baseUrl = url

        workers.execute { acceptLoop(socket) }
        Log.i(TAG, "Serving ${dir.absolutePath} at $url")
        return url + entry.name
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (t: IOException) {
                return
            }
            workers.execute {
                runCatching { handle(client) }
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8), 8 * 1024)
        val requestLine = reader.readLine() ?: return
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }

        val rawPath = requestLine.split(' ').getOrNull(1) ?: "/"
        val decoded = try {
            URLDecoder.decode(rawPath, Charsets.UTF_8)
        } catch (t: Throwable) {
            rawPath
        }.substringBefore('?')

        val dir = root
        val out = BufferedOutputStream(client.getOutputStream())

        if (dir == null) {
            respond(out, 404, "text/plain; charset=utf-8", "no preview running\n".toByteArray())
            return
        }

        // Resolve inside the served directory only — never let `..` walk out of it. The attachment
        // folder is the single exception, and it is bounded the same way.
        val target = try {
            val resolved = File(dir, decoded).canonicalFile
            val inRoot = resolved.canonicalPath.startsWith(dir.canonicalPath + File.separator)
            val extra = secondaryRoot
            val inSecondary = extra != null &&
                resolved.canonicalPath.startsWith(extra.canonicalPath + File.separator)
            when {
                inRoot && resolved.isFile -> resolved
                inSecondary && resolved.isFile -> resolved
                decoded == "/" || decoded.isEmpty() ->
                    dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".html", true) }
                else -> null
            }
        } catch (t: Throwable) {
            null
        }

        if (target == null || !target.isFile) {
            respond(out, 404, "text/plain; charset=utf-8", "not found\n".toByteArray())
            return
        }

        val length = target.length()
        if (length > MAX_BYTES) {
            respond(out, 413, "text/plain; charset=utf-8", "file too large\n".toByteArray())
            return
        }

        val head = StringBuilder()
            .append("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: ").append(mimeOf(target.name)).append("\r\n")
            .append("Content-Length: ").append(length).append("\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        target.inputStream().use { it.copyTo(out) }
        out.flush()
    }

    private fun respond(out: BufferedOutputStream, code: Int, type: String, body: ByteArray) {
        val head = StringBuilder()
            .append("HTTP/1.1 $code ").append(if (code == 200) "OK" else "Error").append("\r\n")
            .append("Content-Type: ").append(type).append("\r\n")
            .append("Content-Length: ").append(body.size).append("\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        out.write(body)
        out.flush()
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "wasm" -> "application/wasm"
        "glb" -> "model/gltf-binary"
        "gltf" -> "model/gltf+json"
        "txt", "md" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        baseUrl = null
        root = null
        secondaryRoot = null
    }
}
