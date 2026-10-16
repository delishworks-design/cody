package com.termux.app.ui.screens

import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.app.chat.AgentInfo
import com.termux.app.chat.Attachment
import com.termux.app.chat.Attachments
import com.termux.app.chat.ChatMessage
import com.termux.app.chat.ChatPart
import com.termux.app.chat.ChatUiState
import com.termux.app.chat.ChatViewModel
import com.termux.app.chat.Dictation
import com.termux.app.chat.PcmRecorder
import com.termux.app.chat.Whisper
import com.termux.app.chat.ModelInfo
import com.termux.app.chat.PermissionPrompt
import com.termux.app.chat.QuestionPrompt
import com.termux.app.chat.ServerState
import com.termux.app.chat.WorkspaceFile
import com.termux.app.chat.WorkspaceFiles
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.AccentSoft
import com.termux.app.ui.theme.Bg
import com.termux.app.ui.theme.BgElevated
import com.termux.app.ui.theme.Border
import com.termux.app.ui.theme.Danger
import com.termux.app.ui.theme.Success
import com.termux.app.ui.theme.Surface as SurfaceColor
import com.termux.app.ui.theme.SurfaceHigh
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary
import com.termux.app.ui.theme.UserText
import com.termux.app.ui.theme.Warning
import com.termux.app.web.WebPreviewServer
import com.termux.app.web.WebViewActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Claude Code-style chat surface.
 *
 * Instead of a terminal it presents a conversation: user turns on the right, agent replies on the
 * left, with reasoning blocks, live tool-call cards and a composer at the bottom. The raw shell is
 * still one tap away through [onOpenTerminal].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onOpenSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTerminal: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFiles by remember { mutableStateOf(false) }
    var showChanges by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dictation. Whisper in the container is the real transcriber; the platform recogniser is only a
    // fallback for devices where the optional install step never ran. Either way the recogniser owns
    // the microphone, so it is stopped on the way out of the screen.
    var listening by remember { mutableStateOf(false) }
    var dictationError by remember { mutableStateOf<String?>(null) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) dictationError = "Microphone permission is required."
    }

    val whisperReady = remember { Whisper.isInstalled() }
    val micPermissionGranted = remember { Dictation.hasPermission(context) }
    val recorder = remember {
        PcmRecorder(File(context.cacheDir, "dictation-${System.currentTimeMillis()}.wav"))
    }

    fun finishDictation(wav: File) {
        listening = false
        scope.launch {
            val transcript = withContext(Dispatchers.IO) { Whisper.transcribe(wav) }
            if (transcript != null) {
                viewModel.appendToInput(transcript)
            } else {
                dictationError = "Whisper could not transcribe that. Try again."
            }
            runCatching { wav.delete() }
        }
    }

    fun toggleDictation() {
        if (listening) {
            recorder.stop()
            if (!recorder.hasAudio) {
                listening = false
                dictationError = "Nothing was recorded."
                return
            }
            finishDictation(recorder.output)
            return
        }

        dictationError = null
        if (!micPermissionGranted) {
            micPermission.launch(Dictation.PERMISSION)
            return
        }
        if (whisperReady) {
            if (recorder.start()) {
                listening = true
            } else {
                dictationError = "Could not open the microphone."
            }
            return
        }

        // Whisper was never installed: fall back to the platform recogniser rather than failing.
        if (!Dictation.isAvailable(context)) {
            dictationError = "No speech recogniser. Run setup to install Whisper."
            return
        }
        listening = true
        Dictation.start(
            context = context,
            onPartial = { partial -> viewModel.appendToInput(partial) },
            onFinal = { final ->
                if (final.isNotBlank()) viewModel.appendToInput(final)
                listening = false
            },
            onError = { message -> dictationError = message },
            onReady = {},
            onEnd = { listening = false },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            Dictation.stop()
            recorder.stop()
        }
    }

    // Follow the tail of the conversation, but never fight the user if they scrolled up.
    //
    // Two things used to hide the model's reply here:
    //  1. `animateScrollToItem(last)` lines the *top* of the last message up with the viewport, so
    //     as a long answer streams in, the newest tokens land below the fold.
    //  2. The "is the user near the tail?" test ran before the new item had been measured, so a
    //     freshly appended reply was judged to be "off screen" and never scrolled to at all.
    // Both are fixed by scrolling to the *end* of the last item once the layout knows about it, and
    // by reading layoutInfo inside snapshotFlow so the measurement is part of the trigger.
    var pinnedToTail by remember { mutableStateOf(true) }

    // `scrollToItem` is suspending, so this helper has to be too; the jump-to-latest button calls it
    // from its own coroutine scope for the same reason.
    suspend fun scrollToNewest() {
        val last = state.messages.lastIndex
        if (last >= 0) listState.scrollToItem(last, Int.MAX_VALUE / 2)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow true
            // One item of slack so a newly appended message does not immediately unpin us.
            lastVisible >= info.totalItemsCount - 2
        }.collect { pinnedToTail = it }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val tail = state.messages.lastOrNull()?.let { m -> m.parts.sumOf { it.text.length } } ?: 0
            Triple(total, state.messages.size, tail)
        }.collect { if (pinnedToTail) scrollToNewest() }
    }

    // The pickers hand back a content:// Uri that is only resolvable right now, so the bytes are
    // pulled in immediately instead of when the prompt is eventually sent.
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.addAttachments(listOfNotNull(Attachments.fromUri(context, uri)))
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addAttachments(listOfNotNull(Attachments.fromUri(context, uri)))
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // The transcript sits inside a drawer: history is what people reach for constantly on a phone,
    // and burying it in an overflow menu at the top-right corner makes it invisible.
    ChatDrawer(
        sessions = state.sessions,
        currentId = state.sessionId,
        onNewChat = viewModel::newChat,
        onSelect = viewModel::selectSession,
        onDelete = viewModel::deleteSession,
        onRename = viewModel::renameSession,
        onTogglePin = viewModel::togglePin,
        onOpenFiles = { showFiles = true },
        onOpenSettings = onOpenSettings,
        onOpenSetup = onOpenSetup,
        onOpenTerminal = {
            // Close the drawer first. The terminal is a separate activity, so on return the user
            // would otherwise land on a still-open drawer covering the transcript.
            scope.launch { drawerState.close() }
            onOpenTerminal()
        },
        drawerState = drawerState,
    ) {
        // Indented one level further than the drawer call that follows it: this Column is the
        // drawer's content lambda, not another statement in ChatScreen's body.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            // `enableEdgeToEdge()` lets the window paint under the system bars, so the screens have
            // to inset themselves. Without this the top bar sits under the clock.
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ChatTopBar(
            title = state.sessions.firstOrNull { it.id == state.sessionId }?.title
                ?: state.messages.lastOrNull { it.isUser }?.text?.take(40)
                ?: "New chat",
            onOpenDrawer = { scope.launch { drawerState.open() } },
            changedFiles = state.files.size,
            onOpenFiles = { showChanges = true },
        )
        state.error?.let { message ->
            ErrorBanner(message = message, onDismiss = viewModel::dismissError)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (state.server) {
                ServerState.Starting -> InitializingScreen()

                ServerState.Failed -> ServerNotice(
                    message = "Could not reach the opencode server.",
                    progress = false,
                    actionLabel = "Retry",
                    onAction = { viewModel.connect() },
                )

                ServerState.Ready -> when {
                    state.messages.isEmpty() -> WelcomeState(
                        onSuggestion = { suggestion ->
                            viewModel.onInputChange(suggestion)
                            viewModel.send()
                        },
                    )

                    else ->
                    // The selection container sits on each message, NOT around the LazyColumn.
                    // Wrapping the list puts a long-press selection gesture on the scroll
                    // container itself, which swallows the vertical drag — the transcript stopped
                    // scrolling entirely. Per-message selection gives the same text handles
                    // without ever touching the list's own touch handling.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            top = 14.dp,
                            end = 14.dp,
                            bottom = 80.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            // The status rides on the newest turn only, and only while it has no
                            // prose yet - so it sits exactly where the reply is about to land.
                            val isNewest = message.id == state.messages.lastOrNull { !it.isUser }?.id
                            SelectionContainer {
                                MessageRow(
                                    message = message,
                                    modelLabel = state.modelLabel,
                                    status = if (isNewest) state.statusLine else "",
                                    busy = state.busy,
                                    canRegenerate = !state.busy,
                                    onRegenerate = { viewModel.regenerate(message.id) },
                                    onEdit = { text -> viewModel.editAndResend(message.id, text) },
                                )
                            }
                        }
                    }
                }
            }

            // Only offered once the user has deliberately scrolled back through the history.
            if (!pinnedToTail && state.messages.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceHigh)
                        .border(1.dp, Border, CircleShape)
                        .clickable {
                            pinnedToTail = true
                            scope.launch { scrollToNewest() }
                        },
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Jump to latest",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        if (state.server == ServerState.Ready) {
            dictationError?.let { message ->
                NoticeBar(message = message, onDismiss = { dictationError = null })
            }
            state.notice?.let { notice ->
                NoticeBar(message = notice, onDismiss = viewModel::dismissNotice)
            }
            Column(modifier = Modifier.imePadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                ModeSwitch(
                    selected = state.selectedAgent,
                    agents = state.agents,
                    onPick = viewModel::selectAgent,
                )
                SkillPicker(
                    skills = state.skills,
                    // Inserts `/name` rather than sending: the user should see what they picked and
                    // be able to edit the sentence around it before it goes.
                    onPick = { skill ->
                        val prefix = "/${skill.name} "
                        val existing = state.input
                        viewModel.onManualInputChange(
                            if (existing.startsWith("/")) existing else prefix + existing
                        )
                    },
                )
            }
            Composer(
                value = state.input,
                busy = state.busy,
                modelLabel = state.modelLabel,
                models = state.models,
                selectedModel = state.selectedModel,
                autoFree = state.autoFree,
                onAutoFree = viewModel::enableAutoFree,
                variants = state.variants,
                selectedVariant = state.selectedVariant,
                attachments = state.attachments,
                providers = state.providers,
                onChange = viewModel::onManualInputChange,
                listening = listening,
                onVoiceResult = { toggleDictation() },
                onAction = { if (state.busy) viewModel.stop() else viewModel.send() },
                onPickModel = viewModel::selectModel,
                onPickVariant = viewModel::selectVariant,
                onRemoveAttachment = viewModel::removeAttachment,
                onPickPhoto = { photoLauncher.launch("image/*") },
                onPickFile = { fileLauncher.launch(arrayOf("*/*")) },
                // Provider auth lives in Settings, next to the service keys. The catalogue is
                // refreshed on the way there so a provider connected on that screen shows up in the
                // picker as soon as the user comes back.
                onConfigureProvider = {
                    viewModel.refreshCatalogue()
                    onOpenSettings()
                },
            )
            }
        }
    }

    state.permission?.let { prompt ->
        PermissionDialog(prompt = prompt, onReply = viewModel::replyPermission)
    }
    state.question?.let { prompt ->
        QuestionDialog(
            prompt = prompt,
            onAnswer = viewModel::answerQuestion,
            onReject = viewModel::rejectQuestion,
        )
    }

    if (showFiles) {
        FilesSheet(onDismiss = { showFiles = false })
    }
    if (showChanges) {
        FileTreePanel(files = state.files, onDismiss = { showChanges = false })
    }
    }
}

