package com.termux.app.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.termux.app.install.TermuxPaths
import java.io.File

/**
 * Turns a picked `content://` Uri into an [Attachment] the server can consume.
 *
 * The container never sees Android's storage: proot-distro only shares `/tmp`, so pointing the
 * prompt at a host path would 404 inside Debian. The bytes are therefore inlined as a
 * `data:` URI, which is the one form that travels across the boundary intact.
 */
object Attachments {
    private const val TAG = "Attachments"

    /** Upper bound on the raw payload; base64 adds a third on top of this. */
    const val MAX_BYTES = 12L * 1024 * 1024

    private const val THUMBNAIL_PX = 128

    fun fromUri(context: Context, uri: Uri): Attachment? {
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = displayName(context, uri) ?: uri.lastPathSegment ?: "attachment"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

            if (bytes.size > MAX_BYTES) {
                Log.w(TAG, "Rejecting $name: ${bytes.size} bytes exceeds the ${MAX_BYTES}B cap")
                return null
            }

            Attachment(
                id = "att-${System.nanoTime()}",
                name = name,
                mime = mime,
                size = bytes.size.toLong(),
                dataUri = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
                thumbnail = if (mime.startsWith("image/")) thumbnail(bytes) else null,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Could not read $uri", t)
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    } catch (t: Throwable) {
        null
    }

    /**
     * Decodes the image down to a chip-sized preview.
     *
     * `inSampleSize` is used rather than a full decode so a 12 MP photo costs a few hundred KB of
     * peak memory instead of tens of MB. The returned bitmap is the one the UI will draw, so it is
     * never recycled here.
     */
    /**
     * Writes an attachment to the Debian container's filesystem and returns the path the agent will
     * see (e.g. `/root/.opencode-attachments/report.zip`).
     *
     * This is how non-image attachments reach the model. They used to be inlined as `data:` URIs in
     * the prompt, which is what broke `.zip`: the payload is base64 (a third larger again), a
     * provider has no decoder for `application/zip`, and models cannot read a zip as prose either
     * way. Dropping the real bytes onto disk means the agent can `unzip`, `grep` or `cat` them with
     * the tools it already has, which is the only route that actually works.
     */
    fun persistToContainer(attachment: Attachment): String? = try {
        val root = File(TermuxPaths.DEBIAN_CONTAINER_ROOTFS, "root/.opencode-attachments")
        if (!root.isDirectory && !root.mkdirs()) {
            Log.w(TAG, "Could not create $root")
            null
        } else {
            var target = File(root, safeName(attachment.name))
            if (target.exists()) {
                val base = target.nameWithoutExtension
                val ext = target.extension
                var i = 1
                while (File(root, "$base-$i.$ext").exists()) i++
                target = File(root, "$base-$i.$ext")
            }
            val payload = attachment.bytes()
            if (payload == null) {
                Log.w(TAG, "Could not decode ${attachment.name} for writing")
                null
            } else {
                target.writeBytes(payload)
                "/root/.opencode-attachments/${target.name}"
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Could not persist ${attachment.name}", t)
        null
    }

    /** Keeps a recognisable name while making sure it cannot escape the target directory. */
    private fun safeName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.')
        val safe = cleaned.ifEmpty { "attachment" }.take(80)
        return if (safe.length > 60) safe.take(50) + "-" + safe.hashCode().toString(16) + safe.takeLast(6) else safe
    }

    private fun thumbnail(bytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        while (bounds.outWidth / sample > THUMBNAIL_PX * 4 || bounds.outHeight / sample > THUMBNAIL_PX * 4) {
            sample *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )

        if (decoded == null) {
            null
        } else {
            val longEdge = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
            val scale = THUMBNAIL_PX.toFloat() / longEdge
            if (scale >= 1f) {
                decoded
            } else {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true,
                ).also { if (it !== decoded) decoded.recycle() }
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Thumbnail failed", t)
        null
    }
}
