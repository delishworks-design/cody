package com.termux.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.app.ui.AppViewModel
import com.termux.app.ui.SetupState
import com.termux.app.ui.theme.AccentSoft
import com.termux.app.ui.theme.Bg
import com.termux.app.ui.theme.Border
import com.termux.app.ui.theme.Cyan
import com.termux.app.ui.theme.Danger
import com.termux.app.ui.theme.Success
import com.termux.app.ui.theme.Surface
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary

@Composable
fun SetupScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.log.size) {
        if (state.log.isNotEmpty()) listState.animateScrollToItem(state.log.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            // Edge-to-edge: inset by the real bars rather than the old fixed 52dp, which collided
            // with the status bar.
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.dp, Border, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Text("Setup", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Spacer(Modifier.height(24.dp))

        when (val s = state.setup) {
            is SetupState.Ready -> ReadyPanel(onBack, onRerun = { viewModel.startSetup() })
            is SetupState.Failed -> FailedPanel(s.message, onRetry = { viewModel.startSetup() })
            is SetupState.Working -> WorkingPanel(s.title, s.detail, state.stepIndex, state.stepTotal)
            SetupState.NeedsSetup -> StartPanel(
                // The chat is the whole product; a terminal is not a prerequisite for it, so setup
                // ends the moment the server is reachable.
                onStart = { viewModel.startSetup() },
            )
            SetupState.Checking -> WorkingPanel("Checking", "…", 0, 0)
        }

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF070A10))
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            if (state.log.isEmpty()) {
                Text(
                    "Waiting…",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            } else {
                LazyColumn(state = listState) {
                    items(state.log) { line ->
                        Text(
                            text = line,
                            color = if (line.startsWith("!")) Danger else TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartPanel(onStart: () -> Unit) {
    Column {
        Text(
            "One-time setup. Takes a few minutes and needs an internet connection.",
            color = TextMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Start",
            icon = Icons.Filled.PlayArrow,
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WorkingPanel(title: String, detail: String, stepIndex: Int, stepTotal: Int) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Cyan, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        if (stepTotal > 0) {
            LinearProgressIndicator(
                progress = { stepIndex.toFloat() / stepTotal.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentSoft,
                trackColor = Surface,
            )
            Spacer(Modifier.height(8.dp))
            Text("Step $stepIndex of $stepTotal", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentSoft,
                trackColor = Surface,
            )
        }
    }
}

@Composable
private fun FailedPanel(message: String, onRetry: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Danger)
            Spacer(Modifier.width(8.dp))
            Text("Failed", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            text = "Retry",
            icon = Icons.Filled.PlayArrow,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The success panel, plus a way back into provisioning when something optional failed.
 *
 * Steps 06, 07 and 10 are deliberately allowed to fail without failing setup, and a failed optional
 * step is recorded as *attempted* so the app does not retry a multi-minute build on every launch.
 * That leaves a real hole: once setup reports Ready there was nothing here that could run it again,
 * so a failed Whisper build could only ever be recovered by clearing the app's data. This button is
 * that recovery, and it re-runs everything rather than only what failed.
 */
@Composable
private fun ReadyPanel(onBack: () -> Unit, onRerun: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Success.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Success, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Ready", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = "Back",
            icon = Icons.Filled.PlayArrow,
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Run setup again",
            icon = Icons.Filled.Refresh,
            onClick = onRerun,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