// =================================================================================================
// Top bar
// =================================================================================================

/**
 * The header: a drawer handle, the conversation's name, and nothing else.
 *
 * There used to be a back arrow and a four-item overflow menu here, both of which duplicated the
 * drawer that replaced them — and the back arrow was a lie, since the chat is now the root of the
 * graph and going "back" from it could only land on the setup screen. So: hamburger left, title
 * middle, new chat right. One tap each, nothing hidden.
 */
@Composable
private fun ChatTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    changedFiles: Int,
    onOpenFiles: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(BgElevated)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onOpenDrawer) {
            // DragHandle rather than Menu: three filled lines is the old hamburger, and the two-line
            // outline is the minimal version this bar now uses everywhere else.
            Icon(Icons.Outlined.DragHandle, contentDescription = "Conversations", tint = TextPrimary)
        }
        // Centred between the two ends of the row, with real padding so a long conversation name
        // ellipsizes instead of running under the hamburger or off the edge. The trailing button is
        // a fixed-width spacer rather than a control — new chat lives in the drawer — so the title's
        // true centre is also the screen's centre.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        // What the agent has touched, so "is it doing anything" is answerable without scrolling
        // the transcript. The badge is the point; the sheet it opens is the detail.
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = onOpenFiles) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = "Changed files",
                    tint = if (changedFiles > 0) AccentSoft else TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (changedFiles > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 4.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .border(1.dp, BgElevated, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (changedFiles > 9) "9+" else changedFiles.toString(),
                        fontSize = 8.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// =================================================================================================
// Messages
// =================================================================================================

@Composable
private fun MessageRow(
    message: ChatMessage,
    modelLabel: String,
    status: String,
    busy: Boolean,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var editing by remember(message.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.isUser) {
            UserMessage(message = message)
        } else {
            AssistantMessage(
                message = message,
                modelLabel = modelLabel,
                status = status,
                busy = busy,
            )
        }
        // Copy the whole turn, plus regenerate/edit once the turn can actually be re-run. Hidden
        // until the message is tapped so the transcript stays quiet.
        var showActions by remember(message.id) { mutableStateOf(false) }
        if (showActions) {
            CopyChip(
                text = message.text,
                canRegenerate = canRegenerate && message.isUser,
                onRegenerate = {
                    showActions = false
                    onRegenerate()
                },
                onEdit = {
                    showActions = false
                    editing = true
                },
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clickable { showActions = !showActions },
        )
    }
}

/**
 * The row of actions under a tapped message: icons only.
 *
 * Three text buttons in a row read as a toolbar stuck under every message. Three glyphs read as
 * controls that mean what they look like, which is the whole point of copy being a copy icon.
 *
 * Regenerate and Edit appear on a user turn only, and only when the turn can actually be re-run:
 * the server replaces the question and everything after it, so offering it on an assistant reply
 * would mean guessing which question it belongs to. It is hidden while a reply is streaming too —
 * deleting the turn underneath a live reply would race it.
 */
@Composable
private fun CopyChip(
    text: String,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember(text) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    ) {
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
            scope.launch {
                delay(1_600)
                copied = false
            }
        }, modifier = Modifier.size(32.dp)) {
            Icon(
                if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy message",
                tint = if (copied) Success else TextMuted,
                modifier = Modifier.size(15.dp),
            )
        }
        if (canRegenerate) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit prompt",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Regenerate",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Rewrites a prompt and re-asks it.
 *
 * Says plainly what is about to be discarded: editing a question from the middle of a conversation
 * deletes that question and everything the agent said after it. Files already written stay on disk —
 * this is not a rollback — and saying so is the difference between a user who meant it and a user
 * who just lost work.
 */
@Composable
private fun EditPromptDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val changed = text.trim().isNotEmpty() && text.trim() != initial.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 22.dp),
        title = {
            Text("Edit prompt", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 10,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This deletes this question and every reply after it, then asks again. Files the " +
                        "agent already wrote stay on disk.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = changed, onClick = { onConfirm(text) }) {
                Text("Send", color = if (changed) AccentSoft else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}

/**
 * The prompt: flush right, no bubble, tinted blue.
 *
 * Colour alone carries the distinction from the agent's reply, which keeps the transcript flat —
 * no containers competing with the tool cards for attention.
 */
@Composable
private fun UserMessage(message: ChatMessage) {
    val files = message.parts.filter { it.type == "file" }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.End,
            // `align` moves the Column itself to the right edge of the Box. Without it the Box
            // defaults to TopStart, the Column hugs its content on the left, and the prompt sits
            // exactly where the agent's reply does. BoxScope.align takes the 2D Alignment, hence
            // TopEnd rather than End.
            modifier = Modifier
                .align(Alignment.TopEnd)
                .widthIn(max = 320.dp),
        ) {
            files.forEach { part ->
                FilePreview(part = part, maxHeight = 220.dp)
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UserText,
                    lineHeight = 21.sp,
                )
                Timestamp(createdAt = message.createdAt)
            }
        }
    }
}

