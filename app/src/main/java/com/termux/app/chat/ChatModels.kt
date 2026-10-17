package com.termux.app.chat

import android.graphics.Bitmap

/**
 * Data model for a conversation rendered by the chat UI.
 *
 * These mirror what `GET /session/:id/message` returns (`{ info, parts }[]`) and what the server's
 * `message.part.updated` / `message.part.delta` events push over SSE, so a message can be rebuilt
 * either from history or live while it is still being generated.
 */

/** A tool invocation inside an assistant message (the "the agent is running bash" activity). */
data class ToolState(
    /** `pending` -> `running` -> `completed` (or `error`), straight from the server. */
    val status: String = "pending",
    /** The tool arguments as compact JSON, e.g. `{"command":"ls -la"}`. */
    val input: String = "",
    /** Whatever the tool produced; the server calls this `raw` (or `output`). */
    val output: String = "",
)

/**
 * One piece of a message.
 *
 * `type` is `text`, `reasoning` or `tool`; the remaining server part types (step-start, step-finish,
 * ...) are separators with no content to show, so they are dropped when parsing.
 */
data class ChatPart(
    val id: String,
    val type: String,
    val text: String = "",
    /** Present when [type] == `tool`: `bash`, `edit`, `read`, `glob`, ... */
    val tool: String = "",
    val state: ToolState? = null,
    /** Present when [type] == `file`: the original name, its mime and a `data:`/http URL. */
    val filename: String = "",
    val mime: String = "",
    val url: String = "",
) {
    val isRunningTool: Boolean
        get() = type == "tool" && state?.status.let { it == "pending" || it == "running" }

    val isImage: Boolean
        get() = type == "file" && (mime.startsWith("image/") || Regex("\\.(png|jpe?g|gif|webp|bmp)$", RegexOption.IGNORE_CASE).containsMatchIn(filename))

    /** True for pages that can be rendered straight into the transcript. */
    val isHtml: Boolean
        get() = type == "file" && Regex("\\.x?html?$", RegexOption.IGNORE_CASE).containsMatchIn(filename)
}

data class ChatMessage(
    val id: String,
    val role: String,
    val parts: List<ChatPart> = emptyList(),
    val modelID: String = "",
    /**
     * When the turn was created, in epoch millis. `0` means "not known" — a message still streaming
     * has not been persisted yet, and the UI shows no time for it rather than guessing.
     */
    val createdAt: Long = 0L,
) {
    val isUser: Boolean get() = role == "user"

    /** Concatenated assistant prose, ignoring tool chatter. */
    val text: String
        get() {
            val raw = parts.filter { it.type == "text" }.joinToString("") { it.text }
            if (!isUser) return raw
            // The app appends attachment paths and credential notes to the outgoing message so the
            // agent can use them; those are not part of what the user typed and must not show up in
            // the transcript. Strip them from the displayed text only.
            val markers = listOf(
                "\n\nThe user attached ",
                "\n\nAlso on disk in the workspace:",
                "\n\nThe user has service credentials configured",
            )
            var cut = raw.length
            for (m in markers) {
                val i = raw.indexOf(m)
                if (i >= 0 && i < cut) cut = i
            }
            return raw.substring(0, cut).trimEnd()
        }
}

/**
 * An agent skill opencode advertises, from `GET /skill`.
 *
 * The picker lists whatever the server reports rather than a list bundled with the app: skills are
 * user- and project-defined, and opencode is the only thing that knows which ones are installed.
 * Hardcoding names here would show a skill that does not exist and hide one that does.
 */
data class SkillInfo(
    val name: String,
    val description: String,
    val location: String = "",
)

/** One file the agent has changed, from `GET /file/status`. */
data class FileStatus(
    val path: String,
    val status: String,
    val added: Int = 0,
    val removed: Int = 0,
) {
    val isAdded: Boolean get() = status == "added"
    val isDeleted: Boolean get() = status == "deleted"
}

/** The folder a file sits in, for grouping the change panel. */
val FileStatus.folder: String
    get() = path.substringBeforeLast('/', "").ifEmpty { "." }

data class SessionInfo(
    val id: String,
    val title: String,
    val updated: Long,
    /**
     * Stored in the session's own `metadata`, not in app preferences.
     *
     * The server has no `pinned` field, but it does accept a metadata object on PATCH and returns
     * it on read — so a pin travels with the conversation instead of dying with the install.
     */
    val pinned: Boolean = false,
    /** The title the server itself reported, kept so a local rename can be undone. */
    val serverTitle: String = title,
)

/** Lifecycle of the headless `opencode serve` process the chat talks to. */
enum class ServerState { Starting, Ready, Failed }

