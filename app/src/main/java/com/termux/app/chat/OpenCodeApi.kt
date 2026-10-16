package com.termux.app.chat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin HTTP + Server-Sent-Events client for the headless `opencode serve` instance running inside
 * the Debian container.
 *
 * proot does not isolate the network namespace, so the server bound to `127.0.0.1:4096` in the
 * container is reachable from the app on exactly the same address - a plain [HttpURLConnection]
 * is enough and no new dependency is required.
 *
 * All methods here block; call them from [Dispatchers.IO].
 */
object OpenCodeApi {
    private const val TAG = "OpenCodeApi"

    const val PORT = 4096
    const val BASE = "http://127.0.0.1:$PORT"

    /** Set while an SSE reader is running so it can be stopped from another thread. */
    @Volatile
    private var eventsOpen = false
    @Volatile
    private var currentEventsConnection: HttpURLConnection? = null

    // ---------------------------------------------------------------------------------------------
    // Request plumbing
    // ---------------------------------------------------------------------------------------------

    private fun connection(method: String, path: String, body: String?, timeoutMs: Int): HttpURLConnection {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return conn
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        timeoutMs: Int = 45_000,
    ): String {
        val conn = connection(method, path, body, timeoutMs)
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code for $method $path: ${text.take(300)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun get(path: String, timeoutMs: Int = 45_000): String =
        request("GET", path, null, timeoutMs)

    private fun post(path: String, body: String, timeoutMs: Int = 45_000): String =
        request("POST", path, body, timeoutMs)

    // ---------------------------------------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------------------------------------

    /** Cheap liveness probe used to decide whether the server needs to be (re)started. */
    fun health(timeoutMs: Int = 3_000): Boolean = try {
        val text = get("/global/health", timeoutMs)
        JSONObject(text).optBoolean("healthy", false)
    } catch (t: Throwable) {
        false
    }

    /**
     * The configured default model, e.g. `big-pickle`.
     *
     * `/config/providers` reports `default` as a map of `providerID -> modelID`; an empty result
     * simply means the model label stays unset until an assistant reply names it.
     */
    fun defaultModel(timeoutMs: Int = 8_000): String = try {
        val defaults = JSONObject(get("/config/providers", timeoutMs)).optJSONObject("default")
        if (defaults == null || defaults.length() == 0) {
            ""
        } else {
            defaults.keys().next()
        }
    } catch (t: Throwable) {
        ""
    }

    fun createSession(title: String? = null, timeoutMs: Int = 20_000): String {
        val payload = JSONObject()
        if (title != null) payload.put("title", title)
        return JSONObject(post("/session", payload.toString(), timeoutMs)).getString("id")
    }

    fun deleteSession(sessionId: String) {
        runCatching { request("DELETE", "/session/$sessionId", null, 15_000) }
    }

    /**
     * Renames a session on the server.
     *
     * opencode's published spec documents `session.update` as `PATCH /api/session/{sessionID}`
     * carrying a `title`, answering `204`. This client, though, speaks the older un-prefixed
     * namespace throughout — `/global/health`, `/config/providers`, `/session` — and that namespace
     * does not appear in the spec at all. Rather than assume which one this server actually serves,
     * both are tried, legacy first because that is the rest of this client's convention.
     *
     * @return true when a server accepted the rename. False tells the caller to fall back to a
     *   device-local title, so the UI never implies a sync that did not actually happen.
     */
    fun renameSession(sessionId: String, title: String): Boolean {
        val body = JSONObject().put("title", title).toString()
        // A 204 has no body, so success is carried entirely by the status code; [request] throws
        // on anything outside 2xx, which is exactly the signal needed here.
        for (path in listOf("/session/$sessionId", "/api/session/$sessionId")) {
            if (runCatching { request("PATCH", path, body, 15_000) }.isSuccess) return true
        }
        return false
    }

    fun listSessions(timeoutMs: Int = 20_000): List<SessionInfo> {
        val array = JSONArray(get("/session", timeoutMs))
        val out = ArrayList<SessionInfo>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isEmpty()) continue
            out += SessionInfo(
                id = id,
                title = obj.optString("title").ifEmpty { "Untitled" },
                updated = obj.optJSONObject("time")?.optLong("updated") ?: 0L,
            )
        }
        return out.sortedByDescending { it.updated }
    }