/**
 * The turn's clock time, or nothing at all when it is unknown.
 *
 * Silent rather than showing an epoch or a placeholder: a message still streaming has no persisted
 * timestamp yet, and a wrong time is worse than no time.
 */
@Composable
private fun Timestamp(createdAt: Long) {
    if (createdAt <= 0L) return
    Text(
        text = remember(createdAt) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(createdAt))
        },
        fontSize = 10.sp,
        color = TextMuted.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    modelLabel: String,
    status: String,
    busy: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // The status belongs *inside* the turn being produced, not in a bar above the composer. It
        // renders only while there is nothing to read yet and disappears the moment prose arrives,
        // so it never sits next to a finished answer competing for attention.
        val hasProse = message.parts.any { it.type == "text" && it.text.isNotBlank() }
        if (!hasProse) {
            InlineStatus(status = status, busy = busy)
            Spacer(modifier = Modifier.height(4.dp))
        }
        val previews = remember(message.id) { mutableStateMapOf<String, File>() }
        message.parts.forEach { part ->
            when (part.type) {
                "reasoning" -> ThinkingBlock(part = part)
                "tool" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    ToolCard(part = part)
                    // The model writes HTML and images with the write/edit tools or a shell heredoc, so
                    // they land here as tool calls - not as `file` parts. Without this the agent's own
                    // output would never render, only what the user attached.
                    writtenAsset(part)?.let { file ->
                        if (previews.putIfAbsent(file.absolutePath, file) == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FileChip(file = file)
                        }
                    }
                }

                "file" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilePreview(part = part, maxHeight = 320.dp)
                }

                "text" -> if (part.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MarkdownText(text = part.text)
                }
            }
        }
    }
}

/**
 * Renders a `file` part.
 *
 * Images decode inline from their `data:`/http URL. HTML gets a live, interactive preview in the
 * transcript — served from loopback so its own stylesheets, scripts and images resolve — so an HTML
 * deliverable can be opened and tried without ever leaving the chat. Everything else collapses to a
 * tappable chip.
 */
