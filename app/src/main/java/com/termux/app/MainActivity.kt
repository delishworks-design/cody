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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.termux.app.chat.OpenCodeWeb
import com.termux.app.install.EnvironmentProvisioner
import com.termux.app.install.TermuxPaths
import com.termux.app.terminal.TerminalActivity
import com.termux.app.ui.AppViewModel
import com.termux.app.ui.screens.ChatScreen
import com.termux.app.ui.screens.SettingsScreen
import com.termux.app.ui.screens.SetupScreen
import com.termux.app.ui.theme.CodyTheme
import com.termux.app.web.WebViewActivity
import kotlinx.coroutines.launch

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

                // The chat IS the app. There is no home screen and no onboarding: a fresh install
                // opens straight into the transcript, where the initialising state stands in until
                // the environment is ready. A separate setup destination stays reachable only when
                // provisioning actually fails.
                LaunchedEffect(Unit) {
                    if (openChatOnStart) openChatOnStart = false
                }

                NavHost(navController = navController, startDestination = "chat") {
                    composable("chat") {
                        ChatScreen(
                            onOpenSetup = { navController.navigate("setup") },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenTerminal = { openTerminal() },
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
