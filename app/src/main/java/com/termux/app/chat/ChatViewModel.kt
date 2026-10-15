package com.termux.app.chat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatUiState(
    val server: ServerState = ServerState.Starting,
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<SessionInfo> = emptyList(),
    val busy: Boolean = false,
    /** Short description of what the agent is doing right now, shown above the composer. */
    val statusLine: String = "",
    val input: String = "",
    val error: String? = null,
    val modelLabel: String = "",
    val models: List<ModelInfo> = emptyList(),
    /**
     * Every model opencode advertises, connected or not. The picker shows [models]; this is the
     * pool a quota fallback draws from, since a one-provider setup leaves [models] with one entry.
     */
    val fallbackPool: List<ModelInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    /** Provider ids opencode reports as connected, shown in the picker so the user can see what is set up. */
    val providers: List<String> = emptyList(),
    /** Reasoning-effort variants the catalogue reports for [selectedModel]; empty when it has none. */
    val variants: List<String> = emptyList(),
    /** Empty string means "the model default". */
    val selectedVariant: String = "",
    /** Null means "keep whatever the session or server default is". */
    val selectedModel: ModelInfo? = null,
    /**
     * True when the user has asked the app to choose the free model rather than pick one.
     *
     * With over a thousand free models in the catalogue, making the user scroll a dropdown to find
     * one is the wrong default — almost nobody wants a specific model here, they want "the best one
     * that is free and currently answering". In this mode the runtime picks the strongest free
     * tool-calling model from the connected providers, and re-picks whenever the current one fails.
     */
    val autoFree: Boolean = false,
    val selectedAgent: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** Set while a tool call is waiting for approval; the reply stays paused until it is answered. */
    val permission: PermissionPrompt? = null,
    /** Set while the agent is asking a multiple-choice question. */
    val question: QuestionPrompt? = null,
    /** Non-fatal warning (e.g. "this model cannot read images") shown above the composer. */
    val notice: String? = null,
)

/** Id prefix for the placeholder bubble shown while a prompt is still in flight. */
const val LOCAL_PREFIX = "local-"

/**
 * Thin adapter between the chat screen and [OpenCodeRuntime].
 *
 * The runtime keeps the state and the coroutines for the lifetime of the *process*, so leaving the
 * screen does not cancel a reply; the ViewModel only hands the screen a stable [StateFlow] and
 * forwards intents. `viewModelScope` is intentionally never used here.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    /** Held directly: `AndroidViewModel.application` is private. */
    private val app: Application = application

    val state: StateFlow<ChatUiState> = OpenCodeRuntime.state

    init {
        OpenCodeRuntime.start(app)
    }

    /**
     * Renames a conversation.
     *
     * Straight delegation: the runtime owns the title, asks opencode first and only falls back to a
     * device-local copy when the server refuses, saying so when it does. This ViewModel used to keep
     * a second copy of that copy in a SharedPreferences wrapper, and those values were never handed
     * back — so a locally-stored title vanished from the drawer on the next launch. One owner,
     * one store.
     */
    fun renameSession(sessionId: String, title: String) = OpenCodeRuntime.renameSession(sessionId, title)

    /** Forgets a local override, falling back to the server's own title. */
    fun clearRenamedSession(sessionId: String) = OpenCodeRuntime.applyLocalTitle(sessionId, null)

    fun onInputChange(value: String) = OpenCodeRuntime.onInputChange(value)
    fun dismissError() = OpenCodeRuntime.dismissError()
    fun dismissNotice() = OpenCodeRuntime.dismissNotice()
    fun send() = OpenCodeRuntime.send()
    fun stop() = OpenCodeRuntime.stop()
    fun newChat() = OpenCodeRuntime.newChat()
    fun selectSession(id: String) = OpenCodeRuntime.selectSession(id)
    fun deleteSession(id: String) = OpenCodeRuntime.deleteSession(id)
    fun selectModel(model: ModelInfo) = OpenCodeRuntime.selectModel(model)

    /** Lets the app choose the best free model instead of the user picking one. */
    fun enableAutoFree() = OpenCodeRuntime.enableAutoFree()
    fun selectAgent(agent: String) = OpenCodeRuntime.selectAgent(agent)
    fun selectVariant(variant: String) = OpenCodeRuntime.selectVariant(variant)
    fun appendToInput(text: String) = OpenCodeRuntime.appendToInput(text)
    fun onManualInputChange(value: String) = OpenCodeRuntime.onManualInputChange(value)
    fun addAttachments(items: List<Attachment>) = OpenCodeRuntime.addAttachments(items)
    fun removeAttachment(id: String) = OpenCodeRuntime.removeAttachment(id)
    fun replyPermission(response: String) = OpenCodeRuntime.replyPermission(response)
    fun answerQuestion(answers: List<List<String>>) = OpenCodeRuntime.answerQuestion(answers)
    fun rejectQuestion() = OpenCodeRuntime.rejectQuestion()
    fun refreshPending() = OpenCodeRuntime.refreshPending()

    /** Re-reads models/agents/providers, so a provider connected in the terminal shows up on return. */
    fun refreshCatalogue() = OpenCodeRuntime.refreshCatalogue()

    /** Retries the backend after a failed start; [OpenCodeRuntime.start] is idempotent until it fails. */
    fun connect() = OpenCodeRuntime.start(app)

    /** The runtime outlives the ViewModel, so there is nothing to tear down here. */
    override fun onCleared() = Unit
}