@Composable
private fun FilePreview(part: ChatPart, maxHeight: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current

    if (part.isHtml) {
        val file = remember(part.url, part.filename) { WorkspaceFiles.resolve(part.url, part.filename) }
        if (file != null) {
            HtmlCard(file = file)
            return
        }
    }

    if (part.isImage) {
        val bitmap = remember(part.url) { decodePreview(part.url) }
        val resolved = remember(part.url, part.filename) { WorkspaceFiles.resolve(part.url, part.filename) }
        if (bitmap != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .clickable {
                        // Tap opens it properly; the inline thumbnail is only a cropped teaser.
                        if (resolved != null) {
                            ImageViewerActivity.start(context, resolved, part.filename)
                        }
                    },
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = part.filename.ifEmpty { "image" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .heightIn(max = maxHeight),
                )
            }
            return
        }
    }

    val resolved = remember(part.url, part.filename) { WorkspaceFiles.resolve(part.url, part.filename) }
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = part.url.isNotEmpty()) {
                if (resolved != null && part.isHtml) {
                    WebViewActivity.start(context, WebPreviewServer.start(resolved) ?: part.url, part.filename)
                } else {
                    WebViewActivity.start(context, part.url)
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Icon(
                if (part.isImage) Icons.Outlined.PhotoLibrary else Icons.Outlined.Folder,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = part.filename.ifEmpty { part.url.substringBefore('?').substringAfterLast('/') },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (resolved != null) {
                IconButton(
                    onClick = {
                        WebViewActivity.start(context, WebPreviewServer.start(resolved) ?: part.url, part.filename)
                    },
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = "Open",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Finds a file the agent just produced that the chat can render itself, so a written `.html` or
 * `.png` shows up as a live preview rather than as a mention in prose.
 *
 * This is the path that actually matters: the model creates files with `write`/`edit` or a shell
 * heredoc, which the server reports as *tool* parts, not `file` parts. `file` parts only ever carry
 * what the user attached, so without this the agent's own output is invisible in the transcript.
 *
 * `bash` is handled separately because there the path is embedded in the command text rather than a
 * JSON field, and the *last* match wins — a heredoc writes its target last.
 */
private fun writtenAsset(part: ChatPart): File? {
    if (part.type != "tool") return null
    val tool = part.tool.lowercase()
    val input = part.state?.input.orEmpty()
    if (input.isBlank()) return null

    if (tool in BASH_TOOLS) {
        val command = try {
            JSONObject(input).optString("command")
        } catch (t: Throwable) {
            input
        }
        return ASSET_IN_TEXT.findAll(command)
            .map { it.value.trim('"', '\'', ' ', ';', '>', '<') }
            .filter { RENDERABLE_NAME.containsMatchIn(it) }
            .lastOrNull()
            ?.let { WorkspaceFiles.resolve(it, it.substringAfterLast('/')) }
    }

    if (tool !in WRITE_TOOLS) return null
    val path = try {
        val obj = JSONObject(input)
        PATH_KEYS.firstOrNull { obj.optString(it).isNotBlank() }
            ?.let { obj.optString(it) }
            ?: return null
    } catch (t: Throwable) {
        return null
    }
    if (!RENDERABLE_NAME.containsMatchIn(path)) return null

    return WorkspaceFiles.resolve(path, path.substringAfterLast('/'))
}

/** Tools that write a file through the shell rather than through a structured argument. */
private val BASH_TOOLS = setOf("bash", "shell", "run", "execute", "sh")

/** Any `.html` or image path appearing inside a shell command, quoted or bare. */
private val ASSET_IN_TEXT =
    Regex("""[\w./~-]*[\w~-]+\.(?:x?html?|png|jpe?g|gif|webp|bmp|svg)\b""", RegexOption.IGNORE_CASE)

/** Extensions the chat can render on its own: HTML pages, still images and video. */
private val RENDERABLE_NAME =
    Regex(
        """\.(?:x?html?|png|jpe?g|gif|webp|bmp|svg|mp4|webm|mov|mkv|mp3|m4a|wav|ogg)$""",
        RegexOption.IGNORE_CASE,
    )

/** Video the transcript can play inline with the platform player. */
private val VIDEO_NAME = Regex("\\.(mp4|webm|mov|mkv)$", RegexOption.IGNORE_CASE)

/** Audio the transcript can play inline. */
private val AUDIO_NAME = Regex("\\.(mp3|m4a|wav|ogg|aac|flac)$", RegexOption.IGNORE_CASE)

/** Tool names whose argument identifies a file the model just authored. */
private val WRITE_TOOLS = setOf("write", "edit", "patch", "create", "multiedit", "apply_patch")

/** The argument names the write tools use for the target path, across opencode's own spellings. */
private val PATH_KEYS = listOf("filePath", "file_path", "path", "file", "target", "filename")

/**
 * Renders whatever the agent just wrote, picked by kind: HTML pages become a live preview, still
 * images a thumbnail, video and audio an inline player, and anything else a plain chip.
 */
@Composable
private fun FileChip(file: File) {
    val name = file.name
    when {
        VIDEO_NAME.containsMatchIn(name) -> VideoCard(file = file)
        AUDIO_NAME.containsMatchIn(name) -> AudioCard(file = file)
        name.endsWith(".html", true) || name.endsWith(".htm", true) -> HtmlCard(file = file)
        IMAGE_NAME.containsMatchIn(name) -> ImageCard(file = file)
        else -> GenericFileChip(file = file)
    }
}

private val IMAGE_NAME =
    Regex("\\.(png|jpe?g|gif|webp|bmp|svg)$", RegexOption.IGNORE_CASE)

/** A still image the agent produced, shown inline and openable full screen. */
@Composable
private fun ImageCard(file: File) {
    val context = LocalContext.current
    val bitmap = remember(file.absolutePath, file.lastModified()) {
        decodePreview(file.absolutePath)
    }
    if (bitmap == null) {
        GenericFileChip(file = file)
        return
    }
    var expanded by remember(file.absolutePath) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable { ImageViewerActivity.start(context, file, file.name) },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = file.name,
            contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .heightIn(max = if (expanded) 560.dp else 240.dp),
        )
    }
}

/**
 * Inline video.
 *
 * Uses the platform `VideoView` rather than Media3/ExoPlayer: it plays the format the agent almost
 * certainly produces (H.264 in MP4) with no new dependency and no download. Anything exotic would
 * need ExoPlayer, which is not worth ~3 MB for a preview.
 *
 * Two deliberate choices:
 *  - **Never autoplay.** A player that starts on its own is startling and costs battery.
 *  - **The surface is only created once tapped.** Constructing a decoder per list cell would open
 *    one for every video in the transcript at once and exhaust the codec pool.
 */
@Composable
private fun VideoCard(file: File) {
    var playing by remember(file.absolutePath) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp)),
    ) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(file.absolutePath)
                        setMediaController(android.widget.MediaController(ctx))
                        setOnPreparedListener {
                            it.isLooping = false
                            it.start()
                        }
                    }
                },
                update = { view ->
                    // Recomposition can land after the view is already finished; do not restart it
                    // in that case, or a dismissed video would replay on any unrelated state change.
                    if (!view.isPlaying && view.currentPosition < view.duration) view.start()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { playing = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    tint = AccentSoft,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = humanSize(file.length()),
                        fontSize = 10.sp,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}

/** Inline audio — same no-autoplay rule as [VideoCard]. */
@Composable
private fun AudioCard(file: File) {
    val context = LocalContext.current
    var playing by remember(file.absolutePath) { mutableStateOf(false) }
    var instance by remember(file.absolutePath) { mutableStateOf<android.media.MediaPlayer?>(null) }

    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (playing) {
                    runCatching { instance?.stop() }
                    instance?.release()
                    instance = null
                } else {
                    val player = android.media.MediaPlayer()
                    runCatching {
                        player.setDataSource(context, android.net.Uri.fromFile(file))
                        player.prepare()
                        player.start()
                        player.setOnCompletionListener { it.release(); instance = null }
                    }.onFailure { player.release() }
                    instance = player
                }
                playing = !playing
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(
                if (playing) Icons.Filled.Stop else Icons.Filled.PlayCircleFilled,
                contentDescription = null,
                tint = AccentSoft,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = humanSize(file.length()), fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun GenericFileChip(file: File) {
    val context = LocalContext.current
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                WebViewActivity.start(context, WebPreviewServer.start(file) ?: file.absolutePath, file.name)
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Icon(Icons.Outlined.Folder, null, tint = TextMuted, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = humanSize(file.length()),
                fontSize = 10.sp,
                color = TextMuted,
            )
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> "${bytes / 1_024} KB"
    else -> "$bytes B"
}

/**
 * An HTML file rendered live inside the transcript, collapsed to a bar until tapped.
 *
 * The page is served by [WebPreviewServer] rather than loaded from `file://`, because a WebView
 * document on `file://` may not fetch its sibling assets and any WebGL asset is blocked outright
 * when it comes from another origin.
 */
@Composable
private fun HtmlCard(file: java.io.File) {
    val context = LocalContext.current
    var expanded by remember(file.absolutePath) { mutableStateOf(false) }
    var url by remember(file.absolutePath) { mutableStateOf(WebPreviewServer.start(file)) }
    var revision by remember(file.absolutePath) { mutableStateOf(0) }

    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    tint = AccentSoft,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (expanded) "Tap to collapse" else "Tap to preview",
                        fontSize = 10.sp,
                        color = TextMuted,
                    )
                }
                IconButton(onClick = { revision++ }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Reload preview",
                        tint = TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                }
                IconButton(
                    onClick = { url?.let { WebViewActivity.start(context, it, file.name) } },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = "Open full screen",
                        tint = TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            if (expanded) {
                val target = url
                if (target == null) {
                    Text(
                        text = "Could not start the preview server.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    InlineWebView(url = target, revision = revision)
                }
            }
        }
    }
}

/** A WebView sized to a phone-friendly slice of the transcript. */
@Composable
private fun InlineWebView(url: String, revision: Int) {
    var height by remember(url) { mutableIntStateOf(320) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 520.dp)
            .border(1.dp, Border),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(false)
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    isVerticalScrollBarEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            // Grow the frame to the document so a short page is not letterboxed.
                            view?.post {
                                view.measure(
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                )
                                height = (view.measuredHeight + 40).coerceIn(200, 520)
                            }
                        }
                    }
                }
            },
            update = { view ->
                if (view.url != url || view.tag != revision) {
                    view.tag = revision
                    view.loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp),
        )
    }
}

