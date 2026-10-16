package com.termux.app.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.termux.app.shell.OpenCodeService
import com.termux.app.shell.AgentNotifications
import com.termux.app.settings.CredentialStore
import com.termux.app.shell.PendingJobStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Singleton owner of everything chat-related, for the lifetime of the **process**.
 *
 * State and coroutines deliberately live here rather than in the ViewModel: leaving the screen,
 * rotating or pressing home must not cancel a reply that is already running. The screen only ever
 * observes [state] and forwards intents.
 *
 * Threading: the scope defaults to the main dispatcher, so every mutation of [messages] and
 * [_state] is main-thread confined and needs no locking. Blocking HTTP and the SSE reader are
 * pushed onto [Dispatchers.IO].
 */
object OpenCodeRuntime {
    private const val TAG = "OpenCodeRuntime"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /**
     * Device-local conversation titles, keyed by session id.
     *
     * Only consulted when opencode refuses a rename — the server owns the title whenever it will
     * take it. Kept as one store on purpose: this used to be split between this map and a separate
     * SharedPreferences wrapper in the ViewModel, and the two went out of sync across restarts.
     */
    private val titleOverrides = ConcurrentHashMap<String, String>()

    /** Backing file for [titleOverrides]. */
    private const val TITLE_PREFS = "opencode_titles"

    private val events = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Insertion-ordered so replies stay chronological. */
    private val messages = LinkedHashMap<String, ChatMessage>()

    private var appContext: Context? = null
    private var started = false
    private var eventsActive = false
    private var sendJob: Job? = null

    /** Debounce for the changed-file list, which updates on every `file.edited` event. */
    private const val FILE_REFRESH_MS = 700L

    // -------------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------------

    /** Boots the backend the first time a chat screen appears; a no-op afterwards. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        // Restored before anything reads the drawer. This was a real bug: the overrides map is
        // process-scoped, so a title that had to be stored locally (because the server refused the
        // rename) survived in SharedPreferences but vanished from the UI on the next launch — the
        // drawer silently reverted to the server's name. Seeding here closes that gap.
        app.getSharedPreferences(TITLE_PREFS, Context.MODE_PRIVATE).all.forEach { (id, name) ->
            if (name is String && name.isNotBlank()) titleOverrides[id] = name
        }
        keepAlive(app)
        OpenCodeService.publishStatus(app, "Starting the opencode server…")

        scope.launch {
            _state.update { it.copy(server = ServerState.Starting, error = null) }
            val healthy = withContext(Dispatchers.IO) {
                OpenCodeServer.ensureStarted(app)
                var attempt = 0
                while (attempt < 60) {
                    if (OpenCodeApi.health()) return@withContext true
                    delay(1_000)
                    attempt++
                }
                OpenCodeApi.health()
            }
            if (!healthy) {
                _state.update { it.copy(server = ServerState.Failed) }
                OpenCodeService.publishStatus(app, "The opencode server is not responding.")
                started = false
                return@launch
            }
            _state.update { it.copy(server = ServerState.Ready) }
            OpenCodeService.publishStatus(app, "OpenCode is ready.")
            startEventStream()
            loadMeta()
            loadSkills()
            refreshFiles()
            refreshSessions(pickRecent = true)
            refreshPending()
        }
    }

    /** Loads agents and the model catalogue once the server is up. */
    private fun loadMeta() {
        scope.launch {
            val meta = withContext(Dispatchers.IO) {
                // Providers first: the catalogue needs their connected flags, and the fallback pool
                // needs the unfiltered list, or a single-provider setup has nothing to switch to.
                val providers = runCatching { OpenCodeApi.connectedProviders() }.getOrDefault(emptyList())
                // Free only, hidden providers removed. The fallback must never pick something that would bill the
                // user: the whole point of auto-switch is a free model taking over, not a surprise
                // charge on the next turn.
                val all = runCatching { OpenCodeApi.allModels() }.getOrDefault(emptyList())
                    .filter { it.providerID !in OpenCodeApi.HIDDEN_PROVIDERS && it.isFree }
                val models = all.filter { it.connected }.ifEmpty { all }
                val agents = runCatching { OpenCodeApi.agents() }.getOrDefault(emptyList())
                val fallback = runCatching { OpenCodeApi.defaultModel() }.getOrDefault("")
                Meta(agents, models, fallback, providers, all)
            }
            _state.update { current ->
                current.copy(
                    agents = meta.agents,
                    models = meta.models,
                    providers = meta.providers,
                    fallbackPool = meta.all,
                    modelLabel = meta.fallback,
                    selectedAgent = current.selectedAgent.ifEmpty {
                        meta.agents.firstOrNull { it.name == "build" }?.name
                            ?: meta.agents.firstOrNull()?.name.orEmpty()
                    },
                )
            }
        }
    }

    private data class Meta(
        val agents: List<AgentInfo>,
        val models: List<ModelInfo>,
        val fallback: String,
        val providers: List<String>,
        /** Every advertised model, used only as the pool for a limit fallback. */
        val all: List<ModelInfo>,
    )

