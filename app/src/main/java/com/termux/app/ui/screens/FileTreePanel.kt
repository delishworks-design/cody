package com.termux.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.chat.FileStatus
import com.termux.app.chat.folder
import com.termux.app.ui.theme.BgElevated
import com.termux.app.ui.theme.Border
import com.termux.app.ui.theme.Danger
import com.termux.app.ui.theme.Success
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary
import com.termux.app.ui.theme.Warning

/**
 * What the agent has changed in the working project.
 *
 * Reached from a button in the chat header, because that is where "what is happening to my files"
 * belongs while you are watching it happen. Grouped by folder and split into added / modified /
 * deleted, so a twenty-file refactor reads as a shape rather than a list.
 *
 * The numbers are the point. "3 files changed" is an abstraction; "+120 −34 in ChatScreen.kt" is
 * something you can decide on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreePanel(
    files: List<FileStatus>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgElevated,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    "Changed files",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (files.isNotEmpty()) {
                    Text(
                        text = summaryLine(files),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Nothing changed yet",
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                }
                return@Column
            }

            val grouped = files.groupBy { it.folder }.toList().sortedBy { it.first }

            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                grouped.forEach { (folder, entries) ->
                    item(key = "folder-$folder") {
                        Text(
                            text = folder,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 14.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }
                    items(entries, key = { it.path }) { file ->
                        FileRow(file)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: FileStatus) {
    val (icon, tint) = iconFor(file)
    val name = file.path.substringAfterLast('/')

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (file.added > 0) {
            Text("+${file.added}", fontSize = 11.sp, color = Success, fontFamily = FontFamily.Monospace)
        }
        if (file.added > 0 && file.removed > 0) Spacer(Modifier.width(6.dp))
        if (file.removed > 0) {
            Text("−${file.removed}", fontSize = 11.sp, color = Danger, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Added, modified and deleted get three different glyphs and colours rather than three labels.
 *
 * Colour alone would fail anyone who cannot separate the green from the red, which is the most
 * common accessibility complaint about a diff view. The icon carries it too.
 */
private fun iconFor(file: FileStatus): Pair<ImageVector, Color> = when {
    file.isAdded -> Icons.Outlined.AddCircle to Success
    file.isDeleted -> Icons.Outlined.DeleteOutline to Danger
    else -> Icons.Outlined.EditNote to Warning
}

private fun summaryLine(files: List<FileStatus>): String {
    val added = files.count { it.isAdded }
    val deleted = files.count { it.isDeleted }
    val modified = files.size - added - deleted
    return buildList {
        if (modified > 0) add("$modified modified")
        if (added > 0) add("$added new")
        if (deleted > 0) add("$deleted deleted")
    }.joinToString(" · ")
}