/** Decodes a `data:image/...;base64,` URL, or falls back to an http(s) fetch on a worker thread. */
private fun decodePreview(url: String): android.graphics.Bitmap? {
    if (url.isEmpty()) return null
    return try {
        if (url.startsWith("data:")) {
            val comma = url.indexOf(',')
            if (comma < 0) null else android.util.Base64.decode(url.substring(comma + 1), android.util.Base64.DEFAULT)
                .let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
        } else {
            null
        }
    } catch (t: Throwable) {
        null
    }
}

/** Collapsible "thinking" trace, shown the way Claude Code collapses its reasoning. */
@Composable
private fun ThinkingBlock(part: ChatPart) {
    var expanded by remember(part.id) { mutableStateOf(false) }
    val running = part.text.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh.copy(alpha = 0.45f))
            .clickable { if (!running) expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (running) "Thinking…" else "Thought for a moment",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!running) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = part.text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = TextMuted,
            )
        }
    }
}

/** A single tool invocation with its live status; tap to reveal the captured output. */
@Composable
private fun ToolCard(part: ChatPart) {
    var expanded by remember(part.id) { mutableStateOf(false) }
    val state = part.state
    val status = state?.status.orEmpty()
    val output = state?.output.orEmpty()
    val canExpand = output.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceColor)
            .border(1.dp, Border, RoundedCornerShape(10.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Icon(
                toolIcon(part.tool),
                contentDescription = null,
                tint = toolColor(status),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = part.tool.ifEmpty { "tool" },
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 13.sp,
                    color = TextPrimary,
                )
                val summary = toolSummary(state?.input.orEmpty())
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ToolStatus(status = status)
            if (canExpand) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded && output.isNotBlank()) {
            HorizontalDivider(color = Border, thickness = 1.dp)
            Text(
                text = output.trim().take(4_000),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgElevated)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun ToolStatus(status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            "completed" -> {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Success, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("done", fontSize = 11.sp, color = Success, style = MaterialTheme.typography.labelSmall)
            }

            "error", "failed" -> {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Danger, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("failed", fontSize = 11.sp, color = Danger, style = MaterialTheme.typography.labelSmall)
            }

            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = if (status == "running") Accent else TextMuted,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (status == "running") "running" else "queued",
                    fontSize = 11.sp,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun toolIcon(tool: String): ImageVector = when (tool) {
    "bash", "shell", "run" -> Icons.Outlined.Terminal
    "edit", "patch" -> Icons.Outlined.Edit
    "write", "create" -> Icons.Outlined.Create
    "read", "view" -> Icons.Outlined.Article
    "glob", "grep", "search" -> Icons.Outlined.Search
    "task", "agent", "todo_write" -> Icons.Outlined.SmartToy
    "webfetch", "browser", "fetch" -> Icons.Outlined.Language
    else -> Icons.Outlined.Article
}

private fun toolColor(status: String): Color = when (status) {
    "completed" -> Success
    "error", "failed" -> Danger
    else -> AccentSoft
}

/**
 * Turns a tool's arguments into a one-line preview, preferring the field that actually names what
 * the tool is operating on (`command`, `path`, `pattern`, `url`, ...).
 */
private fun toolSummary(input: String): String {
    if (input.isBlank()) return ""
    return try {
        val obj = JSONObject(input)
        val preferred = listOf("command", "path", "file_path", "filePath", "file", "pattern", "url", "query")
        preferred.asSequence()
            .mapNotNull { key -> obj.optString(key).takeIf { it.isNotEmpty() } }
            .firstOrNull() ?: obj.toString()
    } catch (t: Throwable) {
        input
    }
}

// =================================================================================================
// Markdown
// =================================================================================================

private data class MarkdownBlock(val code: Boolean, val text: String)

private const val URL_TAG = "url"

/** A fenced code block with a copy affordance in its corner. */
@Composable
private fun CodeBlock(code: String) {
    var copied by remember(code) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceColor)
                .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Text(
                text = if (copied) "Copied" else "Copy",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (copied) Success else TextMuted,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                    scope.launch {
                        delay(1_600)
                        copied = false
                    }
                },
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) Success else TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Color(0xFFD7DDF0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp, top = 2.dp),
        )
    }
}

/** Bold, inline code, or a bare link - the three things an assistant reply actually decorates. */
private val INLINE_MARKER = Regex("""(\*\*[^*\n]+\*\*|`[^`\n]+`|https?://[^\s)\]}>"',]+)""")

/**
 * A deliberately small markdown renderer: fenced code blocks, `inline code` and **bold**.
 *
 * A full markdown engine is not worth its weight here; these three cover what an assistant reply
 * actually contains while staying cheap enough to recompose on every streamed delta.
 */
@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { splitMarkdown(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            if (block.code) {
                CodeBlock(code = block.text.trimEnd('\n'))
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                val annotated = remember(block.text) { inlineMarkup(block.text) }
                val context = LocalContext.current
                ClickableText(
                    text = annotated,
                    // Always name the colour: `bodyMedium` leaves it Unspecified and ClickableText
                    // would fall back to LocalContentColor, which used to be black here.
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        lineHeight = 21.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { offset ->
                        annotated.getStringAnnotations(URL_TAG, offset, offset)
                            .firstOrNull()
                            ?.let { WebViewActivity.start(context, it.item) }
                    },
                )
            }
        }
    }
}

private fun splitMarkdown(text: String): List<MarkdownBlock> {
    val blocks = ArrayList<MarkdownBlock>()
    val buffer = StringBuilder()
    var inCode = false
    for (line in text.lineSequence()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock(code = true, text = buffer.toString())
            } else if (buffer.isNotBlank()) {
                blocks += MarkdownBlock(code = false, text = buffer.toString())
            }
            buffer.setLength(0)
            inCode = !inCode
        } else {
            buffer.append(line).append('\n')
        }
    }
    if (buffer.isNotBlank()) {
        blocks += MarkdownBlock(code = inCode, text = buffer.toString())
    }
    return blocks
}

