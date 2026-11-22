package com.termux.app.features

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, portable usage/cost tracking. OpenCode exposes no quota API, so this counts the cost
 * reported on each assistant `message.updated` event and sums it over sliding windows.
 *
 * Everything here is safe to call from any thread; the in-memory records are guarded by [lock].
 * [load] and [save] touch SharedPreferences only — the caller decides whether to hop to
 * `Dispatchers.IO`.
 */
object UsageTracker {
    /** One counted assistant reply. [id] is the message id, used to deduplicate stream updates. */
    data class Record(
        val time: Long,
        val model: String,
        val cost: Double,
        val input: Long,
        val output: Long,
        val reasoning: Long,
        val id: String = "",
    ) {
        val tokens: Long get() = input + output + reasoning
    }

    /** Totals for one sliding window. [byModel] maps model id to cost. */
    data class Window(val cost: Double, val tokens: Long, val byModel: Map<String, Double>)

    private const val PREFS = "cody_features"
    private const val KEY_USAGE = "usage"
    private const val HOUR = 3_600_000L
    private const val DAY = 24L * HOUR
    private const val KEEP_MS = 35L * DAY

    private val lock = Any()
    private val records = LinkedHashMap<String, Record>()

    /** Replace the tracked records with what is stored in SharedPreferences. */
    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USAGE, null) ?: return
        val parsed = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val loaded = LinkedHashMap<String, Record>()
        for (i in 0 until parsed.length()) {
            val o = parsed.optJSONObject(i) ?: continue
            val time = o.optLong("t", 0L)
            if (time <= 0L) continue
            val input = o.optLong("i", 0L)
            val output = o.optLong("o", 0L)
            val reasoning = o.optLong("r", 0L)
            val id = o.optString("id")
            val key = if (id.isEmpty()) syntheticKey(time, o.optString("m"), input, output, reasoning, o.optDouble("c", 0.0)) else id
            loaded[key] = Record(
                time = time,
                model = o.optString("m"),
                cost = o.optDouble("c", 0.0),
                input = input,
                output = output,
                reasoning = reasoning,
                id = id,
            )
        }
        synchronized(lock) {
            records.clear()
            records.putAll(loaded)
        }
    }

    /** Persist the tracked records, dropping anything older than 35 days. */
    fun save(context: Context) {
        val array = synchronized(lock) {
            val cutoff = System.currentTimeMillis() - KEEP_MS
            val iterator = records.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.time < cutoff) iterator.remove()
            }
            val out = JSONArray()
            records.values.forEach { r ->
                out.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("t", r.time)
                        .put("m", r.model)
                        .put("c", r.cost)
                        .put("i", r.input)
                        .put("o", r.output)
                        .put("r", r.reasoning),
                )
            }
            out.toString()
        }
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_USAGE, array).apply()
        }
    }

    /**
     * Fold one `message.updated` info object into the tracker. Ignores non-assistant messages and
     * events with neither cost nor token data, and replaces an existing record with the same
     * message id so streamed partial updates do not double-count.
     */
    fun record(eventInfo: JSONObject) {
        if (eventInfo.optString("role") != "assistant") return
        val cost = costOf(eventInfo) ?: return
        val tokens = eventInfo.optJSONObject("tokens")
        val input = tokens?.optLong("input", 0L) ?: 0L
        val output = tokens?.optLong("output", 0L) ?: 0L
        val reasoning = tokens?.optLong("reasoning", 0L) ?: 0L
        val totalTokens = input + output + reasoning

        val id = eventInfo.optString("id")
        val model = eventInfo.optString("modelID").ifEmpty {
            eventInfo.optJSONObject("model")?.optString("modelID").orEmpty()
        }
        val time = eventInfo.optJSONObject("time")?.let { t ->
            val created = t.optLong("created", 0L)
            if (created > 0L) created else t.optLong("completed", 0L)
        }?.takeIf { it > 0L } ?: eventInfo.optLong("createdAt", 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()

        val key = if (id.isEmpty()) syntheticKey(time, model, input, output, reasoning, cost) else id
        synchronized(lock) {
            val old = records[key]
            if (old != null && old.cost == cost && old.tokens == totalTokens) return@synchronized
            if (old == null && cost <= 0.0 && totalTokens == 0L) return@synchronized
            records[key] = Record(time, model, cost, input, output, reasoning, id)
        }
    }

    /** Forget everything, both in memory and in SharedPreferences. */
    fun clear(context: Context) {
        synchronized(lock) { records.clear() }
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_USAGE).apply()
        }
    }

    /** Totals over the last 5 hours, 7 days and 30 days. Future records are ignored. */
    fun compute(now: Long = System.currentTimeMillis()): Triple<Window, Window, Window> {
        val snapshot: List<Record> = synchronized(lock) { records.values.toList() }
        fun window(span: Long): Window {
            val cutoff = now - span
            val inWindow = snapshot.filter { it.time > cutoff && it.time <= now }
            val byModel = LinkedHashMap<String, Double>()
            for (r in inWindow) {
                val key = r.model.ifEmpty { "unknown" }
                byModel[key] = (byModel[key] ?: 0.0) + r.cost
            }
            return Window(
                cost = round6(inWindow.sumOf { it.cost }),
                tokens = inWindow.sumOf { it.tokens },
                byModel = byModel.mapValues { round6(it.value) },
            )
        }
        return Triple(window(5L * HOUR), window(7L * DAY), window(30L * DAY))
    }

    /**
     * Rough cost hint for a prompt about to be sent: assumes four characters per token and a
     * blended input/output price from [PRICES] ($ per 1M tokens).
     *
     * This is deliberately not a bill. Prices are hardcoded snapshots, ignore caching, tiered
     * rates and provider markups, and are wrong for most models — treat the result as an order of
     * magnitude only. Free-tier ids return 0.0; unknown ids use [DEFAULT_PRICE].
     */
    fun estimate(context: Context, provider: String, model: String, promptChars: Int): Double {
        val id = "$provider $model".lowercase()
        if (FREE_MARKERS.any { id.contains(it) }) return 0.0
        var perMillion = DEFAULT_PRICE
        for ((needle, price) in PRICES) {
            if (id.contains(needle)) { perMillion = price; break }
        }
        val tokens = maxOf(0, promptChars) / 4.0
        return round6(tokens / 1_000_000.0 * perMillion)
    }

    private fun costOf(info: JSONObject): Double? {
        info.optJSONObject("cost")?.let { c ->
            val v = if (c.has("total")) c.optDouble("total", Double.NaN)
                    else if (c.has("cost")) c.optDouble("cost", Double.NaN)
                    else Double.NaN
            return if (v.isNaN() || v.isInfinite()) null else v
        }
        return when (val v = info.opt("cost")) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun syntheticKey(
        time: Long, model: String, input: Long, output: Long, reasoning: Long, cost: Double,
    ): String = "anon:$time:$model:$input:$output:$reasoning:$cost"

    private fun round6(v: Double): Double = Math.round(v * 1_000_000.0).toDouble() / 1_000_000.0

    private val FREE_MARKERS = listOf(":free", "-free", "free-", "/free", "_free")

    private val PRICES: Map<String, Double> = linkedMapOf(
        "haiku" to 1.25,
        "opus" to 45.0,
        "sonnet" to 9.0,
        "gpt-4o-mini" to 0.6,
        "gpt-3.5" to 1.0,
        "gpt-4" to 10.0,
        "gpt" to 5.0,
        "o1" to 15.0,
        "o3" to 10.0,
        "flash" to 0.4,
        "gemini" to 3.5,
        "claude" to 9.0,
    )

    private const val DEFAULT_PRICE = 5.0
}