    /**
     * Full conversation history for a session: `[{info, parts}, ...]`.
     *
     * `GET /session/:id/message` returns an array, but a few server versions hand back a single
     * `{info, parts}` object instead - both shapes are accepted so a history reload can never
     * blank out the conversation after a successful reply.
     */
    fun messages(sessionId: String, timeoutMs: Int = 30_000): List<ChatMessage> {
        val body = get("/session/$sessionId/message", timeoutMs).trim()
        val array = when {
            body.startsWith("[") -> JSONArray(body)
            body.startsWith("{") -> JSONArray().put(JSONObject(body))
            else -> JSONArray()
        }
        val out = ArrayList<ChatMessage>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val info = entry.optJSONObject("info") ?: continue
            val id = info.optString("id")
            if (id.isEmpty()) continue
            out += ChatMessage(
                id = id,
                role = info.optString("role").ifEmpty { "assistant" },
                parts = parseParts(entry.optJSONArray("parts")),
                modelID = modelOf(info),
                createdAt = createdOf(info),
            )
        }
        return out
    }

    /**
     * Hard-deletes one message.
     *
     * This is what backs regenerate and edit-prompt. It is a deletion rather than a revert on
     * purpose: `POST /session/:id/revert` would also roll the working tree back to its snapshot, and
     * someone asking for a different answer does not want the files the agent already wrote to
     * disappear. Returns false rather than throwing, so a batch delete can carry on.
     */
    fun deleteMessage(sessionId: String, messageId: String, timeoutMs: Int = 15_000): Boolean = try {
        request("DELETE", "/session/$sessionId/message/$messageId", null, timeoutMs)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "Could not delete $messageId: ${t.message?.take(120)}")
        false
    }

    /**
     * Reads a message's creation time, accepting the shapes opencode has used for it.
     *
     * `time.created` is the current one; the bare `created` field is older builds and some event
     * payloads. Anything unrecognised returns 0, which the UI renders as "no timestamp" rather than
     * inventing an epoch.
     */
    private fun createdOf(info: JSONObject): Long {
        val time = info.optJSONObject("time")
        val nested = time?.optLong("created") ?: 0L
        if (nested > 0) return nested
        val flat = info.optLong("created") ?: 0L
        if (flat > 0) return flat
        val completed = time?.optLong("completed") ?: 0L
        return if (completed > 0) completed else 0L
    }

    /**
     * Sends a prompt and blocks until the model finishes.
     *
     * Images ride along as `file` parts carrying a `data:` URI, because that is the only form a
     * vision model will accept. Everything else is referenced by path instead: a `.zip` inlined as
     * base64 is unreadable to the model *and* bloats the request past what providers will take, which
     * is why attaching one used to produce no reply at all. The agent gets the path and can unpack it
     * with its own tools.
     *
     * The SSE stream is what makes the reply feel live; this call is the authoritative result. It
     * also guarantees completion even if the event stream drops, because the caller reloads history
     * once this returns.
     */
    fun send(
        sessionId: String,
        text: String,
        images: List<Attachment> = emptyList(),
        fileRefs: List<String> = emptyList(),
        model: ModelInfo? = null,
        agent: String? = null,
        credentialNote: String? = null,
        timeoutMs: Int = 300_000,
    ): String {
        val parts = JSONArray()
        for (attachment in images) {
            parts.put(
                JSONObject()
                    .put("type", "file")
                    .put("mime", attachment.mime)
                    .put("url", attachment.dataUri)
                    .put("filename", attachment.name),
            )
        }
        // Name the on-disk files so the agent knows they exist and can reach them with bash/unzip.
        // Images are called out separately because they have an extra constraint: the in-chat
        // preview serves only the folder the HTML lives in, so a picture referenced from another
        // directory will not load.
        val imagePaths = fileRefs.filter { isImagePath(it) }
        val otherPaths = fileRefs.filterNot { isImagePath(it) }
        val body = buildString {
            if (text.isNotEmpty()) append(text)

            if (imagePaths.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("The user attached ")
                append(if (imagePaths.size == 1) "this image" else "these images")
                append(", saved on disk:\n")
                imagePaths.forEach { append("- ").append(it).append('\n') }
                append(
                    "You can see them in this message, and the files above are the real bytes. " +
                        "If you reference one from a file you create, copy it next to that file " +
                        "first (same directory) and use a relative path — the preview only serves " +
                        "the folder the page itself is in."
                )
            }

            if (otherPaths.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Also on disk in the workspace:\n")
                otherPaths.forEach { append("- ").append(it).append('\n') }
                append("Read or unpack ")
                append(if (otherPaths.size == 1) "it" else "them")
                append(" with your tools before answering.")
            }

            // Same idea as the attachments above: tell the agent what is available and where, not
            // what it contains. The note names the variables and the path; the values stay on disk.
            if (!credentialNote.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(credentialNote)
            }
        }
        parts.put(JSONObject().put("type", "text").put("text", body))
        val payload = JSONObject().put("parts", parts)
        if (model != null) {
            payload.put(
                "model",
                JSONObject().put("providerID", model.providerID).put("modelID", model.modelID),
            )
        }
        if (!agent.isNullOrBlank()) payload.put("agent", agent)
        return post("/session/$sessionId/message", payload.toString(), timeoutMs)
    }

    /** Politely interrupts the in-flight reply, which is what the ⏹ button calls. */
    fun abort(sessionId: String) {
        runCatching { request("POST", "/session/$sessionId/abort", "{}", 15_000) }
    }

    // ---------------------------------------------------------------------------------------------
    // Models and agents
    /**
     * Every model opencode advertises, ignoring credentials.
     *
     * [models] narrows this to providers the user has actually authenticated, which is right for a
     * picker — but as the pool for a quota fallback it is fatal: authenticated with exactly one
     * provider means the filtered list holds one model and there is nothing to switch to. This
     * unfiltered list is what makes "switch to a stronger model" possible at all.
     */
    fun allModels(timeoutMs: Int = 20_000): List<ModelInfo> {
        // Reads from /provider, NOT /config/providers. The latter only advertises 3 providers
        // (the ones the user has explicitly configured), so its `providers` array has 3 entries
        // and almost no free models. /provider's `all` array has every provider opencode knows
        // about — 230 providers, 1113 free models — which is what the fallback pool needs.
        val allResp = JSONObject(get("/provider", timeoutMs))
        val providers = allResp.optJSONArray("all") ?: return emptyList()
        val out = ArrayList<ModelInfo>()
        for (i in 0 until providers.length()) {
            val provider = providers.optJSONObject(i) ?: continue
            val providerID = provider.optString("id")
            if (providerID.isEmpty()) continue
            val providerName = provider.optString("name").ifEmpty { providerID }
            val models = provider.optJSONObject("models") ?: continue
            for (modelID in models.keys()) {
                val model = models.optJSONObject(modelID) ?: continue
                val capabilities = model.optJSONObject("capabilities")
                out += ModelInfo(
                    providerID = providerID,
                    providerName = providerName,
                    modelID = modelID,
                    modelName = model.optString("name").ifEmpty { modelID },
                    attachment = capabilities?.optBoolean("attachment") ?: false,
                    toolcall = capabilities?.optBoolean("toolcall") ?: false,
                    variants = parseVariants(model),
                    costPerMillion = parseCost(model),
                    connected = connected.contains(providerID),
                )
            }
        }
        return out.sortedWith(compareBy({ it.providerName }, { it.modelName }))
    }

    /**
     * Providers the user has asked to hide from the picker.
     *
     * Some of the connected providers are ones the user has explicitly said they don't want - the
     * browser bridge, for example. They stay in the server's config (removing them there would
     * require shell access the user shouldn't need), but they are filtered out of the model picker
     * and out of the automatic fallback pool. That way "auto free switch" never lands on a provider
     * the user has blocked.
     */
    val HIDDEN_PROVIDERS = setOf("oxalpha")

    /**
     * Free models that were probed and answered, kept for reference only — **not** an allowlist.
     *
     * A probe run over every free tool-calling model on the connected providers returned a working
     * reply from only two, but 50 of the failures were rate-limit responses rather than dead
     * models. Cutting the list to those two would have been wrong: a free tier that recovers would
     * have stayed excluded forever, and a fallback needs options precisely when the current model
     * has just refused to work. So nothing is filtered on the strength of one probe — the fallback
     * simply avoids whatever it has already tried this turn ([triedKeys]) and moves on.
     */
    val VERIFIED_FREE_MODELS = setOf(
        "kilo/poolside/laguna-s-2.1:free",
        "kilo/nvidia/nemotron-3-ultra-550b-a55b:free",
    )

    /**
     * Models the app can actually reach.
     *
     * `/provider` advertises every provider opencode knows about - over 200 providers with 1000+
     * free models. The list is narrowed to connected providers, minus any the user has hidden.
     * Without any credentials at all the raw list (minus hidden) is returned rather than an empty
     * picker.
     */
    fun models(timeoutMs: Int = 20_000): List<ModelInfo> {
        // Free only. The user asked for this outright: a picker full of 7000 paid models is noise
        // when every one of them is unreachable without a separate account and key, and it buries
        // the free ones that actually work. The fallback pool is filtered the same way for the same
        // reason — auto-switch must never land on a model that would charge them.
        val free = allModels(timeoutMs)
            .filter { it.providerID !in HIDDEN_PROVIDERS && it.isFree }
        if (connected.isEmpty()) return free
        val usable = free.filter { it.connected }
        return if (usable.isEmpty()) free else usable
    }


    /**
     * Agents the user may actually switch a conversation to.
     *
     * `GET /agent` returns opencode's plumbing alongside the real modes: `compaction`, `summary` and
     * `title` are invoked by the server itself, and `explore`/`general` are task subagents rather
     * than conversation modes. Keeping only primary non-internal agents leaves `build` and `plan`,
     * plus anything the user registers as a primary agent of their own.
     */
    private val INTERNAL_AGENTS = setOf("compaction", "summary", "title")

    fun agents(timeoutMs: Int = 15_000): List<AgentInfo> {
        val array = JSONArray(get("/agent", timeoutMs))
        val out = ArrayList<AgentInfo>(array.length())
        for (i in 0 until array.length()) {
            val agent = array.optJSONObject(i) ?: continue
            val name = agent.optString("name")
            if (name.isEmpty() || name in INTERNAL_AGENTS) continue
            val mode = agent.optString("mode").ifEmpty { "primary" }
            if (mode != "primary") continue
            out += AgentInfo(
                name = name,
                description = agent.optString("description"),
                mode = mode,
            )
        }
        // A prompt always carries an `agent`, so an empty result must not leave the chip blank.
        if (out.isEmpty()) out += AgentInfo(name = "build", description = "The default agent.", mode = "primary")
        return out
    }

    /**
     * Blended price per million tokens, read defensively from the catalogue entry.
     *
     * opencode exposes this either as a `cost` object (OpenRouter style, with separate input/output
     * rates) or as a plain `cost` number. Anything else returns -1 so callers can tell "no data"
     * apart from "genuinely zero" — that distinction matters, because treating unknown as free would
     * start spending the user's money without asking.
     */
    private fun parseCost(model: JSONObject): Double {
        val cost = model.opt("cost") ?: return -1.0
        return when (cost) {
            is Number -> cost.toDouble()
            is JSONObject -> {
                val input = cost.optDouble("input", 0.0)
                val output = cost.optDouble("output", input)
                val cacheRead = cost.optDouble("cache_read", 0.0)
                // Unweighted blend: good enough to rank tiers, which is all this is used for.
                (input * 3 + output * 1 + cacheRead * 1) / 5.0
            }
            else -> -1.0
        }
    }

    /** Cheap test used only to split the prompt's file list into pictures and everything else. */
    private fun isImagePath(path: String): Boolean = IMAGE_PATH.containsMatchIn(path)

    private val IMAGE_PATH =
        Regex("\\.(png|jpe?g|gif|webp|bmp|avif|heic)$", RegexOption.IGNORE_CASE)

    /**
     * Reasoning-effort variants a model offers, read defensively from the catalogue entry.
     *
     * opencode nests this differently depending on the provider — an object of named variants on some,
     * a plain array on others — so both shapes are accepted and anything unrecognised is ignored
     * rather than guessed at.
     */
    private fun parseVariants(model: JSONObject): List<String> {
        val raw = model.opt("variants") ?: model.opt("reasoningEfforts") ?: return emptyList()
        val names = LinkedHashSet<String>()
        when (raw) {
            is JSONArray -> for (i in 0 until raw.length()) {
                val entry = raw.opt(i)
                when (entry) {
                    is String -> names += entry
                    is JSONObject -> entry.optString("name").takeIf { it.isNotEmpty() }?.let { names += it }
                }
            }
            is JSONObject -> for (key in raw.keys()) {
                if (key.isNotEmpty()) names += key
            }
        }
        return names.toList()
    }

    /**
     * Provider ids opencode currently has credentials for.
     *
     * `/provider` answers with an object of {default, connected, all}, where `connected` is the list
     * of providers the user has actually authenticated. This is what the picker shows so the user can
     * see which custom providers are wired up, rather than inferring it from the model list.
     */
    fun connectedProviders(timeoutMs: Int = 15_000): List<String> {
        val obj = JSONObject(get("/provider", timeoutMs))
        val out = ArrayList<String>()
        for (key in listOf("connected", "linked")) {
            val array = obj.optJSONArray(key) ?: continue
            for (i in 0 until array.length()) {
                val id = array.opt(i)?.toString().orEmpty()
                if (id.isNotEmpty() && id !in out) out += id
            }
        }
        connected = out.sorted().toSet()
        return connected.toList()
    }

    /**
     * Provider ids with credentials, cached from the last [connectedProviders] call.
     *
     * Cached because [allModels] needs it on every catalogue load, and a failed `/provider` fetch
     * must not silently mark every provider as disconnected.
     */
    @Volatile
    private var connected: Set<String> = emptySet()

    /**
     * The command a user runs inside the container to add or connect a provider.
     *
     * `opencode auth login` is interactive, so it cannot be driven over the HTTP API from here; the
     * UI sends the user to the terminal with this line rather than pretending to do it in place.
     */
    const val AUTH_COMMAND = "opencode auth login"

    // ---------------------------------------------------------------------------------------------
    // Interrupts, permissions and questions
    // ---------------------------------------------------------------------------------------------

    /** The model/agent a session was created with, so reopening a chat keeps its settings. */
    fun session(sessionId: String, timeoutMs: Int = 15_000): Pair<String?, ModelInfo?> {
        val obj = JSONObject(get("/session/$sessionId", timeoutMs))
        val agent = obj.optString("agent").takeIf { it.isNotEmpty() }
        val model = obj.optJSONObject("model") ?: return agent to null
        val modelID = model.optString("id").ifEmpty { model.optString("modelID") }
        val providerID = model.optString("providerID")
        return agent to if (modelID.isNotEmpty() && providerID.isNotEmpty()) {
            ModelInfo(providerID, providerID, modelID, modelID, attachment = false, toolcall = true)
        } else {
            null
        }
    }

    fun pendingPermissions(timeoutMs: Int = 10_000): List<PermissionPrompt> =
        parsePermissions(JSONArray(get("/permission", timeoutMs)))

    fun pendingQuestions(timeoutMs: Int = 10_000): List<QuestionPrompt> =
        parseQuestions(JSONArray(get("/question", timeoutMs)))

    /**
     * Approves or rejects a pending tool call.
     *
     * opencode exposes two generations of this endpoint; the event that delivered the request tells
     * us which one to answer, and answering on the wrong route silently hangs the session.
     */
    fun replyPermission(prompt: PermissionPrompt, response: String) {
        val body = JSONObject().put(if (prompt.v2) "reply" else "response", response)
        val path = if (prompt.v2) {
            "/permission/${prompt.id}/reply"
        } else {
            "/session/${prompt.sessionID}/permissions/${prompt.id}"
        }
        request("POST", path, body.toString(), 15_000)
    }

    fun replyQuestion(questionId: String, answers: List<List<String>>) {
        val array = JSONArray()
        answers.forEach { choice ->
            val inner = JSONArray()
            choice.forEach { inner.put(it) }
            array.put(inner)
        }
        request("POST", "/question/$questionId/reply", JSONObject().put("answers", array).toString(), 15_000)
    }

    fun rejectQuestion(questionId: String) {
        runCatching { request("POST", "/question/$questionId/reject", "{}", 15_000) }
    }

    // ---------------------------------------------------------------------------------------------
    // Event stream
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads `GET /event` until [alive] returns false, invoking [onEvent] for every `data:` frame.
     *
     * Transient failures are retried after a short pause so a server restart does not permanently
     * kill the live view; [stopEvents] closes the open socket to unblock the reader on shutdown.
     */
    suspend fun streamEvents(alive: () -> Boolean, onEvent: (JSONObject) -> Unit) {
        eventsOpen = true
        while (alive() && eventsOpen) {
            var conn: HttpURLConnection? = null
            try {
                conn = connection("GET", "/event", null, 10_000)
                // An SSE connection is silent between events, so never time out on read.
                conn.readTimeout = 0
                conn.connect()
                currentEventsConnection = conn
                val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
                while (alive() && eventsOpen) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload.isEmpty()) continue
                    runCatching { onEvent(JSONObject(payload)) }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (alive() && eventsOpen) delay(1_500)
            } finally {
                currentEventsConnection = null
                conn?.disconnect()
            }
        }
        eventsOpen = false
    }

    /** Forces the blocking SSE read to fail so [streamEvents] can notice it is shutting down. */
    fun stopEvents() {
        eventsOpen = false
        runCatching { currentEventsConnection?.disconnect() }
        currentEventsConnection = null
    }

    // ---------------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------------

    /**
     * Extracts the model name from a message `info` object.
     *
     * History and `message.updated` events name it differently: the former uses a flat `modelID`,
     * the latter nests it under `model`. Both spellings are accepted.
     */
    fun modelOf(info: JSONObject): String =
        info.optString("modelID").ifEmpty { info.optJSONObject("model")?.optString("modelID").orEmpty() }

    private fun parseParts(array: JSONArray?): List<ChatPart> {
        if (array == null) return emptyList()
        val out = ArrayList<ChatPart>(array.length())
        for (i in 0 until array.length()) {
            val part = array.optJSONObject(i) ?: continue
            val parsed = parsePart(part)
            if (parsed != null) out += parsed
        }
        return out
    }

    /**
     * Converts a server part into a [ChatPart], or returns null for the separator types
     * (`step-start`, `step-finish`, ...) that carry nothing worth rendering.
     */
    fun parsePart(part: JSONObject): ChatPart? {
        val type = part.optString("type")
        return when (type) {
            "text", "reasoning" -> ChatPart(
                id = part.optString("id"),
                type = type,
                text = part.optString("text"),
            )
            "tool" -> ChatPart(
                id = part.optString("id"),
                type = "tool",
                tool = part.optString("tool"),
                state = parseToolState(part.optJSONObject("state")),
            )
            // Pictures the model was given (or produced) arrive as `file` parts. Keeping them is what
            // lets the chat render a real preview instead of a bare "[image]" placeholder.
            "file" -> ChatPart(
                id = part.optString("id"),
                type = "file",
                filename = part.optString("filename").ifEmpty { part.optString("name") },
                mime = part.optString("mime"),
                url = part.optString("url"),
            )
            else -> null
        }
    }

    private fun parseToolState(state: JSONObject?): ToolState {
        if (state == null) return ToolState()
        val output = when {
            !state.isNull("output") && state.has("output") -> state.optString("output")
            state.has("raw") -> state.optString("raw")
            else -> ""
        }
        return ToolState(
            status = state.optString("status").ifEmpty { "pending" },
            input = state.optJSONObject("input")?.toString().orEmpty(),
            output = output,
        )
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }

    /** Builds a pending permission from either generation of the `permission` payload. */
    fun parsePermission(data: JSONObject, v2: Boolean): PermissionPrompt? {
        val id = data.optString("id")
        if (id.isEmpty()) return null
        return PermissionPrompt(
            id = id,
            sessionID = data.optString("sessionID"),
            permission = data.optString("permission").ifEmpty { data.optString("action") },
            patterns = data.stringList("patterns"),
            v2 = v2,
            resources = data.stringList("resources"),
        )
    }

    private fun parsePermissions(array: JSONArray): List<PermissionPrompt> {
        val out = ArrayList<PermissionPrompt>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parsePermission(obj, v2 = false)?.let { out += it }
        }
        return out
    }

    fun parseQuestions(data: JSONObject): QuestionPrompt? {
        val id = data.optString("id").ifEmpty { data.optString("requestID") }
        if (id.isEmpty()) return null
        val array = data.optJSONArray("questions") ?: JSONArray()
        val blocks = ArrayList<QuestionBlock>(array.length())
        for (i in 0 until array.length()) {
            val block = array.optJSONObject(i) ?: continue
            val options = block.optJSONArray("options") ?: JSONArray()
            val choices = ArrayList<QuestionChoice>(options.length())
            for (j in 0 until options.length()) {
                val option = options.optJSONObject(j) ?: continue
                val label = option.optString("label")
                if (label.isNotEmpty()) {
                    choices += QuestionChoice(label, option.optString("description"))
                }
            }
            if (choices.isEmpty()) continue
            blocks += QuestionBlock(
                header = block.optString("header").ifEmpty { "Question" },
                question = block.optString("question"),
                options = choices,
                multiple = block.optBoolean("multiple"),
                custom = block.optBoolean("custom"),
            )
        }
        if (blocks.isEmpty()) return null
        return QuestionPrompt(id, data.optString("sessionID"), blocks)
    }

    private fun parseQuestions(array: JSONArray): List<QuestionPrompt> {
        val out = ArrayList<QuestionPrompt>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseQuestions(obj)?.let { out += it }
        }
        return out
    }
}
