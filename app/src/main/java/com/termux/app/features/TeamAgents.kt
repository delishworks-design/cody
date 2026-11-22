package com.termux.app.features

import android.content.Context
import com.termux.app.chat.OpenCodeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Portable team-of-agents config. Helpers are opencode subagents: the main agent hands them parts
 * of a task with the `task` tool and they work in parallel in child sessions.
 *
 * The lineup lives in the shared "cody_features" preferences, so it follows the app across
 * sessions. [sync] is the only network call and it blocks; callers run it on `Dispatchers.IO`.
 */
object TeamAgents {
    data class Member(val name: String, val role: String, val model: String)

    private const val PREFS = "cody_features"
    private const val KEY_MEMBERS = "team_members"
    private const val KEY_ENABLED = "team_enabled"
    private const val MAX_MEMBERS = 6

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun slug(name: String): String =
        "team-" + name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9а-яё]+"), "-")
            .trim('-')
            .ifBlank { "helper" }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    /** Stored lineup, or an empty list. Malformed preference never throws. Seeds nothing. */
    fun load(context: Context): List<Member> {
        val raw = prefs(context).getString(KEY_MEMBERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<Member>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val role = obj.optString("role")
                if (name.isEmpty() && role.isEmpty()) continue
                out += Member(name, role, obj.optString("model"))
            }
            out
        }.getOrDefault(emptyList())
    }

    /** Normalizes names to slugs, drops blank roles, keeps at most six members. */
    fun save(context: Context, members: List<Member>) {
        val normalized = members
            .filter { it.role.isNotBlank() }
            .take(MAX_MEMBERS)
            .map { Member(slug(it.name), it.role.trim(), it.model.trim()) }
        val array = JSONArray()
        normalized.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("role", it.role)
                    .put("model", it.model),
            )
        }
        prefs(context).edit().putString(KEY_MEMBERS, array.toString()).apply()
    }

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ---------------------------------------------------------------------------------------------
    // Agent instruction
    // ---------------------------------------------------------------------------------------------

    /** Note appended to the main agent's message; empty unless the team is on with members. */
    fun instruction(context: Context): String {
        if (!enabled(context)) return ""
        val members = load(context)
        if (members.isEmpty()) return ""
        return buildString {
            append("You may delegate parts of the task to helper subagents with the task tool ")
            append("(run independent parts in parallel). Available helpers:")
            members.forEach { append("\n- ").append(it.name).append(": ").append(it.role) }
            append("\nDo not claim a helper ran or produced anything unless a task tool result shows it; ")
            append("check their results before reporting.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Config sync
    // ---------------------------------------------------------------------------------------------

    /**
     * Writes the lineup to the shared opencode config as subagents. Disabled means there is
     * nothing to write, which counts as success. Any failure — a server that rejects `agent`, an
     * unsupported key, a dead connection — is swallowed: the team is an optional feature and the
     * caller must not crash because this opencode version does not support it.
     */
    fun sync(context: Context): Boolean = try {
        if (!enabled(context)) {
            true
        } else {
            val agents = JSONObject()
            for (m in load(context)) {
                val role = m.role.trim()
                if (role.isEmpty()) continue
                val entry = JSONObject()
                    .put("description", role)
                    .put("mode", "subagent")
                    .put("prompt", role)
                val model = m.model.trim()
                val slash = model.indexOf('/')
                if (slash > 0 && slash < model.length - 1) {
                    entry.put(
                        "model",
                        JSONObject()
                            .put("providerID", model.substring(0, slash))
                            .put("modelID", model.substring(slash + 1)),
                    )
                }
                agents.put(m.name, entry)
            }
            OpenCodeApi.patch("/global/config", JSONObject().put("agent", agents).toString())
            true
        }
    } catch (t: Throwable) {
        false
    }
}
