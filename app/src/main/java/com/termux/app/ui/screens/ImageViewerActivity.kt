package com.termux.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.termux.app.ui.theme.CodyTheme

/**
 * Full-screen image viewer with pinch-zoom and pan.
 *
 * The inline thumbnail in the transcript is cropped to a fixed height to keep the list readable;
 * this is where the picture is actually looked at. Backdrop is opaque black rather than the app
 * theme so a screenshot with a white edge is not judged against a dark background.
 */
class ImageViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_LABEL = "label"

        fun start(context: android.content.Context, file: java.io.File, label: String? = null) {
            val intent = android.content.Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_PATH, file.absolutePath)
                putExtra(EXTRA_LABEL, label ?: file.name)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // Decoded before setContent: a large photo on a phone can take a moment, and doing it in the
        // composition would flash an empty frame first.
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val bitmap = runCatching {
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = 1
            }
            // Bound the decode so a 50 MP photo cannot OOM the activity.
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 4096) sample *= 2
            options.inSampleSize = sample
            android.graphics.BitmapFactory.decodeFile(path, options)
        }.getOrNull()

        setContent {
            CodyTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    if (bitmap == null) {
                        Text2("Could not open this image.")
                    } else {
                        ZoomableImage(bitmap)
                    }

                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(42.dp)
                            .background(Color(0x66000000), androidx.compose.foundation.shape.CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                    if (label.isNotEmpty()) {
                        Text2(
                            text = label,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Pinch to zoom, drag to pan, double-tap-free but resets when the image no longer fits. */
@Composable
private fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    // Panning only makes sense once the image overflows its frame.
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
    )
}

@Composable
private fun Text2(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text,
        color = Color.White,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