/** A selectable model, flattened out of `GET /config/providers`. */
data class ModelInfo(
    val providerID: String,
    val providerName: String,
    val modelID: String,
    val modelName: String,
    /** False means the model is text-only: attaching a picture would be rejected. */
    val attachment: Boolean,
    val toolcall: Boolean,
    /**
     * Reasoning-effort variants this model exposes (`low` / `medium` / `high`, sometimes `max`).
     * Empty when the catalogue does not list any, which is the common case.
     */
    val variants: List<String> = emptyList(),
    /**
     * Price per million tokens, straight from the catalogue. `-1.0` means the catalogue did not say,
     * which is not the same as free — see [isFree].
     */
    val costPerMillion: Double = -1.0,
    /** True when the user has credentials for [providerID]; from `GET /provider`. */
    val connected: Boolean = false,
) {
    val key: String get() = "$providerID/$modelID"

    /**
     * True when this model costs nothing.
     *
     * Two independent signals, because either can lie: the catalogue's own price (input + output),
     * and the id convention every free-tier provider uses (`…:free`, `free-…`, `…-free`). A model
     * with no price data at all is deliberately **not** treated as free — assuming "unknown means
     * free" would quietly start spending the user's money.
     */
    val isFree: Boolean
        get() {
            val id = "$modelID $modelName".lowercase()
            if (FREE_ID_MARKERS.any { id.contains(it) }) return true
            if (costPerMillion < 0) return false
            return costPerMillion <= 0.0
        }

    /**
     * A coarse capability rank, used only to break ties inside one cost tier when a fallback model
     * has to be chosen.
     *
     * This is a keyword heuristic over the model id. It is deliberately blunt: it only has to answer
     * "is this plausibly bigger than the one that just failed", and every signal it uses is a tier
     * name the providers themselves put in the id (`mini`, `haiku`, `nano` down; `max`, `opus`,
     * `ultra` up).
     */
    val strength: Int = run {
        val id = "$modelID $modelName".lowercase()
        var score = 0
        for ((needle, points) in STRENGTH_TIERS) {
            if (id.contains(needle)) score += points
        }
        // Tool calling is required for this app to be usable at all, so a text-only model is never
        // a better fallback than a tool-calling one.
        if (toolcall) score += 1
        score
    }

    private companion object {
        /** Substrings every free-tier provider uses to mark a free model in its id. */
        val FREE_ID_MARKERS = listOf(":free", "-free", "free-", "/free", "_free")

        val STRENGTH_TIERS = listOf(
            "ultra" to 4, "opus" to 4, "max" to 3, "large" to 3, "pro" to 3, "plus" to 2,
            "turbo" to 2, "flash" to 1, "standard" to 1,
            "mini" to -3, "haiku" to -3, "nano" to -3, "lite" to -3, "small" to -2, "instant" to -2,
        )
    }
}

/**
 * Picks a model to fall back to when [failed] is out of quota or out of context window.
 *
 * Free models are preferred over stronger paid ones — the whole point of the fallback is to keep
 * the user working, not to escalate their bill. Within the same cost tier the stronger model wins,
 * and a model with no cost data is never preferred over one known to be free.
 *
 * [excludeKeys] are the models already tried this turn, so a retry can never bounce back to where
 * it started or re-attempt a model that has just failed.
 */
fun pickStrongerModel(
    models: List<ModelInfo>,
    failed: ModelInfo?,
    excludeKeys: Set<String>,
): ModelInfo? {
    if (models.isEmpty()) return null

    // Never switch to a provider the user has no key for: opencode would reject it with a 401 and
    // the retry would burn the same turn for nothing. Unconnected entries are only useful as a
    // last resort when literally nothing connected is available.
    val connected = models.filter { it.connected }
    val base = if (connected.isEmpty()) models else connected

    val blocked = buildSet {
        addAll(excludeKeys)
        failed?.key?.let { add(it) }
    }
    val candidates = base.filterNot { it.key in blocked }
    if (candidates.isEmpty()) return null

    // Only tool-calling models are usable at all here: without tool access the agent cannot read or
    // edit anything, so a free-but-blind model is no fallback at all.
    val usable = candidates.filter { it.toolcall }
    val pool = if (usable.isNotEmpty()) usable else candidates

    val free = pool.filter { it.isFree }
    val paid = pool.filterNot { it.isFree }

    return best(free, failed) ?: best(paid, failed)
}

/**
 * Free models the user has asked to be preferred, strongest first.
 *
 * Chosen rather than derived: `strength` is a keyword heuristic over model ids and scores almost
 * everything the same, so left to itself auto-free lands on whatever happens to sort first. These
 * are the ones actually wanted. Matching is by substring on `provider/model` so a model that moves
 * provider or gains a suffix still matches.
 */