private fun inlineMarkup(raw: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_MARKER.findAll(raw)) {
        if (match.range.first > cursor) append(raw.substring(cursor, match.range.first))
        val token = match.value
        if (token.startsWith("**")) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.removeSurrounding("**"))
            }
        } else if (token.startsWith("http")) {
            pushStringAnnotation(URL_TAG, token)
            withStyle(SpanStyle(color = AccentSoft, textDecoration = TextDecoration.Underline)) {
                append(token)
            }
            pop()
        } else {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = AccentSoft,
                    background = SurfaceHigh,
                ),
            ) {
                append(token.removeSurrounding("`"))
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

// =================================================================================================
// Chrome: status, composer, banners, empty state, history
// =================================================================================================

/**
 * The status line, rendered inline in the turn that is being produced.
 *
 * Status only — the build/plan switch lives above the composer, so repeating the mode here would
 * just be the same control printed twice. Left-aligned and unboxed so it reads as part of the answer
 * rather than as chrome above it.
 */
@Composable
private fun InlineStatus(status: String, busy: Boolean) {
    // Gated on `busy`, not just on the text being non-blank. The runtime derives the status from
    // message content as well as from the session status, so a finished reply leaves "Responding…"
    // in the snapshot forever; rendering it unconditionally showed a stalled-looking label with no
    // spinner beside it once the turn was actually over.
    if (!busy || status.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(11.dp),
            strokeWidth = 2.dp,
            color = Accent,
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = status,
            fontSize = 12.sp,
            color = TextMuted,
        )
    }
}

/**
 * The build/plan switch, above the composer and flush left.
 *
 * It sits on its own row because it is a mode, not a status: it stays put whether or not the agent
 * happens to be working, where the status appears and disappears as a reply streams.
 */
@Composable
private fun ModeSwitch(
    selected: String,
    agents: List<AgentInfo>,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // The catalogue is the source of truth: `GET /agent` reports the modes this opencode actually
    // has, with their own descriptions. A hardcoded build/plan list would silently hide a custom
    // agent the user registered, and invent descriptions the server never sent.
    val modes = if (agents.isEmpty()) {
        listOf(AgentInfo("build", "", "primary"), AgentInfo("plan", "", "primary"))
    } else {
        agents
    }
    if (selected.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { open = true }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = AccentSoft,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selected,
                    fontSize = 11.sp,
                    color = AccentSoft,
                    fontFamily = FontFamily.Monospace,
                )
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Switch mode",
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = BgElevated,
            ) {
                MenuHeading("Mode")
                modes.forEach { candidate ->
                    val isSelected = candidate.name == selected
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) AccentSoft else TextPrimary,
                                    )
                                    if (candidate.description.isNotEmpty()) {
                                        Text(
                                            text = candidate.description,
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = AccentSoft,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        },
                        onClick = { open = false; onPick(candidate.name) },
                    )
                }
            }
        }
    }
}

/** Shared left inset for the composer's text and its control row. One number, both children. */
private val COMPOSER_INSET = 14.dp

@Composable
private fun Composer(
    value: String,
    busy: Boolean,
    modelLabel: String,
    models: List<ModelInfo>,
    selectedModel: ModelInfo?,
    autoFree: Boolean,
    onAutoFree: () -> Unit,
    variants: List<String>,
    selectedVariant: String,
    attachments: List<Attachment>,
    providers: List<String>,
    listening: Boolean,
    transcribing: Boolean = false,
    onChange: (String) -> Unit,
    onVoiceResult: () -> Unit,
    onAction: () -> Unit,
    onPickModel: (ModelInfo) -> Unit,
    onPickVariant: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onConfigureProvider: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
        }

        Surface(
            color = SurfaceColor,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // One inset for the text and the control row below it. They were 14dp and 8dp
            // respectively, so the "+" glyph sat ~19dp right of the first character and the field
            // read as misaligned. Every child starts at the same x now.
            Column(
                modifier = Modifier.padding(
                    start = COMPOSER_INSET,
                    end = 8.dp,
                    top = 10.dp,
                    bottom = 8.dp,
                ),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp, max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Ask anything…",
                                    color = TextMuted,
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        }
                    },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Attachment + provider setup.
                    PlusMenu(
                        providers = providers,
                        onPickPhoto = onPickPhoto,
                        onPickFile = onPickFile,
                        onConfigureProvider = onConfigureProvider,
                    )

                    // The model lives on the composer, not behind the plus: it changes far more often
                    // than attachments do, and burying it made the picker feel hidden.
                    //
                    // No `weight` here on purpose. Weighting the picker *and* keeping a weighted
                    // spacer made the send button's position depend on whether a variant chip was
                    // present, so it drifted to the middle whenever the model had no options. One
                    // weighted spacer, at the end, pins send to the right unconditionally.
                    ModelPicker(
                        models = models,
                        selectedModel = selectedModel,
                        modelLabel = modelLabel,
                        autoFree = autoFree,
                        onPick = onPickModel,
                        onAutoFree = onAutoFree,
                    )

                    // Reasoning / token effort, but only when the model actually offers variants.
                    if (variants.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        VariantPicker(
                            variants = variants,
                            selected = selectedVariant,
                            onPick = onPickVariant,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Removed TranscribingPill entirely — Option B: tap to record, tap to stop, no intermediate state.

                    MicButton(
                        listening = listening,
                        enabled = !busy,
                        onClick = onVoiceResult,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            // Square with rounded corners rather than a circle: a circle implies a round trip or a
                            // radio action, and the arrow-up glyph is narrower than it is tall so it
                            // reads off-centre inside one.
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (busy) Color.Black else Accent)
                            .clickable(onClick = onAction),
                    ) {
                        Icon(
                            imageVector = if (busy) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                            contentDescription = if (busy) "Stop" else "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How many models the picker shows before the search is required.
 *
 * The catalogue advertises over a thousand free models. Rendering all of them inside a dropdown on
 * every open is both slow and impossible to scan, so the list is cut and the remainder is counted
 * for the user rather than silently dropped.
 */
private const val MODEL_PICKER_LIMIT = 60

/** The current model, with its full catalogue behind a tap. */
@Composable
private fun ModelPicker(
    models: List<ModelInfo>,
    selectedModel: ModelInfo?,
    modelLabel: String,
    autoFree: Boolean,
    onPick: (ModelInfo) -> Unit,
    onAutoFree: () -> Unit,
) {
    // Plain remember, never rememberSaveable: a dropdown that comes back open after the screen is
    // rebuilt is a dropdown nobody expects to be waiting for them, and the "is it already open?"
    // question is not worth answering with stale state.
    var open by remember { mutableStateOf(false) }
    val label = when {
        autoFree -> "Auto · free"
        else -> selectedModel?.modelName ?: modelLabel.ifEmpty { "Model" }
    }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // A maximum alone is what let this squeeze the Send button off the row: the chip
                // would grow to 140dp, the variant chip beside it would take its own share, and the
                // weighted spacer — which is the only flexible child — absorbed the squeeze, leaving
                // Send and the mic compressed. Capping this lower and letting the text ellipsize
                // keeps the row's fixed-size controls (mic, Send) at their natural width, which is
                // what "Send must always be visible" actually requires.
                modifier = Modifier.widthIn(max = 96.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(15.dp),
            )
        }
        // A DropdownMenu rather than a hand-rolled Card. It already does the three things that are easy to
        // get wrong by hand: it renders only while open, it anchors to this chip, and it dismisses on
        // an outside tap or a back press. An earlier Card attempt dropped all three and rendered
        // permanently, swallowing the composer.
        //
        // The height cap is the one thing it does not do: it grows to fit its content and runs off
        // the bottom of the screen with the keyboard up. Capping it makes the list scroll instead.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = BgElevated,
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            // No Column wrapper here. DropdownMenu already provides a scrolling column internally;
            // wrapping it in another verticalScroll produced a nested-scroll surface that threw off
            // the menu's own measurement and left the brace structure unbalanced.
            MenuHeading("Model")

            // Automatic selection sits above the list and above the search field, because it is the
            // default most people want and burying it under a thousand rows would defeat the point.
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto · free",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (autoFree) AccentSoft else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Pick the strongest free model automatically",
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (autoFree) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = AccentSoft,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                },
                onClick = { open = false; onAutoFree() },
            )

            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 4.dp))

            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Loading models…", color = TextMuted) },
                    onClick = { open = false },
                )
            } else {
                // Search, because the catalogue now runs to over a thousand entries and scrolling a
                // dropdown to find one model is not a usable interaction. Matches on the model name,
                // the provider name and the id, since people look for any of the three.
                var query by remember { mutableStateOf("") }
                val needle = query.trim().lowercase()
                val matches = remember(models, needle) {
                    if (needle.isEmpty()) models else models.filter {
                        it.modelName.lowercase().contains(needle) ||
                            it.providerName.lowercase().contains(needle) ||
                            it.modelID.lowercase().contains(needle)
                    }
                }

                // Kept short on purpose: a dropdown that renders 1100 rows at once is slow to open and
                // impossible to scan. The count below the field says when the list has been cut.
                val shown = if (needle.isEmpty()) matches.take(MODEL_PICKER_LIMIT) else matches.take(MODEL_PICKER_LIMIT)

                DropdownMenuItem(
                    enabled = false,
                    text = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search models…", fontSize = 12.sp, color = TextMuted) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentSoft,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp, max = 52.dp),
                        )
                    },
                    onClick = {},
                )

                if (shown.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No model matches \"$query\"", color = TextMuted) },
                        onClick = {},
                    )
                }

                shown.forEach { model ->
                    val selected = selectedModel?.key == model.key
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.modelName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) AccentSoft else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = model.providerName + if (model.isFree) " · free" else "",
                                        fontSize = 10.sp,
                                        color = if (model.isFree) Success else TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!model.attachment) {
                                    Text("no images", fontSize = 9.sp, color = TextMuted)
                                }
                                if (selected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = AccentSoft,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        },
                        onClick = { open = false; onPick(model) },
                    )
                }

                // Tells the user the list was cut rather than silently hiding the other thousand.
                if (matches.size > shown.size) {
                    DropdownMenuItem(
                        enabled = false,
                        text = {
                            Text(
                                text = "${matches.size - shown.size} more — refine the search",
                                fontSize = 11.sp,
                                color = TextMuted,
                            )
                        },
                        onClick = {},
                    )
                }
            }
        }
    }
}