    /**
     * Waits for the SSE stream to report idle, then reloads the transcript.
     *
     * This is what makes a POST timeout harmless: the socket gave up, but the server did not. The
     * reply keeps arriving over the event stream, and this picks it up once it finishes.
     */
    private fun awaitIdleThenReload() {
        scope.launch {
            var waited = 0
            while (waited < 15 * 60_000) {
                if (!_state.value.busy) break
                delay(1_000)
                waited += 1_000
            }
            // Still busy after 15 minutes: something is genuinely stuck, so surface that much later
            // than a five-minute timeout would have.
            if (_state.value.busy) {
                _state.update { it.copy(busy = false, error = "This took longer than 15 minutes.") }
                return@launch
            }
            val sessionId = _state.value.sessionId
            if (sessionId != null) {
                val history = withContext(Dispatchers.IO) {
                    runCatching { OpenCodeApi.messages(sessionId) }.getOrNull()
                }
                if (history != null) replaceMessages(history) else publish()
            }
            appContext?.let { PendingJobStore.clear(it) }
            refreshPending()
        }
    }

    /**
     * Switches to a stronger model and re-sends the prompt that just failed.
     *
     * Reached from the SSE stream, which is where a token or context limit actually surfaces — the
     * blocking POST returns 200 for those, so the retry that lives there never sees them.
     *
     * The prompt is recovered from the transcript rather than remembered, because by the time the
     * event arrives the original send() call has long since returned and its local copy is gone.
     */
    private fun recoverFromLimit(reason: String) {
        val state = _state.value
        val failed = state.selectedModel
        val fallback = pickStrongerModel(state.fallbackPool, failed, emptySet()) ?: run {
            Log.w(TAG, "No stronger model available for: ${reason.take(120)}")
            _state.update { it.copy(error = "Hit a model limit and no stronger model is available.") }
            return
        }

        val prompt = messages.entries
            .lastOrNull { it.key.startsWith(LOCAL_PREFIX) || it.value.isUser }
            ?.value
            ?.text
            .orEmpty()
        if (prompt.isBlank()) {
            _state.update { it.copy(error = "Hit a model limit and no stronger model is available.") }
            return
        }

        val tag = if (fallback.isFree) " (free)" else " (paid)"
        val reason2 = "Limit hit on ${failed?.modelName ?: "current model"} → switched to ${fallback.modelName}$tag"
        _state.update {
            it.copy(
                selectedModel = fallback,
                modelLabel = fallback.modelName,
                variants = fallback.variants,
                selectedVariant = "",
                statusLine = reason2,
                notice = reason2,
                error = null,
            )
        }
        Log.i(TAG, "Retrying on ${fallback.modelName} after a limit")

        // Remove the failed turn so the transcript shows one clean attempt, not two.
        messages.keys.filter { it.startsWith(LOCAL_PREFIX) }.forEach { messages.remove(it) }

        _state.update { it.copy(input = prompt, attachments = emptyList()) }
        send()
    }

    private fun isTimeout(error: Throwable): Boolean =
        error is java.net.SocketTimeoutException ||
            error is java.io.InterruptedIOException ||
            error.message?.contains("timeout", ignoreCase = true) == true

    /** Re-reads the catalogue, so a provider connected after startup shows up without a restart. */
    fun refreshCatalogue() = loadMeta()

    /**
     * Reads the installed skills. Separate from [loadMeta] because it is not part of the model
     * catalogue and a failure here should not empty the picker of models.
     */
    fun loadSkills() {
        scope.launch {
            val skills = withContext(Dispatchers.IO) { OpenCodeApi.skills() }
            _state.update { it.copy(skills = skills) }
            Log.i(TAG, "Loaded ${skills.size} skill(s)")
        }
    }

    /**
     * Re-reads the changed-file list.
     *
     * Called at startup and on every `file.edited` event, so the header count tracks the agent
     * without polling. The workspace directory is not passed: the server already resolves the
     * session's own project, and guessing a path here would report a different tree.
     */
    fun refreshFiles() {
        scope.launch {
            val files = withContext(Dispatchers.IO) { OpenCodeApi.fileStatus() }
            _state.update { it.copy(files = files) }
        }
    }

    /**
     * Re-reads the file list at most once every [FILE_REFRESH_MS].
     *
     * The agent writes in bursts — a formatter plus a rename plus a rewrite can land four events in
     * a second — and each refresh is an HTTP round trip. Without this the panel would hammer the
     * server exactly when it is busiest.
     */
    private var fileRefreshJob: Job? = null

    private fun scheduleFileRefresh() {
        if (fileRefreshJob?.isActive == true) return
        fileRefreshJob = scope.launch {
            delay(FILE_REFRESH_MS)
            refreshFiles()
        }
    }

