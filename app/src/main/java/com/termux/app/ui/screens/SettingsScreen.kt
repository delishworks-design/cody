package com.termux.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.termux.app.settings.Credential
import com.termux.app.settings.CredentialStore
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.AccentSoft
import com.termux.app.ui.theme.Bg
import com.termux.app.ui.theme.BgElevated
import com.termux.app.ui.theme.Border
import com.termux.app.ui.theme.Cyan
import com.termux.app.ui.theme.Success
import com.termux.app.ui.theme.Surface
import com.termux.app.ui.theme.SurfaceHigh
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary

/**
 * Where the user's own accounts live.
 *
 * Two halves, and the split is the point. **AI providers** is opencode's own dashboard — it owns
 * the model credentials and the only supported way to change them. **Service keys** is for the
 * accounts the *agent* needs while doing work: a GitHub token to push, a Vercel token to deploy.
 * Neither can do the other's job, so they sit side by side rather than one pretending to be the
 * other.
 *
 * Values are written as you type rather than behind a Save button. A Save button here would mean
 * carrying dirty state across a scrolling list of a dozen rows and then failing to tell the user
 * which one was missed — the failure mode is silent, and a half-saved key is worse than none.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProviderDashboard: () -> Unit,
) {
    val context = LocalContext.current

    // Reloaded from the store rather than held authoritatively, so what is drawn is what is stored
    // even if a write failed.
    var revision by remember { mutableStateOf(0) }
    val credentials = remember(revision) { CredentialStore.all(context) }
    var values by remember(revision) {
        mutableStateOf(credentials.associate { it.id to (CredentialStore.secret(context, it.id) ?: "") })
    }
    val revealed by remember { mutableStateOf(emptySet<String>()) }
    var showAdd by remember { mutableStateOf(false) }

    val setCount = credentials.count { values[it.id]?.isNotBlank() == true }

    fun persist(credential: Credential, value: String) {
        values = values + (credential.id to value)
        CredentialStore.save(context, credential, value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Header
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
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ------------------------------------------------------------------ AI providers
            SectionLabel("AI providers")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgElevated)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "The models Cody talks to",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Anthropic, OpenAI, Kilo and the rest are set up in opencode's own dashboard, " +
                        "which Cody opens below. Three free providers are already connected, so this " +
                        "is only needed for a paid key.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = "Open provider dashboard",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenProviderDashboard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ------------------------------------------------------------------ Service keys
            Spacer(Modifier.height(26.dp))
            SectionLabel("Service keys")

            Text(
                "Accounts the agent can use while working — to push, deploy or call an API. " +
                    "They are yours to add and yours to clear.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            for (credential in credentials) {
                CredentialRow(
                    credential = credential,
                    value = values[credential.id].orEmpty(),
                    revealed = credential.id in revealed,
                    onChange = { persist(credential, it) },
                    onToggleReveal = {
                        revealed =
                            if (credential.id in revealed) revealed - credential.id
                            else revealed + credential.id
                    },
                    onClear = {
                        if (credential.builtin) {
                            persist(credential, "")
                        } else {
                            CredentialStore.remove(context, credential.id)
                            values = values - credential.id
                            revealed = revealed - credential.id
                            revision++
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
            }

            // Add custom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .clickable { showAdd = true }
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = AccentSoft, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Add another key",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AccentSoft,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Anything not listed — npm, Stripe, a private API, your own service.",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }

            // ------------------------------------------------------------------ Where they went
            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceHigh)
                    .padding(14.dp),
            ) {
                Text(
                    if (setCount == 0) "No keys stored" else "$setCount of ${credentials.size} keys stored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (setCount == 0) TextMuted else Success,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Written to the container so the agent and the CLIs can read them:",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    CredentialStore.CONTAINER_PATH,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Cyan,
                )
                if (setCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Keys are stored on this device only. They are never sent to Cody, never " +
                            "put in a prompt, and never committed.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showAdd) {
        AddKeyDialog(
            onConfirm = { label, envVar, value ->
                if (CredentialStore.addCustom(context, label, envVar, value) != null) {
                    showAdd = false
                    revision++
                }
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        color = TextMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * One key: label, what it is for, the value itself, and the two things you can do to it.
 *
 * The field is masked until asked, and the stored value is never rendered — only what the user is
 * currently typing. So there is no path in this screen where an existing key is visible, which is
 * also why the "is it set" state is a dot rather than the value.
 */
@Composable
private fun CredentialRow(
    credential: Credential,
    value: String,
    revealed: Boolean,
    onChange: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val isSet = value.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgElevated)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSet) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Success),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                credential.label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                credential.envVar,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(SurfaceHigh)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            if (!credential.builtin) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove ${credential.label}",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        if (credential.purpose.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                credential.purpose,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextMuted,
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = {
                Text(
                    if (isSet) "•••••••• saved" else "Paste your key",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
            },
            // The stored key is loaded into the field and masked, the same as every deployment
            // dashboard does it: a user pasting a key and getting one character wrong needs to see
            // it, and the eye toggle is the only way to see it.
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleReveal, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide" else "Show",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (isSet) {
                        IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear ${credential.label}",
                                tint = TextMuted,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentSoft,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (credential.url.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { openUrl(context, credential.url) }
                    .padding(vertical = 3.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Get one at ${shortHost(credential.url)}",
                    fontSize = 11.sp,
                    color = AccentSoft,
                )
            }
        }
    }
}

@Composable
private fun AddKeyDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var envVar by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 22.dp),
        title = { Text("Add a key", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "The environment variable decides which CLI picks it up automatically.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(14.dp))
                DialogField(label, { label = it }, "Name", "Stripe", "api_key")
                Spacer(Modifier.height(10.dp))
                DialogField(
                    envVar,
                    { envVar = it.uppercase() },
                    "Environment variable",
                    "STRIPE_API_KEY",
                    "MY_SERVICE_TOKEN",
                )
                Spacer(Modifier.height(10.dp))
                DialogField(value, { value = it }, "Value", "sk_live_…", "the key itself")
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && envVar.isNotBlank() && value.isNotBlank(),
                onClick = { onConfirm(label, envVar, value) },
            ) {
                Text(
                    "Add",
                    color = if (label.isNotBlank() && envVar.isNotBlank() && value.isNotBlank()) {
                        AccentSoft
                    } else {
                        TextMuted
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}

@Composable
private fun DialogField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supporting: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp, color = TextMuted) },
        supportingText = { Text(supporting, fontSize = 11.sp, color = TextMuted) },
        visualTransformation = PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Border,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentSoft,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Shows only the host, so a long token URL does not wrap the row onto three lines. */
private fun shortHost(url: String): String =
    url.removePrefix("https://").removePrefix("http://").substringBefore('/')

/**
 * Opens a provider's token page in the browser.
 *
 * Deliberately *not* the in-app WebView: signing in to GitHub or Vercel means typing a password,
 * and this app has no business being the thing holding it. A short-lived ACTION_VIEW hand-off to
 * the system browser keeps that outside Cody's process entirely.
 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}