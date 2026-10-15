package com.termux.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Cody's mark: a rounded square with two eyes and a smile.
 *
 * One composable, used in the drawer header and the initialising screen, and mirrored by
 * `res/drawable/ic_launcher.xml` for the launcher. Keeping them in step is what stops them drifting
 * apart — they used to be three separate pieces of artwork.
 *
 * @param animated blinks and bobs. Only meaningful on the initialising screen, where there is
 *   otherwise nothing moving and a multi-minute install reads as a hang. Left off in the drawer: a
 *   logo that keeps moving is decoration, and decoration is what makes an interface look generated.
 */
@Composable
fun CodyMark(
    size: Dp = 40.dp,
    animated: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "cody")

    // 1 = wide open, 0.1 = squeezed shut. Two fixed endpoints rather than a 0..1 sweep, so the eye
    // reads as open nearly all the time and the blink is a discrete event.
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 110, delayMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF4A8CFF), Color(0xFF7FB4FF), Color(0xFFBFE0FF)),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFace(
                eyeOpen = if (animated) blink else 1f,
                bob = if (animated) bob else 0f,
            )
        }
    }
}

/**
 * The face, positioned in absolute canvas coordinates.
 *
 * Deliberately free of `translate`/`scale`/`rotate` blocks. An earlier version wrapped the eyes in
 * `translate { scale { drawCircle() } }` and nested them inside a `rotate`, on the assumption the
 * frames would compose the way they read. They did not — on device the eyes landed together in the
 * bottom-right corner and the smile drifted left of centre. Every draw call below therefore passes
 * its own `topLeft`/`size`/`center`, which has exactly one interpretation.
 *
 * Everything is expressed as a fraction of the shorter side, so the same code is correct at 30dp in
 * the drawer and at 84dp on the initialising screen.
 */
private fun DrawScope.drawFace(eyeOpen: Float, bob: Float) {
    val side = size.minDimension
    val cx = size.width / 2f
    // The whole face rides up and down. A fraction of the side, so it scales with the mark.
    val cy = size.height / 2f + bob * side * 0.01f

    val eyeRadius = side * 0.095f
    val eyeSpread = side * 0.165f
    val eyeY = cy - side * 0.06f

    // The blink shrinks the eye vertically instead of scaling the canvas, which would also squash
    // the smile and read as a twitch. `max` keeps a hairline at full squeeze so the eye never
    // disappears entirely — that looks like a missing draw call, not a blink.
    val eyeRy = max(eyeRadius * eyeOpen, side * 0.014f)

    listOf(-eyeSpread, eyeSpread).forEach { dx ->
        drawOval(
            color = Color.White,
            topLeft = Offset(cx + dx - eyeRadius, eyeY - eyeRy),
            size = Size(eyeRadius * 2f, eyeRy * 2f),
        )
    }

    // Smile: the lower arc of an ellipse, stroked rather than filled so it stays clean when the
    // whole mark is thirty pixels across.
    val smileW = side * 0.34f
    val smileH = side * 0.26f
    drawArc(
        color = Color.White,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - smileW / 2f, cy - side * 0.02f),
        size = Size(smileW, smileH),
        style = Stroke(width = side * 0.065f, cap = StrokeCap.Round),
    )

    // Blush. Off unless there is room for it: at drawer size two dots the width of an eye read as
    // speckle rather than as cheeks, so they only appear once the mark is large enough to carry them.
    if (side > 160f) {
        val blushR = side * 0.05f
        val blushY = cy + side * 0.07f
        listOf(-1f, 1f).forEach { dir ->
            drawOval(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset(cx + dir * side * 0.30f - blushR, blushY - blushR * 0.7f),
                size = Size(blushR * 2f, blushR * 1.4f),
            )
        }
    }
}