private val AUTO_FREE_PRIORITY = listOf(
    "opencode/space-bunny-free",
    "xkiro/stealth/space-bunny-alpha",
    "opencode/mimo-v2.6-flash-free",
    "opencode/muse-spark-1.3-contributor-free",
    "opencode/nemotron-3-ultra-free",
    "opencode/nemotron-3.5-lightning-free",
)

/**
 * Picks the free model auto-free should use.
 *
 * Walks [AUTO_FREE_PRIORITY] in order and returns the first that is present, free, tool-calling and
 * connected — every condition matters, because a model on an unauthenticated provider fails with a
 * 401 and a model without tool access cannot drive the agent at all. Falls back to the strongest
 * free model available when none of the preferred ones are reachable.
 */
fun pickAutoFreeModel(pool: List<ModelInfo>): ModelInfo? {
    val usable = pool.filter { it.isFree && it.toolcall && it.connected }
    for (key in AUTO_FREE_PRIORITY) {
        val hit = usable.firstOrNull { it.key.startsWith(key) }
        if (hit != null) return hit
    }
    return usable.maxByOrNull { it.strength }
}

private fun best(pool: List<ModelInfo>, failed: ModelInfo?): ModelInfo? {
    if (pool.isEmpty()) return null
    // Deliberately NOT filtered by strength. An earlier version only considered models scoring
    // above the one that had just failed, which is backwards for this job: hitting a quota is a
    // reason to drop DOWN to something that will still answer, not to demand something even bigger.
    // With 1100 free models almost all scoring the same, that filter left the search empty and the
    // caller fell through to paid models — which is why the switch appeared to only ever reach the
    // basic tiers.
    //
    // Staying on the provider the user already authenticated still wins, because switching needs
    // credentials that may not exist. Otherwise the strongest available model in the tier.
    return pool.firstOrNull { failed != null && it.providerID == failed.providerID }
        ?: pool.maxByOrNull { it.strength }
}

/**
 * True when a failure looks like the model refusing the work for capacity reasons rather than for
 * anything the user or the agent did wrong.
 *
 * Providers spell this many ways and with several HTTP codes, so this matches on the status line and
 * on the message body rather than trusting a single code.
 */
fun isLimitFailure(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val text = message.lowercase()
    if (LIMIT_CODES.any { text.startsWith(it) || text.contains(it) }) return true
    return LIMIT_WORDS.any { text.contains(it) }
}

private val LIMIT_CODES = listOf("http 429", "http 402", "http 413")

private val LIMIT_WORDS = listOf(
    "rate limit", "rate_limit", "too many requests", "quota", "insufficient_quota",
    "billing", "payment required", "exceeded your current quota",
    "context length", "context_length", "maximum context", "max_tokens",
    "too many tokens", "token limit", "string too long", "input is too long",
    "prompt is too long", "request too large", "payload too large", "413",
    "model is overloaded", "overloaded", "no capacity", "capacity",
)

/** An agent from `GET /agent`; `build` and `plan` are the two the TUI exposes as modes. */
data class AgentInfo(
    val name: String,
    val description: String,
    val mode: String,
)

/** A file or picture waiting to be sent with the next prompt. */
data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    /** Self-contained `data:<mime>;base64,...` payload - the container cannot see app storage. */
    val dataUri: String,
    /** Decoded preview for images, null for documents or when decoding failed. */
    val thumbnail: Bitmap? = null,
) {
    val isImage: Boolean get() = mime.startsWith("image/")

    /** Decodes the [dataUri] back to raw bytes, for writing the file out to disk. */
    fun bytes(): ByteArray? {
        val comma = dataUri.indexOf(',')
        if (comma < 0) return null
        return runCatching {
            android.util.Base64.decode(dataUri.substring(comma + 1), android.util.Base64.DEFAULT)
        }.getOrNull()
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Attachment && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}

/** A tool waiting for the user to approve it (`permission.asked` / `permission.v2.asked`). */
data class PermissionPrompt(
    val id: String,
    val sessionID: String,
    val permission: String,
    val patterns: List<String>,
    /** True when the request came from the v2 endpoint, which uses a different reply route. */
    val v2: Boolean,
    val resources: List<String> = emptyList(),
)

/** A multiple-choice question the agent asked through its `question` tool. */
data class QuestionPrompt(
    val id: String,
    val sessionID: String,
    /** One block per question in the request; the tool almost always sends exactly one. */
    val blocks: List<QuestionBlock>,
) {
    /** True when the answer can be submitted by tapping an option, with no extra confirm step. */
    val singleTap: Boolean
        get() = blocks.size == 1 && !blocks[0].multiple && !blocks[0].custom
}

data class QuestionBlock(
    val header: String,
    val question: String,
    val options: List<QuestionChoice>,
    val multiple: Boolean,
    val custom: Boolean,
)

data class QuestionChoice(val label: String, val description: String)
