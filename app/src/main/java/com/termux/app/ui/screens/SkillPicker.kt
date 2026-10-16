package com.termux.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.chat.SkillInfo
import com.termux.app.ui.theme.AccentSoft
import com.termux.app.ui.theme.BgElevated
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary

/**
 * The skill picker, beside the mode switch.
 *
 * Deliberately shaped like [ModeSwitch] — same chip, same dropdown — because they answer the same
 * kind of question ("what is this turn running as?"), and a second visual language for it would
 * read as two unrelated features.
 *
 * Picking a skill inserts `/name` into the composer rather than sending anything. The user can see
 * what they picked, edit the sentence around it, and only then send — which is the difference
 * between invoking a skill and firing one off by accident.
 *
 * The list comes from opencode, never from this file. Skills are installed by provisioning or by
 * the user in a project directory, so a name hardcoded here would advertise a skill that is not
 * there and hide one that is.
 */
@Composable
fun SkillPicker(
    skills: List<SkillInfo>,
    onPick: (SkillInfo) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    if (skills.isEmpty()) return

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = AccentSoft,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Skill",
                fontSize = 11.sp,
                color = AccentSoft,
                fontFamily = FontFamily.Monospace,
            )
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Choose a skill",
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = BgElevated,
            // Capped so the list scrolls. DropdownMenu grows to fit its content and runs off the
            // bottom of a phone screen otherwise, which is worse than the model picker's own limit.
            modifier = Modifier.heightIn(max = 340.dp),
        ) {
            MenuHeading("Skill")
            skills.forEach { skill ->
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = skill.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                maxLines = 1,
                            )
                            if (skill.description.isNotBlank()) {
                                Text(
                                    text = skill.description,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 2,
                                )
                            }
                        }
                    },
                    onClick = {
                        open = false
                        onPick(skill)
                    },
                )
            }
        }
    }
}