/**
 * Reasoning / token-effort selector.
 *
 * Only rendered when the catalogue reports variants for the chosen model — most models have none, and
 * showing an empty control would imply a setting that does nothing.
 */
@Composable
private fun VariantPicker(
    variants: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = selected.ifEmpty { "Default" },
                fontSize = 11.sp,
                color = AccentSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Bounded for the same reason as the model chip: an unbounded chip here was the
                // actual cause of Send disappearing, because a long variant name ("max reasoning")
                // pushed the row past the screen width and Compose shrank the flexible children —
                // the weighted spacer and the fixed-size controls together — until Send was gone.
                modifier = Modifier.widthIn(max = 72.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = BgElevated,
        ) {
            MenuHeading("Reasoning")
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Default",
                        color = if (selected.isEmpty()) AccentSoft else TextPrimary,
                    )
                },
                onClick = { open = false; onPick("") },
            )
            variants.forEach { variant ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = variant.replaceFirstChar { it.uppercaseChar() },
                            color = if (selected == variant) AccentSoft else TextPrimary,
                        )
                    },
                    onClick = { open = false; onPick(variant) },
                )
            }
        }
    }
}

// =================================================================================================
// Composer chrome: attach / model / agent, and the queued attachments
// =================================================================================================
/**
 * The cross on the left of the composer. Opens a card with the two things you *add* or *connect*.
 *
 * Deliberately only those two: the model moved onto the composer itself (it changes far more often,
 * and hiding it behind a tap made it feel absent), and the build/plan mode moved to the status line
 * where it reads as part of what the agent is doing.
 */
@Composable
private fun PlusMenu(
    providers: List<String>,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onConfigureProvider: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add",
                tint = TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        // DropdownMenu is the popup primitive that anchors to the anchor and dismisses on an outside
        // tap; styled here to read as a small card rather than a list of settings.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = BgElevated,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(min = 240.dp, max = 300.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "Attachment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Picture or file",
                            fontSize = 10.sp,
                            color = TextMuted,
                        )
                    }
                },
                leadingIcon = {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = AccentSoft, modifier = Modifier.size(18.dp))
                },
                onClick = { open = false; onPickFile() },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = providers.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                ?: "AI providers and your own service keys",
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Bolt, null, tint = AccentSoft, modifier = Modifier.size(18.dp))
                },
                onClick = { open = false; onConfigureProvider() },
            )
        }
    }
}

@Composable
internal fun MenuHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** Horizontally scrollable row of queued attachments, each removable before the prompt goes out. */
@Composable
private fun AttachmentStrip(attachments: List<Attachment>, onRemove: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 7.dp),
    ) {
        attachments.forEach { attachment ->
            Surface(
                color = SurfaceColor,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Border),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 7.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    val thumb = attachment.thumbnail
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(5.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachment.name,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onRemove(attachment.id) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Amber strip for non-fatal problems such as "this model cannot read the picture you attached". */
@Composable
private fun NoticeBar(message: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Warning.copy(alpha = 0.14f))
            .padding(start = 14.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = Warning, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Warning, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Danger.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Danger, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = Danger,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Danger, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * What the app shows while the environment is coming up.
 *
 * This is the app's front door now — there is no onboarding screen — so it carries the brand mark and
 * a slow pulse rather than a bare progress ring. A fresh install unpacks a Termux rootfs and installs
 * a Debian container, which genuinely takes minutes; a spinner with no company would read as frozen.
 */
@Composable
private fun InitializingScreen() {
    // The mark blinks and tilts on its own. A progress bar alone tells the user nothing about
    // whether anything is happening, and a fresh install really does take minutes; something with
    // a pulse in it distinguishes "slow" from "stuck".
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        CodyMark(size = 84.dp, animated = true)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Cody",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Setting up your environment…",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(28.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = AccentSoft,
            trackColor = SurfaceHigh,
        )
    }
}

@Composable
private fun ServerNotice(message: String, progress: Boolean, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        if (progress) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.height(18.dp))
        }
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = AccentSoft)
            }
        }
    }
}

