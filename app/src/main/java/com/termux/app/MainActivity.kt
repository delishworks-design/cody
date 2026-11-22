package com.termux.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.termux.app.chat.OpenCodeApi
import com.termux.app.chat.OpenCodeRuntime
import com.termux.app.chat.OpenCodeWeb
import com.termux.app.features.AppUpdater
import com.termux.app.features.RemoteDevices
import com.termux.app.install.EnvironmentProvisioner
import com.termux.app.install.TermuxPaths
import com.termux.app.terminal.TerminalActivity
import com.termux.app.ui.AppViewModel
import com.termux.app.ui.screens.ChatScreen
import com.termux.app.ui.screens.FeatureHubScreen
import com.termux.app.ui.screens.SettingsScreen
import com.termux.app.ui.screens.SetupScreen
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.CodyTheme
import com.termux.app.ui.theme.TextMuted
import com.termux.app.web.WebViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Set by the notification intents so tapping "OpenCode finished" lands on the chat rather
         * than the home screen — the reply is the entire reason the notification exists.
         */
        const val EXTRA_OPEN_CHAT = "open_chat"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the screen on for as long as the chat is in front of the user.
        //
        // FLAG_KEEP_SCREEN_ON rather than a PowerManager.WakeLock: it needs no permission, Android
        // clears it automatically the moment the window loses focus — so it cannot outlive the app
        // or keep the CPU alive in the background the way an unmanaged lock would — and it is the
        // documented answer for "this screen is being read, not left idle".
        //
        // The 15-minute screen timeout is actively harmful here. A reply takes minutes to stream
        // and Whisper adds several more before dictation resolves; the display going to sleep
        // mid-reply is indistinguishable from the app hanging, which is exactly the confusion this
        // was added to remove.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestNotificationPermissionIfNeeded()

        var openChatOnStart = intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true

        setContent {
            CodyTheme {
                val navController = rememberNavController()
                val viewModel: AppViewModel = viewModel()
                val scope = rememberCoroutineScope()
                var pendingDevice by remember { mutableStateOf<RemoteDevices.Device?>(null) }
                var updateRelease by remember { mutableStateOf<AppUpdater.Release?>(null) }
                var updateProgress by remember { mutableStateOf(-1) }

                // Check at most once a day. Marking before the network call is deliberate: a slow or
                // offline GitHub must not make every launch fire another request.
                LaunchedEffect(Unit) {
                    if (AppUpdater.shouldAutoCheck(this@MainActivity)) {
                        AppUpdater.markChecked(this@MainActivity)
                        val found = withContext(Dispatchers.IO) { AppUpdater.check(this@MainActivity) }
                        if (found != null && AppUpdater.isNewer(found, this@MainActivity)) {
                            updateRelease = found
                        }
                    }
                }

                // The chat IS the app. There is no home screen and no onboarding: a fresh install
                // opens straight into the transcript, where the initialising state stands in until
                // the environment is ready. A separate setup destination stays reachable only when
                // provisioning actually fails.
                LaunchedEffect(Unit) {
                    if (openChatOnStart) openChatOnStart = false
                    // A scanned QR opens this activity as a cody://connect deep link. Keep it as an
                    // explicit offer: accepting an address from a camera app is the user's decision,
                    // not something to do silently in the background.
                    intent?.data?.let { uri -> RemoteDevices.parseLink(uri)?.let { pendingDevice = it } }
                }

                pendingDevice?.let { offer ->
                    AlertDialog(
                        onDismissRequest = { pendingDevice = null },
                        title = { Text("Connect to ${offer.name}?") },
                        text = {
                            Text(
                                "This will send chat requests and prompts to ${offer.url}. " +
                                    "Only accept a QR from a computer you trust on this network.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingDevice = null
                                scope.launch {
                                    val reachable = withContext(Dispatchers.IO) {
                                        OpenCodeApi.probe(offer.url, offer.password)
                                    }
                                    if (!reachable) {
                                        Toast.makeText(
                                            this@MainActivity, "Could not reach ${offer.url}", Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        RemoteDevices.add(this@MainActivity, offer)
                                        RemoteDevices.select(this@MainActivity, offer.id)
                                        OpenCodeRuntime.switchServer(this@MainActivity)
                                    }
                                }
                            }) { Text("Connect") }
                        },
                        dismissButton = { TextButton(onClick = { pendingDevice = null }) { Text("Cancel") } },
                    )
                }

                if (pendingDevice == null) updateRelease?.let { release ->
                    AlertDialog(
                        onDismissRequest = { updateRelease = null },
                        title = { Text("Update available") },
                        text = {
                            Column {
                                Text("Cody ${release.tag} is available.")
                                if (release.notes.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        release.notes.take(600),
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.verticalScroll(rememberScrollState()),
                                    )
                                }
                                if (updateProgress >= 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Downloading $updateProgress%", color = Accent)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                updateRelease = null
                                scope.launch {
                                    val apk = withContext(Dispatchers.IO) {
                                        AppUpdater.download(this@MainActivity, release) { pct ->
                                            updateProgress = pct
                                        }
                                    }
                                    updateProgress = -1
                                    if (apk == null) {
                                        Toast.makeText(
                                            this@MainActivity, "Could not download the update", Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        val started = withContext(Dispatchers.IO) {
                                            AppUpdater.install(this@MainActivity, apk)
                                        }
                                        if (!started) {
                                            Toast.makeText(
                                                this@MainActivity, "Could not start the installer", Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                            }) { Text("Download & install") }
                        },
                        dismissButton = { TextButton(onClick = { updateRelease = null }) { Text("Later") } },
                    )
                }

                NavHost(navController = navController, startDestination = "chat") {
                    composable("chat") {
                        ChatScreen(
                            onOpenSetup = { navController.navigate("setup") },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenTerminal = { openTerminal() },
                            onOpenFeatures = { navController.navigate("features") },
                        )
                    }
                    composable("setup") {
                        SetupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenProviderDashboard = { openProviderSetup() },
                        )
                    }
                    composable("features") {
                        FeatureHubScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    /**
     * Opens opencode's own dashboard so the user can connect a provider without leaving the app.
     *
     * This deliberately does not drop the user into a shell. `opencode auth login` is interactive and
     * the dashboard exposes the same provider settings, so the in-app WebView is both the nicer and
     * the more honest route — there is no command to remember and nothing to mistype.
     *
     * The dashboard needs a few seconds to boot, so readiness is awaited briefly first; past that the
     * WebView's own error/retry state takes over rather than leaving a blank page.
     */
    private fun openProviderSetup() {
        lifecycleScope.launch {
            OpenCodeWeb.awaitReady(applicationContext, attempts = 8)
            WebViewActivity.start(this@MainActivity, OpenCodeWeb.URL, "Connect a provider")
        }
    }

    /**
     * Opens the raw terminal on the opencode TUI.
     *
     * This is the shell the chat itself is built on — the same proot session, entered the same way
     * — so it is the right one to offer first. `TerminalActivity` can also be pointed at the bare
     * Termux or Debian shells via [TerminalActivity.EXTRA_SESSION_TYPE]; only the TUI is wired to
     * the drawer for now, because a drawer carrying three near-identical shell entries is worse
     * than one that starts where you meant to be.
     */
    private fun openTerminal() {
        startActivity(
            Intent(this, TerminalActivity::class.java).apply {
                putExtra(TerminalActivity.EXTRA_SESSION_TYPE, TerminalActivity.SESSION_OPENCODE)
                putExtra(TerminalActivity.EXTRA_TITLE, "opencode")
            },
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
