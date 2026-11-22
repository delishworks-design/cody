package com.termux.app.features

import android.content.Context

/**
 * Optional instructions appended to a user turn by [com.termux.app.chat.OpenCodeRuntime.send].
 *
 * Two sources, both off-limits to the agent unless the user turned them on: persistent markdown
 * memory and a team of subagents. They live here rather than in the runtime so the chat layer only
 * has to know about one string, and adding another optional capability does not touch the send path
 * again. Everything is local: no network and no shell work here.
 */
object FeatureInstructions {
    private const val PREFS = "cody_features"
    private const val KEY_MEMORY = "memory_enabled"

    fun memoryEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MEMORY, true)

    fun setMemoryEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MEMORY, enabled).apply()
    }

    fun note(context: Context): String = buildString {
        if (memoryEnabled(context)) {
            append(MemoryStore.prompt())
        }
        val team = TeamAgents.instruction(context)
        if (team.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("# Team\n\n").append(team)
        }
    }
}