private val SUGGESTIONS = listOf(
    "Summarize what this project does",
    "Find and fix a bug",
    "Explain the architecture",
    "Add tests for the main module",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Accent.copy(alpha = 0.14f))
                .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        ) {
            Text(
                text = ">",
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AccentSoft,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "What should we build?",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(22.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUGGESTIONS.forEach { suggestion ->
                SuggestionChip(text = suggestion, onClick = { onSuggestion(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        color = SurfaceColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

// =================================================================================================
// Agent interjections: tool permissions and multiple-choice questions
// =================================================================================================

/**
 * Approval sheet for a tool call that opencode classifies as `ask`.
 *
 * The three answers map exactly onto what the API accepts: `once`, `always` and `reject`.
 */
@Composable
private fun PermissionDialog(prompt: PermissionPrompt, onReply: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onReply("reject") },
        containerColor = BgElevated,
        title = {
            Text("Allow this tool?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = SurfaceColor,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = prompt.permission.ifEmpty { "tool" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = AccentSoft,
                        )
                        (prompt.resources + prompt.patterns)
                            .distinct()
                            .take(5)
                            .forEach { target ->
                                Text(
                                    text = target,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                }
                Text(
                    text = "Only allow this if you asked for it.",
                    fontSize = 11.sp,
                    color = TextMuted,
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onReply("once") }) {
                    Text("Allow once", color = AccentSoft, fontSize = 13.sp)
                }
                TextButton(onClick = { onReply("always") }) {
                    Text("Always allow", color = AccentSoft, fontSize = 13.sp)
                }
                TextButton(onClick = { onReply("reject") }) {
                    Text("Reject", color = Danger, fontSize = 13.sp)
                }
            }
        },
    )
}

/**
 * The agent's multiple-choice question.
 *
 * A single, non-multi question with no free-text option is answered by tapping, the way Claude Code
 * does it; anything more involved collects its selections first and sends them together.
 */
@Composable
private fun QuestionDialog(
    prompt: QuestionPrompt,
    onAnswer: (List<List<String>>) -> Unit,
    onReject: () -> Unit,
) {
    val selections = remember(prompt.id) { mutableStateMapOf<Int, List<String>>() }
    val tapToSubmit = prompt.singleTap
    val ready = prompt.blocks.indices.all { index ->
        prompt.blocks[index].custom || !selections[index].isNullOrEmpty()
    }
    val many = prompt.blocks.size > 1

    AlertDialog(
        onDismissRequest = onReject,
        containerColor = BgElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = prompt.blocks.firstOrNull()?.header ?: "Question",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (many) {
                    Text(
                        text = "${prompt.blocks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentSoft,
                    )
                }
            }
        },
        text = {
            // Scrollable, and capped. Without this a multi-block request simply does not fit: the
            // lower options and the Send button fall off the screen, the question cannot be answered,
            // and the reply stays parked forever waiting for an answer that can never be given.
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                prompt.blocks.forEachIndexed { blockIndex, block ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (many || block.header.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (many) {
                                    Text(
                                        text = "${blockIndex + 1}.",
                                        fontSize = 11.sp,
                                        color = AccentSoft,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(block.header, fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Text(block.question, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        block.options.forEach { option ->
                            val chosen = selections[blockIndex]?.contains(option.label) == true
                            Surface(
                                color = if (chosen) Accent.copy(alpha = 0.14f) else SurfaceColor,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (chosen) Accent else Border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (tapToSubmit && !block.multiple) {
                                            onAnswer(listOf(listOf(option.label)))
                                        } else if (block.multiple) {
                                            val current = selections[blockIndex].orEmpty()
                                            selections[blockIndex] =
                                                if (chosen) current - option.label else current + option.label
                                        } else {
                                            selections[blockIndex] = listOf(option.label)
                                        }
                                    },
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (chosen) AccentSoft else TextPrimary,
                                    )
                                    if (option.description.isNotEmpty()) {
                                        Text(
                                            text = option.description,
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!tapToSubmit) {
                TextButton(
                    enabled = ready,
                    onClick = { onAnswer(prompt.blocks.indices.map { selections[it].orEmpty() }) },
                ) {
                    Text("Send", color = if (ready) AccentSoft else TextMuted, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Skip", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}

// =================================================================================================
// Getting the agent's output out of the container
// =================================================================================================

/**
 * Browses the workspace and hands files to the user.
 *
 * "Share" is always offered because it works on every Android version; "Save to Downloads" is the
 * convenience path and only needs a runtime permission below Android 10.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<WorkspaceFile>?>(null) }
    var actionTarget by remember { mutableStateOf<WorkspaceFile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = actionTarget
        actionTarget = null
        message = if (!granted) {
            "Storage permission denied — use Share instead."
        } else if (target != null && WorkspaceFiles.saveToDownloads(context, target)) {
            "Saved to Downloads."
        } else {
            "Could not save that file."
        }
    }

    LaunchedEffect(Unit) { files = WorkspaceFiles.list() }

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
                Text("Files", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = files?.size?.toString() ?: "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            HorizontalDivider(color = Border)

            val list = files
            when {
                list == null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(20.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Accent,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Scanning the workspace…", fontSize = 12.sp, color = TextMuted)
                    }
                }

                list.isEmpty() -> Text(
                    text = "Nothing has been written to the workspace yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(20.dp),
                )

                else -> LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    items(list, key = { it.file.absolutePath }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { actionTarget = item }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                        ) {
                            Icon(
                                fileIcon(item),
                                contentDescription = null,
                                tint = AccentSoft,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${item.scope} · ${formatSize(item.size)} · " +
                                        if (item.modified > 0) dateFormat.format(Date(item.modified)) else "",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = "Send",
                                tint = TextMuted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            message?.let { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = AccentSoft,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            containerColor = BgElevated,
            title = {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(target.relativePath, fontSize = 12.sp, color = TextMuted)
                    Text(
                        text = "${target.scope} · ${formatSize(target.size)} · " +
                            if (target.modified > 0) dateFormat.format(Date(target.modified)) else "",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            },
            confirmButton = {
                Row {
                    if (target.name.endsWith(".glb", ignoreCase = true)) {
                        TextButton(
                            onClick = {
                                actionTarget = null
                                WebViewActivity.startModel(context, target.file, target.name)
                            },
                        ) {
                            Text("View", color = AccentSoft, fontSize = 13.sp)
                        }
                    }
                    TextButton(
                        onClick = {
                            actionTarget = null
                            message =
                                if (WorkspaceFiles.share(context, target)) null else "Could not share that file."
                        },
                    ) {
                        Text("Share", color = AccentSoft, fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                actionTarget = null
                                message = if (WorkspaceFiles.saveToDownloads(context, target)) {
                                    "Saved to Downloads."
                                } else {
                                    "Could not save that file."
                                }
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                    ) {
                        Text("Save", color = AccentSoft, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text("Cancel", color = TextMuted, fontSize = 13.sp)
                }
            },
        )
    }
}

private fun fileIcon(item: WorkspaceFile): ImageVector {
    val name = item.name.lowercase()
    return when {
        name.endsWith(".glb") || name.endsWith(".gltf") -> Icons.Outlined.ViewInAr
        item.mime.startsWith("image/") -> Icons.Outlined.PhotoLibrary
        item.mime.startsWith("video/") || item.mime.startsWith("audio/") -> Icons.Outlined.Language
        name.endsWith(".apk") || name.endsWith(".zip") || name.endsWith(".tar") ||
            name.endsWith(".gz") || name.endsWith(".7z") -> Icons.Outlined.Folder
        else -> Icons.Outlined.Article
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
