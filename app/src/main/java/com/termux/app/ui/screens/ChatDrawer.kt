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
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    onTogglePin: (String) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenTerminal: () -> Unit,
    drawerState: androidx.compose.material3.DrawerState,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<SessionInfo?>(null) }
    var menuFor by remember { mutableStateOf<SessionInfo?>(null) }

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
                            // Pinned first, then by recency within each group. A pin the user
                            // cannot find is the same as no pin.
                            items(
                                sessions.sortedWith(
                                    compareByDescending<SessionInfo> { it.pinned }
                                        .thenByDescending { it.updated },
                                ),
                                key = { it.id },
                            ) { session ->
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
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // A pinned row leads with the mark rather than
                                                    // a different colour: on a phone, position is
                                                    // read faster than hue.
                                                    if (session.pinned) {
                                                        Icon(
                                                            Icons.Outlined.PushPin,
                                                            contentDescription = "Pinned",
                                                            tint = AccentSoft,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = session.title,
                                                        color = if (session.id == currentId) AccentSoft else TextPrimary,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
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
                                                    .clickable { menuFor = session },
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
                    DrawerRow(Icons.Outlined.Handyman, "Setup", onOpenSetup)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
    ) {
        content()
    }

    menuFor?.let { session ->
        SessionMenu(
            session = session,
            onRename = {
                menuFor = null
                renaming = session
            },
            onTogglePin = {
                menuFor = null
                onTogglePin(session.id)
            },
            onDismiss = { menuFor = null },
        )
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
 * The per-conversation menu, behind the ⋮.
 *
 * Pin and Rename only. Delete stays on the swipe, deliberately: it is the one action here that is
 * hard to undo, and a menu item two taps from a title is one mis-tap too many.
 */
@Composable
private fun SessionMenu(
    session: SessionInfo,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 40.dp),
        title = {
            Text(
                session.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                SessionMenuRow(
                    icon = Icons.Outlined.PushPin,
                    label = if (session.pinned) "Unpin" else "Pin to top",
                    onClick = onTogglePin,
                )
                SessionMenuRow(
                    icon = Icons.Outlined.Edit,
                    label = "Rename",
                    onClick = onRename,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        },
    )
}

@Composable
private fun SessionMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