    /**
     * Pins or unpins a conversation, server-side.
     *
     * The local list is updated optimistically and then reconciled from the server's answer. If
     * the write is refused the pin reverts, because a pin the drawer does not show is worse than
     * one that never appeared.
     */
    fun togglePin(sessionId: String) {
        val current = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        val next = !current.pinned
        _state.update { state -> state.copy(sessions = state.sessions.map { if (it.id == sessionId) it.copy(pinned = next) else it }) }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { OpenCodeApi.setPinned(sessionId, next) }
            if (!ok) {
                _state.update { state ->
                    state.copy(
                        sessions = state.sessions.map { if (it.id == sessionId) it.copy(pinned = !next) else it },
                        error = "Could not pin that chat.",
                    )
                }
            }
            refreshSessions(pickRecent = false)
        }
    }

    /**
     * Raises a foreground service so the OS does not reclaim the process while a prompt is in
     * flight — this is what lets the reply continue after the user exits the app.
     */
    private fun keepAlive(context: Context) {
        runCatching {
            val intent = Intent(context, OpenCodeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { Log.w(TAG, "Could not start the foreground service", it) }
    }

    private fun startEventStream() {
        if (eventsActive) return
        eventsActive = true
        scope.launch {
            withContext(Dispatchers.IO) {
                OpenCodeApi.streamEvents(alive = { eventsActive }) { event -> events.tryEmit(event) }
            }
        }
        scope.launch { events.collect { handleEvent(it) } }
    }

    /** Drops the SSE subscription; the server itself keeps running. */
    fun shutdown() {
        eventsActive = false
        OpenCodeApi.stopEvents()
    }

    // -------------------------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------------------------

    private fun handleEvent(event: JSONObject) {
        val type = event.optString("type")
        val props = event.optJSONObject("properties") ?: event.optJSONObject("data") ?: return
        when {
            type == "message.updated" -> onMessageUpdated(props)
            type == "message.part.updated" -> onPartUpdated(props)
            type == "message.part.delta" -> onPartDelta(props)

            // The agent just wrote something. The change panel would otherwise only refresh on
            // launch, so the count in the header would sit at zero through an entire session.
            type == "file.edited" -> scheduleFileRefresh()

            // The server drops a message when it is deleted, which is what regenerate and
            // edit-prompt do. Without this the row would sit on screen until the next full history
            // reload, so a delete would look like it had not worked.
            type == "message.removed" || type == "message.part.removed" -> {
                val id = props.optString("messageID").ifEmpty { props.optString("id") }
                if (id.isNotEmpty() && messages.remove(id) != null) publish()
            }

            type == "session.status" -> {
                val status = props.optJSONObject("status")?.optString("type").orEmpty()
                setBusy(status == "busy")
            }

            type == "session.idle" -> setBusy(false)

            // opencode can report a failure mid-stream and still return 200 from the POST, so the
            // error event is the only place some limit failures are visible.
            type == "error" || type == "session.error" -> {
                val message = props.optString("message")
                    .ifEmpty { props.optJSONObject("error")?.optString("message").orEmpty() }
                if (isLimitFailure(message)) {
                    // This is the path a token limit actually takes — it never reaches the blocking
                    // POST, which is why the fallback there alone did nothing. Hand it to the same
                    // retry machinery, with the prompt that is still sitting in the transcript.
                    Log.w(TAG, "Limit reported over SSE: ${message.take(160)}")
                    recoverFromLimit(message)
                } else {
                    _state.update {
                        it.copy(error = message.take(200).ifEmpty { "Request failed" })
                    }
                }
                setBusy(false)
            }

            type == "permission.asked" ->
                _state.update { it.copy(permission = OpenCodeApi.parsePermission(props, v2 = false)) }

            type == "permission.v2.asked" ->
                _state.update { it.copy(permission = OpenCodeApi.parsePermission(props, v2 = true)) }

            type == "permission.replied" || type == "permission.v2.replied" ->
                _state.update { it.copy(permission = null) }

            type == "question.asked" || type == "question.v2.asked" ->
                _state.update { it.copy(question = OpenCodeApi.parseQuestions(props)) }

            type == "question.replied" || type == "question.rejected" ->
                _state.update { it.copy(question = null) }
        }
    }

    private fun setBusy(busy: Boolean) {
        _state.update { it.copy(busy = busy) }
        appContext?.let { OpenCodeService.publishStatus(it, if (busy) "OpenCode is working…" else "OpenCode is ready.") }
        if (busy) publish()
    }

    private fun onMessageUpdated(props: JSONObject) {
        val info = props.optJSONObject("info") ?: return
        val id = info.optString("id")
        if (id.isEmpty()) return
        val role = info.optString("role").ifEmpty { "assistant" }
        if (role == "user" && messages.keys.any { it.startsWith(LOCAL_PREFIX) }) {
            // The server echoed our prompt, so the optimistic bubble is now redundant.
            messages.keys.filter { it.startsWith(LOCAL_PREFIX) }.forEach { messages.remove(it) }
        }
        val existing = messages[id]
        messages[id] = (existing ?: ChatMessage(id, role)).copy(
            role = role,
            modelID = OpenCodeApi.modelOf(info).ifEmpty { existing?.modelID.orEmpty() },
        )
        publish()
    }

    private fun onPartUpdated(props: JSONObject) {
        val part = props.optJSONObject("part") ?: return
        val parsed = OpenCodeApi.parsePart(part) ?: return
        val messageId = part.optString("messageID")
        if (messageId.isEmpty() || parsed.id.isEmpty()) return
        val message = messages[messageId] ?: ChatMessage(messageId, "assistant")
        val parts = message.parts.toMutableList()
        val index = parts.indexOfFirst { it.id == parsed.id }
        if (index >= 0) {
            // A `part.updated` snapshot can arrive with less text than the deltas already delivered
            // (out-of-order frames); keep whichever side is further along instead of losing words.
            val merged = parts[index]
            parts[index] = if (parsed.text.length >= merged.text.length) parsed else merged
        } else {
            parts.add(parsed)
        }
        messages[messageId] = message.copy(parts = parts)
        publish()
    }

    private fun onPartDelta(props: JSONObject) {
        val partId = props.optString("partID")
        if (props.optString("field") != "text") return
        val delta = props.optString("delta")
        if (partId.isEmpty() || delta.isEmpty()) return
        val entry = messages.entries.firstOrNull { (_, m) -> m.parts.any { it.id == partId } }
            ?: return returnOrphanDelta(partId, delta)
        val parts = entry.value.parts.toMutableList()
        val index = parts.indexOfFirst { it.id == partId }
        parts[index] = parts[index].copy(text = parts[index].text + delta)
        messages[entry.key] = entry.value.copy(parts = parts)
        publish()
    }

    /**
     * Deltas can beat the `part.updated` that introduces the part. Rather than dropping those
     * tokens — which is what left replies empty or missing their opening lines — park them on the
     * newest assistant message so the later snapshot can adopt them.
     */
    private fun returnOrphanDelta(partId: String, delta: String) {
        val entry = messages.entries.lastOrNull { (_, m) -> !m.isUser } ?: return publish()
        val parts = entry.value.parts.toMutableList()
        val index = parts.indexOfFirst { it.id == partId }
        if (index >= 0) {
            parts[index] = parts[index].copy(text = parts[index].text + delta)
        } else {
            parts.add(ChatPart(id = partId, type = "text", text = delta))
        }
        messages[entry.key] = entry.value.copy(parts = parts)
        publish()
    }

    /** Publishes an immutable snapshot and refreshes the status line shown while working. */
    private fun publish() {
        val snapshot = messages.values.toList()
        val runningTool = snapshot.asReversed()
            .asSequence()
            .flatMap { it.parts.asReversed().asSequence() }
            .firstOrNull { it.isRunningTool }
        val status = when {
            runningTool != null -> "Running ${runningTool.tool}…"
            snapshot.any { !it.isUser && it.parts.any { p -> p.text.isNotEmpty() } } -> "Responding…"
            else -> "Thinking…"
        }
        val lastModel = snapshot.lastOrNull { !it.isUser && it.modelID.isNotEmpty() }?.modelID
        _state.update {
            it.copy(
                messages = snapshot,
                statusLine = status,
                modelLabel = lastModel ?: it.modelLabel,
            )
        }
    }

    // -------------------------------------------------------------------------------------------
    // Regenerate and edit
    // -------------------------------------------------------------------------------------------

    /**
     * Answers the same question again, from a different run of the model.
     *
     * Deletes the whole turn — the user's message and everything the agent produced from it — and
     * re-sends the text. Deleting the user's message too is deliberate: the alternative is deleting
     * only the reply and posting a fresh prompt, which leaves two copies of the same question in the
     * transcript. One turn in, one turn out.
     *
     * Files the agent wrote are left alone. This is a chat-level operation, not a rollback: asking
     * for a different answer is not asking to lose the work.
     */
    fun regenerate(userMessageId: String) {
        val text = messages[userMessageId]?.text?.takeIf { it.isNotBlank() } ?: return
        replaceTurn(userMessageId, text)
    }

    /** Replaces a prompt and re-asks it. Everything after that message goes with it. */
    fun editAndResend(userMessageId: String, newText: String) {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return
        replaceTurn(userMessageId, cleaned)
    }

    /**
     * Drops [userMessageId] and every message after it, then sends [text] as a new turn.
     *
     * Both features are this. Doing it as delete-then-send rather than as an edit is what the server
     * allows: `PATCH` only reaches a *part*'s metadata, not the text of a message, and `revert`
     * would drag the working tree back with it.
     */
    private fun replaceTurn(userMessageId: String, text: String) {
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.busy) return

        // Deleting from the *user* message backwards would be wrong: the messages after it are the
        // ones the new turn has to replace.
        val doomed = messages.values
            .filter { it.id != LOCAL_PREFIX && !it.id.startsWith(LOCAL_PREFIX) }
            .dropWhile { it.id != userMessageId }

        if (doomed.isEmpty()) return

        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            publish()

            withContext(Dispatchers.IO) {
                for (message in doomed) {
                    OpenCodeApi.deleteMessage(sessionId, message.id)
                }
            }

            // Drop them locally first, so the transcript updates immediately rather than waiting on
            // a history reload. The server also pushes message.removed, which is idempotent here.
            doomed.forEach { messages.remove(it.id) }
            publish()

            // Cleared in one update: send() refuses to start while busy is set, and a second
            // update here would leave a gap where the turn looks idle but has not been re-sent.
            _state.update { it.copy(input = text, attachments = emptyList(), busy = false) }
            send()
        }
    }

    // -------------------------------------------------------------------------------------------
    // Conversation actions
    // -------------------------------------------------------------------------------------------

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    /** The last chunk dictated, so the next partial can replace it instead of stacking a copy. */
    @Volatile
    private var dictatedTail: String = ""

    /**
     * Appends dictated text to whatever is already typed.
     *
     * `onPartialResults` fires repeatedly with the running transcript, so each update replaces the
     * previously dictated tail rather than appending to it — otherwise every tick would stack
     * another copy of the same words. Anything the user typed by hand is left untouched.
     */
    fun appendToInput(text: String) = _state.update { current ->
        val typed = if (dictatedTail.isNotEmpty() && current.input.endsWith(dictatedTail)) {
            current.input.dropLast(dictatedTail.length)
        } else {
            current.input
        }
        dictatedTail = text
        current.copy(input = (typed + text).trimStart())
    }

    /** Called when the composer is edited by hand, so a later dictation does not eat those words. */
    fun onManualInputChange(value: String) {
        dictatedTail = ""
        _state.update { it.copy(input = value) }
    }

    /**
     * Picks up a prompt that was in flight when the process died.
     *
     * Called from the service rather than the UI, because the service is what Android re-creates.
     * Conservative by design: the reply is *not* re-sent, because the model may still be generating
     * the original and asking twice would double the work and confuse the transcript. Instead the
     * session history is reloaded and the user is notified — if the original reply never landed,
     * they can send it again themselves and see why.
     */
    fun resumePending(app: Context) {
        val job = PendingJobStore.take(app) ?: return
        Log.i(TAG, "Resuming after a restart; pending prompt: ${job.text.take(60)}")
        scope.launch {
            val healthy = withContext(Dispatchers.IO) {
                OpenCodeServer.ensureStarted(app)
                var attempt = 0
                while (attempt < 60) {
                    if (OpenCodeApi.health()) return@withContext true
                    delay(1_000)
                    attempt++
                }
                OpenCodeApi.health()
            }
            if (!healthy) {
                // Put it back so the next start can try again rather than losing it outright.
                PendingJobStore.save(app, job)
                Log.w(TAG, "Server did not come back; keeping the pending job for next time")
                return@launch
            }
            job.sessionId?.let { id -> selectSession(id) }
            _state.update { it.copy(busy = false) }
            AgentNotifications.notifyFinished(app, job.sessionId)
        }
    }

    /** Applies a permission decision that arrived from a notification rather than the app. */
    fun replyPermissionFromNotification(response: String) = replyPermission(response)

    /**
     * A question cannot be answered from a notification button — there is no way to know which
     * option was meant — so it is skipped rather than guessed. Guessing would be far worse than
     * skipping: it would silently pick work the user never read.
     */
    fun answerPendingQuestion() {
        Log.i(TAG, "Question deferred to the app; skipping rather than guessing an answer")
        rejectQuestion()
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() && current.attachments.isEmpty()) return
        if (current.busy || sendJob != null) return

        val attachments = current.attachments
        val selected = current.selectedModel
        // Captured here, not at the point of use: the note describes what was on disk when the
        // user pressed send, which is the only moment it can be honestly described.
        val credentialNote = appContext?.let { CredentialStore.agentNote(it) }
        if (attachments.any { it.isImage } && selected?.attachment == false) {
            _state.update {
                it.copy(notice = "${selected.modelName} is text-only — the picture may be ignored or rejected.")
            }
        }

        // Every attachment is written to the container's disk, images included.
        //
        // Images used to go inline only, as base64 in the request body. That let the model see them
        // for that one turn and nothing more: the agent had no path to quote, so it could not `cp`
        // the picture, embed it in a page, or even mention that it existed — "use the picture I
        // sent" failed because there was no picture, only a blob in a socket. Writing them to
        // `/root/.opencode-attachments/` makes them ordinary files the agent can use like any other.
        val images = attachments.filter { it.isImage }
        val fileRefs = attachments.mapNotNull { attachment ->
            Attachments.persistToContainer(attachment) ?: run {
                Log.w(TAG, "Could not write ${attachment.name} to the container")
                null
            }
        }
        if (fileRefs.size < attachments.size) {
            _state.update { it.copy(notice = "Some files could not be saved and were left out.") }
        }

        _state.update { it.copy(input = "", error = null, notice = null, attachments = emptyList()) }
        val localId = LOCAL_PREFIX + System.currentTimeMillis()
        messages[localId] = ChatMessage(
            id = localId,
            role = "user",
            parts = listOf(ChatPart("$localId-part", "text", text = text)),
        )
        setBusy(true)
        publish()

        // Written before the request leaves, so a killed process can pick the work back up rather
        // than leaving the container generating into a conversation nobody is watching.
        appContext?.let { app ->
            PendingJobStore.save(
                app,
                PendingJobStore.PendingJob(
                    sessionId = current.sessionId,
                    text = text,
                    imagePaths = emptyList(),
                    fileRefs = fileRefs,
                    modelKey = selected?.key,
                    agent = current.selectedAgent.takeIf { it.isNotEmpty() },
                    startedAt = System.currentTimeMillis(),
                ),
            )
        }

        sendJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sessionId = _state.value.sessionId
                        ?: OpenCodeApi.createSession("chat").also { id ->
                            _state.update { s -> s.copy(sessionId = id) }
                        }

                    // Quota and context-limit failures are recoverable: a bigger model usually has a
                    // bigger window and its own budget. Retry once on a stronger model rather than
                    // showing the user a raw provider error they cannot act on.
                    var attempt = _state.value.selectedModel
                    // Every model already tried this turn, so the fallback rotates instead of
                    // bouncing back to the same dead end. Free tiers rate-limit constantly, and one
                    // 429 says nothing about the next model — retrying a handful of them is what
                    // makes "auto switch" actually reliable rather than a coin flip.
                    val tried = LinkedHashSet<String>()
                    attempt?.key?.let { tried.add(it) }

                    while (true) {
                        try {
                            OpenCodeApi.send(
                                sessionId = sessionId,
                                text = text,
                                images = images,
                                fileRefs = fileRefs,
                                model = attempt,
                                agent = _state.value.selectedAgent.takeIf { it.isNotEmpty() },
                                credentialNote = credentialNote,
                            )
                            break
                        } catch (failure: Throwable) {
                            val reason = failure.message
                            // Only a capacity failure is worth retrying elsewhere; anything else is a
                            // real error the user needs to see.
                            if (!isLimitFailure(reason)) throw failure

                            val failed = attempt ?: _state.value.selectedModel
                            val fallback = pickStrongerModel(_state.value.fallbackPool, failed, tried)
                                ?: throw failure
                            tried.add(fallback.key)

                            // Short and transient on purpose: the floating status pill carries it for
                            // as long as the retry is running, then the reply itself replaces it.
                            // The "free" tag matters here — it is the whole point of the fallback.
                            val tag = if (fallback.isFree) " (free)" else " (paid)"
                            val reason2 = if (tried.size == 1) {
                                "Limit hit on ${failed?.modelName ?: "model"} → switched to ${fallback.modelName}$tag"
                            } else {
                                "Limit hit on ${failed?.modelName ?: "model"} → trying ${fallback.modelName}$tag (${tried.size} tried)"
                            }
                            _state.update {
                                it.copy(
                                    selectedModel = fallback,
                                    modelLabel = fallback.modelName,
                                    variants = fallback.variants,
                                    selectedVariant = "",
                                    statusLine = reason2,
                                    notice = reason2,
                                )
                            }
                            attempt = fallback
                        }
                    }

                    val history = runCatching { OpenCodeApi.messages(sessionId) }.getOrNull()
                    val sessions = runCatching { OpenCodeApi.listSessions() }.getOrDefault(emptyList())
                    history to sessions
                }
            }
            sendJob = null
            result.fold(
                onSuccess = { (history, sessions) ->
                    appContext?.let { PendingJobStore.clear(it) }
                    if (history != null) replaceMessages(history) else publish()
                    _state.update { it.copy(busy = false, sessions = sessions) }
                    refreshPending()
                    // The reply is done and the user is probably not looking at the screen.
                    appContext?.let { AgentNotifications.notifyFinished(it, _state.value.sessionId) }
                },
                onFailure = { error ->
                    val raw = error.message.orEmpty()

                    // A timeout here does NOT mean the request failed. The blocking POST is only a
                    // convenience; the reply itself is carried by the SSE stream, which is still
                    // running. Reporting a timeout here was the single most common false error in the
                    // app — the banner appeared over a reply that was happily generating.
                    if (isTimeout(error)) {
                        Log.w(TAG, "POST timed out; the reply is still streaming over SSE")
                        // Wait for the stream to go idle, then take the transcript from the server
                        // rather than inventing a failure.
                        awaitIdleThenReload()
                        return@fold
                    }

                    messages.remove(localId)
                    publish()
                    val short = if (isLimitFailure(raw)) {
                        "Hit a model limit and no stronger model is available."
                    } else {
                        raw.take(300).ifEmpty { "Request failed" }
                    }
                    if (isLimitFailure(raw)) Log.w(TAG, "Limit failure: $raw")
                    appContext?.let { PendingJobStore.clear(it) }
                    _state.update { it.copy(busy = false, error = short) }
                },
            )
        }
    }

    /**
     * Interrupts the running reply.
     *
     * The server's `/abort` endpoint is used rather than cancelling the coroutine: cancelling would
     * only abandon the socket, leaving opencode generating into the void.
     */
    fun stop() {
        val sessionId = _state.value.sessionId ?: return
        _state.update { it.copy(busy = false) }
        scope.launch(Dispatchers.IO) { runCatching { OpenCodeApi.abort(sessionId) } }
        publish()
    }

    private fun replaceMessages(history: List<ChatMessage>) {
        messages.clear()
        history.forEach { messages[it.id] = it }
        publish()
    }

    // -------------------------------------------------------------------------------------------
    // Sessions, models, agents, attachments
    // -------------------------------------------------------------------------------------------

    private fun refreshSessions(pickRecent: Boolean) {
        scope.launch {
            val listed = withContext(Dispatchers.IO) {
                runCatching { OpenCodeApi.listSessions() }.getOrDefault(emptyList())
            }
            // A local rename has to be re-applied on top of every list, or the drawer would show
            // the new name for an instant and then snap back to the server's on the next refresh —
            // which happens right after a rename, because the rename itself triggers one.
            val sessions = listed.map { it.copy(title = titleOverride(it.id, it.title)) }
            _state.update { it.copy(sessions = sessions) }
            if (pickRecent && sessions.isNotEmpty()) selectSession(sessions.first().id)
        }
    }

    /** The device-local title for a session, or the server's own when none was set. */
    private fun titleOverride(sessionId: String, serverTitle: String): String =
        titleOverrides[sessionId] ?: serverTitle

    fun selectSession(sessionId: String) {
        if (_state.value.busy) return
        _state.update { it.copy(sessionId = sessionId, error = null) }
        scope.launch {
            val settings = withContext(Dispatchers.IO) {
                val history = runCatching { OpenCodeApi.messages(sessionId) }.getOrNull()
                val (agent, model) = runCatching { OpenCodeApi.session(sessionId) }
                    .getOrDefault(null to null)
                history to (agent to model)
            }
            val (agent, rawModel) = settings.second
            // `session()` only carries ids; the catalogue is matched so capabilities stay accurate.
            val matched = rawModel?.let { wanted ->
                _state.value.models.find {
                    it.providerID == wanted.providerID && it.modelID == wanted.modelID
                }
            }
            _state.update {
                it.copy(
                    selectedAgent = agent ?: it.selectedAgent,
                    selectedModel = matched ?: it.selectedModel,
                    modelLabel = matched?.modelName ?: it.modelLabel,
                )
            }
            settings.first?.let { replaceMessages(it) }
        }
    }

    /**
     * Starts a fresh conversation.
     *
     * This used to bail out silently while a reply was running, which is why "new chat" appeared to
     * do nothing whenever the agent happened to be busy. Tapping it now always starts a new chat:
     * the in-flight reply is aborted server-side first, otherwise opencode keeps generating into a
     * session the user has already left behind.
     */
    fun newChat() {
        val wasBusy = _state.value.busy
        val sessionId = _state.value.sessionId
        if (wasBusy && sessionId != null) {
            scope.launch(Dispatchers.IO) { runCatching { OpenCodeApi.abort(sessionId) } }
        }
        // The abort is fire-and-forget; clearing local state must not wait on it.
        sendJob = null
        _state.update {
            it.copy(
                sessionId = null,
                busy = false,
                statusLine = "",
                permission = null,
                question = null,
            )
        }
        replaceMessages(emptyList())
        refreshSessions(pickRecent = false)
    }

    fun deleteSession(sessionId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { OpenCodeApi.deleteSession(sessionId) }
            if (_state.value.sessionId == sessionId) newChat()
            refreshSessions(pickRecent = false)
        }
    }

    /**
     * Renames a conversation, server first.
     *
     * A local title is written regardless, so the drawer never flickers back to the old name
     * between the optimistic update and the server's own answer. What the server call changes is
     * whether that title is durable — and when it is not, the user is told, because "renamed" and
     * "renamed on this phone only" are different promises and only the second survives a reinstall.
     *
     * A blank title is refused here, not at the call site. The dialog's Save button is disabled for
     * one, so this guard is about the model rather than the screen: moving the rename out of the
     * ViewModel had briefly left nothing rejecting an empty name, and this is the layer that owns
     * the contract now.
     */
    fun renameSession(sessionId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        applyLocalTitle(sessionId, trimmed)
        scope.launch {
            val synced = withContext(Dispatchers.IO) { OpenCodeApi.renameSession(sessionId, trimmed) }
            if (!synced) {
                _state.update {
                    it.copy(notice = "Renamed on this device only — opencode did not accept the change.")
                }
            }
            // Re-pull either way: on success this picks up the server's canonical title, and on
            // failure it confirms the local override is still the one being shown.
            refreshSessions(pickRecent = false)
        }
    }

    fun selectModel(model: ModelInfo) = _state.update { current ->
        current.copy(
            selectedModel = model,
            modelLabel = model.modelName,
            // Picking a specific model by hand is the opposite of asking for automatic selection.
            autoFree = false,
            notice = null,
            // A variant only applies to the model it came from; switching models must not leave a
            // stale "high" effort behind on a model that has no such option.
            variants = model.variants,
            selectedVariant = if (model.variants.contains(current.selectedVariant)) {
                current.selectedVariant
            } else {
                ""
            },
        )
    }

    /**
     * Turns on automatic free-model selection.
     *
     * Picks the strongest free tool-calling model among the connected providers straight away, so
     * the composer shows a real model name rather than a placeholder the app might not be able to
     * honour. The send path then behaves the same as it always did — if that model refuses, the
     * existing rotation moves on — which is what makes this safe to leave switched on: there is
     * nothing to turn off and nothing to come back to when every free model is rate-limited.
     */
    fun enableAutoFree() {
        _state.update { it.copy(autoFree = true, notice = null) }
        val best = pickAutoFreeModel(_state.value.fallbackPool)
        if (best == null) {
            _state.update {
                it.copy(
                    autoFree = true,
                    notice = "No free model available right now. Connect a provider or pick one manually.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                selectedModel = best,
                modelLabel = best.modelName,
                variants = best.variants,
                selectedVariant = "",
                notice = "Using ${best.modelName} — the best free model available right now.",
            )
        }
        Log.i(TAG, "auto-free selected ${best.key}")
    }

    fun selectAgent(agent: String) = _state.update { it.copy(selectedAgent = agent) }

    /**
     * Overrides a conversation's title locally. Passing null drops the override so the server's own
     * title shows again.
     */
    fun applyLocalTitle(sessionId: String, title: String?) {
        // Recorded in memory *and* on disk, so every later [refreshSessions] re-applies it and it
        // survives a restart. Writing only to the map was the bug this fixes.
        if (title == null) {
            titleOverrides.remove(sessionId)
            appContext?.getSharedPreferences(TITLE_PREFS, Context.MODE_PRIVATE)
                ?.edit()?.remove(sessionId)?.apply()
        } else {
            titleOverrides[sessionId] = title
            appContext?.getSharedPreferences(TITLE_PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putString(sessionId, title)?.apply()
        }
        _state.update { current ->
            current.copy(
                sessions = current.sessions.map { session ->
                    if (session.id != sessionId) session
                    else session.copy(title = title ?: session.serverTitle.ifEmpty { session.title })
                },
            )
        }
    }

    fun selectVariant(variant: String) =
        _state.update { it.copy(selectedVariant = variant) }

    fun addAttachments(list: List<Attachment>) {
        if (list.isEmpty()) return
        _state.update { it.copy(attachments = it.attachments + list) }
    }

    fun removeAttachment(id: String) =
        _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }

    // -------------------------------------------------------------------------------------------
    // Permissions and questions
    // -------------------------------------------------------------------------------------------

    /**
     * Catches requests raised before this screen attached or while the event stream was
     * reconnecting — without it a stale approval request would hang the reply forever.
     */
    fun refreshPending() {
        scope.launch(Dispatchers.IO) {
            val permissions = runCatching { OpenCodeApi.pendingPermissions() }.getOrDefault(emptyList())
            val questions = runCatching { OpenCodeApi.pendingQuestions() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    permission = it.permission ?: permissions.firstOrNull(),
                    question = it.question ?: questions.firstOrNull(),
                )
            }
            // Anything newly blocked gets a notification: the agent is holding the reply open
            // until it is answered, so a prompt only seen by opening the app is a prompt that will
            // silently never get answered.
            val app = appContext
            if (app != null) {
                val state = _state.value
                state.permission?.let { AgentNotifications.notifyPermission(app, it) }
                state.question?.let { AgentNotifications.notifyQuestion(app, it) }
            }
        }
    }

    fun replyPermission(response: String) {
        val prompt = _state.value.permission ?: return
        _state.update { it.copy(permission = null) }
        scope.launch(Dispatchers.IO) {
            runCatching { OpenCodeApi.replyPermission(prompt, response) }
                .onFailure { e ->
                    _state.update { it.copy(error = "Could not answer the permission: ${e.message?.take(200)}") }
                }
        }
    }

    fun answerQuestion(answers: List<List<String>>) {
        val prompt = _state.value.question ?: return
        _state.update { it.copy(question = null) }
        scope.launch(Dispatchers.IO) {
            runCatching { OpenCodeApi.replyQuestion(prompt.id, answers) }
                .onFailure { e -> _state.update { it.copy(error = e.message?.take(300)) } }
        }
    }

    fun rejectQuestion() {
        val prompt = _state.value.question ?: return
        _state.update { it.copy(question = null) }
        scope.launch(Dispatchers.IO) { runCatching { OpenCodeApi.rejectQuestion(prompt.id) } }
    }
}
