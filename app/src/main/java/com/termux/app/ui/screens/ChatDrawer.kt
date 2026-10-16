package com.termux.app.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.termux.app.chat.SessionInfo
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.AccentSoft
import com.termux.app.ui.theme.Bg
import com.termux.app.ui.theme.BgElevated
import com.termux.app.ui.theme.Border
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * The left drawer: brand, new chat, the conversation list, and the few global destinations.
 *
 * Modelled on the mainstream chat apps — history lives in a drawer rather than behind an overflow
 * menu, because on a phone the menu is off the top-right corner and the list is what people reach for
 * constantly. Rows carry the swipe-to-delete affordance from [SwipeToDelete] so a stray tap cannot
 * destroy a conversation; renaming is deliberately behind a long-press.
 */
@Composable
fun ChatDrawer(
    sessions: List<SessionInfo>,
    currentId: String?,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenTerminal: () -> Unit,
    drawerState: androidx.compose.material3.DrawerState,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<SessionInfo?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // The sheet is pinned to 300.dp rather than left to the Material default. On a narrow phone the
            // default fills nearly the whole screen, which leaves no scrim to tap — so the drawer
            // loses its "I can dismiss this by tapping outside" affordance and starts to look like
            // a different screen. An explicit width keeps a strip of the transcript visible.
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = BgElevated,
                drawerContentColor = TextPrimary,
            ) {
                // The sheet renders outside the chat's own `statusBarsPadding()`, so it has to inset
                // itself: M3 handles the top and the sides, but not the navigation bar, which would
                // otherwise sit under the gesture pill or the three-button bar.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    // Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    ) {
                        CodyMark(size = 30.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Cody",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Android",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextMuted,
                            )
                        }
                    }

                    // New chat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                onNewChat()
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Icon(
                            Icons.Filled.AddComment,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("New chat", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "RECENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (sessions.isEmpty()) {
                        Text(
                            text = "Nothing yet.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            items(sessions, key = { it.id }) { session ->
                                SwipeToDelete(
                                    onDelete = { onDelete(session.id) },
                                ) {
                                    Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    scope.launch { drawerState.close() }
                                                    onSelect(session.id)
                                                }
                                                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                        ) {
                                            Column(modifier = Modifier.padding(end = 28.dp)) {
                                                Text(
                                                    text = session.title,
                                                    color = if (session.id == currentId) AccentSoft else TextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                // A conversation list without dates is just a list of
                                                // titles — "Recents" only means something if you can
                                                // see how recent. Relative for the last week, absolute
                                                // beyond it, because "43 days ago" stops helping.
                                                if (session.updated > 0) {
                                                    Text(
                                                        text = remember(session.updated) { relativeDate(session.updated) },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = TextMuted,
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Filled.MoreVert,
                                                contentDescription = "Rename",
                                                tint = TextMuted,
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .clickable { renaming = session },
                                            )
                                        }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    DrawerDivider()
                    DrawerRow(Icons.Filled.Folder, "Files", onOpenFiles)
                    DrawerRow(Icons.Outlined.Terminal, "Terminal", onOpenTerminal)
                    DrawerRow(Icons.Outlined.Settings, "Settings", onOpenSettings)
                    DrawerRow(Icons.Filled.Settings, "Setup", onOpenSetup)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
    ) {
        content()
    }

    renaming?.let { session ->
        RenameDialog(
            initial = session.title,
            onConfirm = { text ->
                onRename(session.id, text)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun DrawerDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(Border),
    )
}

@Composable
private fun DrawerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 22.dp),
        title = { Text("Rename chat", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text("Save", color = if (text.isNotBlank()) AccentSoft else TextMuted) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}

/**
 * "Just now", "3h", "Yesterday", "Mar 4" for a conversation's last update.
 *
 * Relative inside the last week because that is the window where people actually compare
 * conversations; a real date beyond it, since "43 days ago" stops telling you anything useful.
 */
private fun relativeDate(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val delta = now - epochMillis
    // Clock skew between the host and opencode's server can make a just-written session look like
    // it is from the future. Treat anything within a minute as "Just now" rather than rendering
    // "in 4 seconds".
    if (delta < 60_000L) return "Just now"
    val minutes = delta / 60_000L
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h"
    val days = hours / 24
    if (days == 1L) return "Yesterday"
    if (days < 7L) return "${days}d"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
}