package com.termux.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.chat.OpenCodeApi
import com.termux.app.chat.OpenCodeRuntime
import com.termux.app.features.AppUpdater
import com.termux.app.features.BackupManager
import com.termux.app.features.FeatureInstructions
import com.termux.app.features.GitHubDeviceLogin
import com.termux.app.features.MemoryStore
import com.termux.app.features.RemoteDevices
import com.termux.app.features.SkillsImport
import com.termux.app.features.TeamAgents
import com.termux.app.features.UsageTracker
import com.termux.app.settings.CredentialCatalog
import com.termux.app.settings.CredentialStore
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.Bg
import com.termux.app.ui.theme.Border
import com.termux.app.ui.theme.Surface
import com.termux.app.ui.theme.SurfaceHigh
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cl0udly-style "More" surface: capabilities that sit around the chat rather than inside it.
 *
 * Every section here touches a feature object that owns its own persistence. The screen holds only
 * transient UI state and one shared log, so a failed import or a slow probe cannot corrupt chat
 * state, and no feature can take the transcript down with it.
 */
@Composable
fun FeatureHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    var log by remember { mutableStateOf("") }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    fun say(line: String) {
        mainHandler.post { log = (line + "\n" + log).take(4000) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.dp, Border, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Text("More", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RemoteSection(context, scope, revision, { revision++ }, ::say)
            MemorySection(context, scope, revision, { revision++ }, ::say)
            TeamSection(context, scope, revision, { revision++ }, ::say)
            UsageSection(context, revision)
            GitHubSection(context, scope, ::say)
            SkillsSection(context, scope, ::say)
            UpdateSection(context, scope, ::say)
            BackupSection(context, scope, ::say)
            if (log.isNotEmpty()) {
                Section("Log") {
                    Text(
                        log,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(title.uppercase(), color = Accent, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RemoteSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    revision: Int,
    changed: () -> Unit,
    say: (String) -> Unit,
) {
    val devices = remember(revision) { RemoteDevices.all(context) }
    val active = remember(revision) { RemoteDevices.active(context) }
    var showAdd by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var probing by remember { mutableStateOf(false) }

    fun copyInvite() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Cody pairing command", RemoteDevices.inviteCommand()))
        Toast.makeText(context, "Pairing command copied", Toast.LENGTH_SHORT).show()
    }

    Section("Your computer") {
        Text(
            if (active == null) "Using the opencode server on this phone."
            else "Using ${active.name} at ${active.url}",
            color = TextMuted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAdd = true }) { Text("Add device") }
            OutlinedButton(onClick = { showInvite = true }) { Text("Pair a computer") }
            if (active != null) {
                OutlinedButton(onClick = {
                    RemoteDevices.select(context, null)
                    OpenCodeApi.useLocal()
                    OpenCodeRuntime.switchServer(context)
                    changed()
                    say("Back on the phone's server")
                }) { Text("This phone") }
            }
        }
        devices.forEach { device ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceHigh)
                    .clickable {
                        RemoteDevices.select(context, device.id)
                        OpenCodeApi.useRemote(device.url, device.password)
                        OpenCodeRuntime.switchServer(context)
                        changed()
                        say("Switched to ${device.name}")
                    }
                    .padding(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        device.url,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.DeleteOutline,
                    "Remove device",
                    tint = TextMuted,
                    modifier = Modifier.clickable {
                        RemoteDevices.remove(context, device.id)
                        changed()
                    },
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add device") },
            text = {
                Column {
                    Text("Use the address and password from the QR on the computer.", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(url, { url = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (probing) {
                        Spacer(Modifier.height(8.dp))
                        Text("Checking the server…", color = TextMuted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = RemoteDevices.normalizeUrl(url)
                    if (normalized == null) {
                        say("! invalid address")
                        return@TextButton
                    }
                    probing = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { OpenCodeApi.probe(normalized, password) }
                        probing = false
                        if (!ok) {
                            say("! could not reach $normalized — check the address, password and firewall")
                        } else {
                            val device = RemoteDevices.Device(
                                id = RemoteDevices.newId(),
                                name = name.ifBlank { normalized.substringAfter("://").substringBefore(':') },
                                url = normalized,
                                password = password,
                            )
                            RemoteDevices.add(context, device)
                            RemoteDevices.select(context, device.id)
                            OpenCodeApi.useRemote(device.url, device.password)
                            OpenCodeRuntime.switchServer(context)
                            changed()
                            showAdd = false
                            url = ""; name = ""; password = ""
                            say("Connected to ${device.name}")
                        }
                    }
                }) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }

    if (showInvite) {
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text("Pair a computer") },
            text = {
                Column {
                    Text(
                        "Run this on the computer. Scan the QR it prints with any camera app, or paste the cody:// link into the phone's browser. Both devices must be on the same network.",
                        color = TextMuted, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        RemoteDevices.inviteCommand(),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { copyInvite(); showInvite = false }) { Text("Copy command") } },
            dismissButton = { TextButton(onClick = { showInvite = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun MemorySection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    revision: Int,
    changed: () -> Unit,
    say: (String) -> Unit,
) {
    var enabled by remember(revision) { mutableStateOf(FeatureInstructions.memoryEnabled(context)) }
    var files by remember { mutableStateOf(emptyList<String>()) }
    var selected by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(revision) { files = MemoryStore.list(context) { } }
    LaunchedEffect(selected) {
        text = if (selected.isEmpty()) "" else MemoryStore.read(context, selected) { }
    }

    Section("Memory") {
        RowSwitch("Remember facts across chats", enabled) {
            enabled = it
            FeatureInstructions.setMemoryEnabled(context, it)
        }
        Text(
            "Markdown shelves in the container at ${MemoryStore.DIR}. The agent reads MEMORY.md as an index.",
            color = TextMuted, fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showNew = true }) { Text("New file") }
            if (selected.isNotEmpty()) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val ok = MemoryStore.write(context, selected, text) { say(it) }
                        say(if (ok) "Saved $selected" else "! could not save $selected")
                        changed()
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = {
                    scope.launch {
                        MemoryStore.delete(context, selected) { say(it) }
                        selected = ""; text = ""; changed()
                    }
                }) { Text("Delete") }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (files.isEmpty()) {
            Text("No memory files yet. Create one to get started.", color = TextMuted, fontSize = 11.sp)
        }
        files.forEach { name ->
            Text(
                name,
                color = if (name == selected) Accent else TextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = name }
                    .padding(vertical = 4.dp),
            )
        }
        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                text, { text = it },
                label = { Text(selected) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New memory file") },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    label = { Text("Filename, e.g. preferences.md") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            val ok = MemoryStore.write(context, name, "# ${name.substringBeforeLast('.')}\n\n", { say(it) })
                            if (ok) { selected = name; changed() } else say("! invalid filename")
                            showNew = false; newName = ""
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TeamSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    revision: Int,
    changed: () -> Unit,
    say: (String) -> Unit,
) {
    val members = remember(revision) { TeamAgents.load(context) }
    var enabled by remember(revision) { mutableStateOf(TeamAgents.enabled(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    Section("Team of agents") {
        RowSwitch("Let the main agent delegate", enabled) {
            enabled = it
            TeamAgents.setEnabled(context, it)
            if (it) scope.launch {
                val ok = withContext(Dispatchers.IO) { TeamAgents.sync(context) }
                say(if (ok) "Helpers added to opencode" else "! this opencode version rejected the helper config")
            }
        }
        Text(
            "Helpers run as opencode subagents. Their names and roles are added to each prompt so the main agent knows who is available.",
            color = TextMuted, fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (members.isEmpty()) {
            Text("No helpers yet. Add one below.", color = TextMuted, fontSize = 11.sp)
        }
        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(member.name, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        member.role + if (member.model.isBlank()) "" else " · ${member.model}",
                        color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.DeleteOutline,
                    "Remove helper",
                    tint = TextMuted,
                    modifier = Modifier.clickable {
                        TeamAgents.save(context, members - member)
                        changed()
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Helper")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { TeamAgents.sync(context) }
                    say(if (ok) "Helper config applied" else "! helper config was rejected")
                }
            }) { Text("Apply") }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a helper") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(role, { role = it }, label = { Text("Role") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        model, { model = it },
                        label = { Text("Model (optional, provider/model)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TeamAgents.save(context, members + TeamAgents.Member(name, role, model))
                    showAdd = false; name = ""; role = ""; model = ""; changed()
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun UsageSection(context: Context, revision: Int) {
    var tick by remember { mutableStateOf(0) }
    val windows = remember(revision, tick) { UsageTracker.compute() }
    Section("Usage") {
        UsageRow("Last 5 hours", windows.first)
        UsageRow("Last 7 days", windows.second)
        UsageRow("Last 30 days", windows.third)
        TextButton(onClick = { tick++ }) { Text("Refresh") }
        Text(
            "Reported by the server on each reply. Rough prompt hint: $%.4f for a 4k-character Claude prompt.".format(
                UsageTracker.estimate(context, "", "claude", 4000),
            ),
            color = TextMuted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun UsageRow(label: String, window: UsageTracker.Window) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "$%.4f · %s tokens".format(window.cost, window.tokens),
            color = TextPrimary, fontSize = 12.sp,
        )
    }
}

@Composable
private fun GitHubSection(context: Context, scope: kotlinx.coroutines.CoroutineScope, say: (String) -> Unit) {
    var flow by remember { mutableStateOf<GitHubDeviceLogin.Flow?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var signedIn by remember { mutableStateOf(false) }
    val gh = remember { CredentialCatalog.BUILTINS.first { it.envVar == "GH_TOKEN" } }

    LaunchedEffect(Unit) {
        signedIn = CredentialStore.secret(context, gh.id)?.isNotBlank() == true
    }

    Section("GitHub") {
        Text(
            if (signedIn) "Signed in. The token is exported to the container as GH_TOKEN."
            else "Sign in once so the agent can clone, commit, push and open PRs as you.",
            color = TextMuted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            job?.cancel()
            job = scope.launch {
                try {
                    val started = withContext(Dispatchers.IO) { GitHubDeviceLogin.start() }
                    flow = started
                    say("Open ${started.verificationUri} and enter ${started.userCode}")
                    val deadline = System.currentTimeMillis() + started.expiresInSeconds * 1000L
                    while (System.currentTimeMillis() < deadline) {
                        delay(started.intervalSeconds * 1000L)
                        when (val outcome = withContext(Dispatchers.IO) { GitHubDeviceLogin.poll(started) }) {
                            is GitHubDeviceLogin.Outcome.Success -> {
                                CredentialStore.save(context, gh, outcome.token)
                                signedIn = true
                                flow = null
                                say("GitHub connected")
                                break
                            }
                            GitHubDeviceLogin.Outcome.Pending, GitHubDeviceLogin.Outcome.SlowDown -> Unit
                            GitHubDeviceLogin.Outcome.Denied -> { say("! GitHub sign-in denied"); flow = null; break }
                            GitHubDeviceLogin.Outcome.Expired -> { say("! code expired, try again"); flow = null; break }
                            is GitHubDeviceLogin.Outcome.Failed -> { say("! ${outcome.message}"); flow = null; break }
                        }
                    }
                } catch (t: Throwable) {
                    say("! ${t.message ?: "GitHub sign-in failed"}")
                    flow = null
                }
            }
        }) { Text(if (signedIn) "Sign in again" else "Sign in with GitHub") }

        flow?.let { active ->
            Spacer(Modifier.height(10.dp))
            Text("Code: ${active.userCode}", color = TextPrimary, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(active.verificationUri)))
                }) { Text("Open GitHub") }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("GitHub code", active.userCode))
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy code") }
                TextButton(onClick = { job?.cancel(); flow = null }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SkillsSection(context: Context, scope: kotlinx.coroutines.CoroutineScope, say: (String) -> Unit) {
    val pickSkill = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = SkillsImport.install(context, uri) { say(it) }
            say(if (ok) "Skill installed" else "! skill install failed")
        }
    }
    Section("Skills") {
        Text(
            "Import a SKILL.md or a zip containing one. The agent discovers it on the next turn.",
            color = TextMuted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { pickSkill.launch(arrayOf("*/*")) }) { Text("Import skill") }
    }
}

@Composable
private fun UpdateSection(context: Context, scope: kotlinx.coroutines.CoroutineScope, say: (String) -> Unit) {
    val progressHandler = remember { Handler(Looper.getMainLooper()) }
    var release by remember { mutableStateOf<AppUpdater.Release?>(null) }
    var progress by remember { mutableStateOf(-1) }
    Section("Updates") {
        Text("Check GitHub releases for a newer Cody APK.", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val found = withContext(Dispatchers.IO) { AppUpdater.check() }
                    release = found
                    say(found?.let { "Release ${it.tag} is available" } ?: "No release found or GitHub unreachable")
                }
            }) { Text("Check") }
            release?.let { found ->
                Button(onClick = {
                    scope.launch {
                        progress = 0
                        val apk = withContext(Dispatchers.IO) {
                            AppUpdater.download(context, found) { pct ->
                                progressHandler.post { progress = pct }
                            }
                        }
                        if (apk == null) {
                            say("! download failed")
                        } else {
                            val ok = withContext(Dispatchers.IO) { AppUpdater.install(context, apk) }
                            say(if (ok) "Installer opened" else "! install could not start — check unknown-source permission")
                        }
                        progress = -1
                    }
                }) { Text("Install ${found.tag}") }
            }
        }
        if (progress >= 0) Text("Downloading $progress%", color = Accent, fontSize = 12.sp)
    }
}

@Composable
private fun BackupSection(context: Context, scope: kotlinx.coroutines.CoroutineScope, say: (String) -> Unit) {
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = BackupManager.restore(context, uri) { say(it) }
            say(if (ok) "Backup restored — restart Cody" else "! restore failed")
        }
    }
    Section("Backup & move") {
        Text(
            "Saves the container home, workspace and app settings into one archive in Downloads.",
            color = TextMuted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val ok = BackupManager.export(context) { say(it) }
                    say(if (ok) "Backup saved to Downloads/Cody" else "! backup failed")
                }
            }) { Text("Export") }
            OutlinedButton(onClick = { pickBackup.launch(arrayOf("application/gzip", "application/x-tar", "*/*")) }) {
                Text("Restore")
            }
        }
    }
}
