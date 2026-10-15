package com.termux.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.termux.app.ui.theme.Danger

/**
 * A row that can be dragged aside to reveal a delete action, and springs back otherwise.
 *
 * Written by hand rather than pulled in as a library because the behaviour wanted here is narrow:
 * horizontal drag only, a fixed commit threshold, haptic feedback at the commit point, and an
 * irreversible action the user has to confirm with a second tap. A general swipe-to-dismiss either
 * fires on a short flick — disastrous here, since a conversation cannot be recovered — or needs a
 * swipe-back gesture this row has nowhere to put.
 *
 * The offset is driven by [dragTarget] rather than being written to the [Animatable] from the drag
 * callback. `onHorizontalDrag` is not a suspending context, so calling `snapTo` there means
 * launching a coroutine per pointer event — at 120Hz across a scrolling list that janks badly.
 * Instead the drag only writes a plain float, and a single [LaunchedEffect] either snaps to it
 * (while a finger is down, so the row tracks exactly) or animates to it (on release).
 *
 * @param onDelete invoked only when the revealed button is tapped, never by the drag itself.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    val thresholdPx = with(density) { 88.dp.toPx() }
    val maxPx = with(density) { 132.dp.toPx() }

    var dragTarget by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(dragTarget, dragging) {
        if (dragging) offsetX.snapTo(dragTarget) else offsetX.animateTo(dragTarget, tween(180))
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // No "revealed action" layer underneath. There was one, and it was a real bug: the row
        // content is transparent, so a permanently-drawn action underneath showed through on every
        // row at rest — a trash icon on all four sessions, as if they were all already pending
        // deletion. The action is drawn only once the row is genuinely armed.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(onDelete) {
                    var drag = 0f
                    var crossed = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            drag = 0f
                            crossed = false
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                            // Only the reveal direction engages. A rightward drag is damped hard
                            // rather than blocked, so the row does not feel stuck.
                            val next = if (drag > 0f) drag * 0.2f else drag
                            dragTarget = next.coerceIn(-maxPx, 0f)
                            if (!crossed && drag < -thresholdPx) {
                                crossed = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            val open = dragTarget < -thresholdPx
                            armed = open
                            // Releasing past the threshold parks the row at the threshold with the
                            // button showing; otherwise it returns to zero and disarms.
                            dragTarget = if (open) -thresholdPx else 0f
                        },
                        onDragCancel = {
                            dragging = false
                            armed = false
                            dragTarget = 0f
                        },
                    )
                },
        ) {
            content()
        }

        // Only rendered once armed, so the row cannot be deleted by a stray tap mid-gesture.
        if (armed) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(width = 72.dp, height = 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Danger.copy(alpha = 0.16f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            armed = false
                            dragTarget = 0f
                            onDelete()
                        }
                    },
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete conversation",
                    tint = Danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}