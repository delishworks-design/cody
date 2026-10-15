package com.termux.app.web

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Serves the bundled 3D viewer and a single model file from one origin.
 *
 * The viewer JavaScript is shipped in the APK so it works offline, but `<model-viewer>` fetches
 * `./model.glb` relative to the page. Serving both from `http://127.0.0.1:<port>` keeps that fetch
 * same-origin, which is what WebGL texture loading requires - a `file://` page fetching an
 * `http://` URL (or the reverse) is cross-origin and gets blocked.
 *
 * Loopback only, one connection at a time, and torn down by [stop]; this is a preview helper, not
 * a general-purpose web server.
 */
object ViewerServer {
    private const val TAG = "ViewerServer"
    private const val ASSET_ROOT = "viewer"
    private const val MODEL_PATH = "/model.glb"
    private const val MODEL_MIME = "model/gltf-binary"

    private val workers = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "viewer-server").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var modelFile: File? = null

    @Volatile
    var baseUrl: String? = null
        private set

    /** Starts the server if needed and returns the URL the WebView should load. */
    @Synchronized
    fun start(context: Context, model: File): String {
        val existing = baseUrl
        if (existing != null && modelFile?.absolutePath == model.absolutePath) return existing

        stop()
        modelFile = model

        val socket = try {
            ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        } catch (t: Throwable) {
            Log.e(TAG, "Could not bind a loopback port", t)
            return "http://127.0.0.1:0/"
        }
        serverSocket = socket
        baseUrl = "http://127.0.0.1:${socket.localPort}/"

        workers.execute { acceptLoop(socket, context) }
        Log.i(TAG, "Serving ${model.absolutePath} at $baseUrl")
        return baseUrl!!
    }

    private fun acceptLoop(socket: ServerSocket, context: Context) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (t: IOException) {
                return
            }
            runCatching { handle(client, context) }
            runCatching { client.close() }
        }
    }

    private fun handle(client: Socket, context: Context) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8), 8 * 1024)
        val requestLine = reader.readLine() ?: return
        // Drain the headers so the client sees a clean response.
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }

        val parts = requestLine.split(' ')
        val rawPath = parts.getOrNull(1) ?: "/"
        val path = try {
            URLDecoder.decode(rawPath, Charsets.UTF_8)
        } catch (t: Throwable) {
            rawPath
        }.substringBefore('?')

        val out = BufferedOutputStream(client.getOutputStream())
        when {
            path == "/" || path == "/index.html" -> {
                writeHead(out, 200, "text/html; charset=utf-8", -1)
                out.flush()
                copyAsset(context, "$ASSET_ROOT/index.html", out)
            }

            path == "/$ASSET_ROOT/model-viewer.min.js" || path == "/model-viewer.min.js" -> {
                writeHead(out, 200, "application/javascript; charset=utf-8", -1)
                out.flush()
                copyAsset(context, "$ASSET_ROOT/model-viewer.min.js", out)
            }

            path == MODEL_PATH -> {
                val file = modelFile
                if (file == null || !file.isFile) {
                    writeHead(out, 404, "text/plain; charset=utf-8", -1)
                    out.flush()
                    out.write("model not found\n".toByteArray())
                } else {
                    writeHead(out, 200, MODEL_MIME, file.length().toInt())
                    out.flush()
                    file.inputStream().use { it.copyTo(out) }
                }
            }

            else -> {
                writeHead(out, 404, "text/plain; charset=utf-8", -1)
                out.flush()
                out.write("not found\n".toByteArray())
            }
        }
        out.flush()
    }

    private fun writeHead(out: BufferedOutputStream, code: Int, contentType: String, length: Int) {
        val reason = when (code) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "OK"
        }
        val head = StringBuilder()
            .append("HTTP/1.1 $code $reason\r\n")
            .append("Content-Type: $contentType\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n")
        if (length >= 0) head.append("Content-Length: $length\r\n")
        head.append("\r\n")
        out.write(head.toString().toByteArray())
    }

    private fun copyAsset(context: Context, name: String, out: BufferedOutputStream) {
        try {
            context.assets.open(name).use { input: InputStream -> input.copyTo(out) }
        } catch (t: Throwable) {
            Log.e(TAG, "Missing asset $name", t)
        }
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        baseUrl = null
        modelFile = null
    }
}
