package com.termux.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.TextMuted

/**
 * The dictation control, immediately left of Send.
 *
 * Pulses while it is listening so there is a visible answer to "did the tap register" — Android gives
 * no haptic or audio cue of its own on a silent device.
 *
 * Two states only: listening (pulsing red stop) and idle (gray mic). Tap to start, tap to stop.
 * No intermediate "transcribing" state — when the user stops recording, the text appears in the
 * composer once Whisper finishes, which takes a few seconds on the first run and is nearly instant
 * thereafter.
 */
@Composable
fun MicButton(
    listening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .scale(if (listening) pulse else 1f)
            .clip(CircleShape)
            .background(if (listening) Accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (listening) "Stop dictation" else "Dictate",
            tint = when {
                listening -> Accent
                enabled -> TextMuted
                else -> TextMuted.copy(alpha = 0.35f)
            },
            modifier = Modifier
                // 19.dp in a 38.dp target left the mic looking like a speck next to the much
                // heavier Send button beside it. 22.dp reads as the same weight without crowding
                // the circle.
                .size(22.dp)
                .alpha(if (listening) 1f else 0.9f),
        )
    }
}