package com.termux.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.app.install.EnvironmentProvisioner
import com.termux.app.install.EnvironmentProvisioner.StepUpdate
import com.termux.app.install.TermuxInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SetupState {
    data object Checking : SetupState
    data object NeedsSetup : SetupState
    data class Working(val title: String, val detail: String) : SetupState
    data object Ready : SetupState
    data class Failed(val message: String) : SetupState
}

data class UiState(
    val setup: SetupState = SetupState.Checking,
    val log: List<String> = emptyList(),
    val stepIndex: Int = 0,
    val stepTotal: Int = 0,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val installer = TermuxInstaller(application)
    private val provisioner = EnvironmentProvisioner(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Provisioning starts by itself. The old flow needed the user to open Setup, press a
        // button and wait — which read as an onboarding wizard this app does not have. On a fresh
        // install the chat simply shows its initialising state until this finishes.
        ensureReady()
    }

    /**
     * Brings the environment up if it is not already, and otherwise just confirms the check.
     *
     * Idempotent: [EnvironmentProvisioner.provision] re-runs the numbered steps, which are written to
     * be safe to repeat, so this is also how an app update picks up newly added steps.
     */
    fun ensureReady() {
        viewModelScope.launch {
            val installed = installer.isInstalled()
            val provisioned = provisioner.isProvisioned()
            // `provisioned` only means "the scripts this device has already been offered all ran".
            // A build that ships a new step must not be waved through, or Whisper and the preview
            // instructions would never reach a device set up by an earlier build.
            val pendingSteps = provisioner.hasPendingSteps()
            val offered = provisioner.stepsOffered()
            if (installed && provisioned && !pendingSteps) {
                _state.update { it.copy(setup = SetupState.Ready) }
                return@launch
            }

            if (pendingSteps) {
                append("Finishing environment setup…")
            } else {
                append("Checking embedded Termux environment…")
            }
            _state.update {
                it.copy(
                    setup = SetupState.Working(
                        if (pendingSteps) "Updating" else "Preparing",
                        if (pendingSteps) "Adding newly bundled steps" else "Extracting Termux rootfs",
                    ),
                )
            }

            // Only extract the rootfs when it is genuinely missing. A catch-up run of newly bundled steps
            // does not need it, and re-extracting ~500MB on every app update would make the app look
            // broken for minutes on a phone.
            val ok = if (installed) true else installer.install { message -> append(message) }
            if (!ok) {
                _state.update {
                    it.copy(setup = SetupState.Failed("Could not install the Termux environment."))
                }
                return@launch
            }

            append("Provisioning Debian container and opencode…")
            val ready = provisioner.provision({ update -> onStep(update) }, fromIndex = offered)
            if (ready) {
                append("Done. opencode is ready.")
                _state.update { it.copy(setup = SetupState.Ready) }
            } else {
                _state.update {
                    it.copy(
                        setup = SetupState.Failed(
                            "opencode could not be installed automatically.",
                        ),
                    )
                }
            }
        }
    }

    /** Re-runs provisioning by hand after a failure. The happy path never needs this. */
    fun startSetup() {
        viewModelScope.launch {
            _state.update { it.copy(setup = SetupState.Working("Setting up", "Starting"), log = emptyList()) }
            // Deliberately from zero: this is the user asking for everything, not for a catch-up. The
            // sentinels still protect the heavy steps, so a deliberate re-run is thorough without
            // re-downloading Debian and Blender over a working environment.
            val ok = if (installer.isInstalled()) true else installer.install { message -> append(message) }
            if (!ok) {
                _state.update {
                    it.copy(setup = SetupState.Failed("Could not install the Termux environment."))
                }
                return@launch
            }
            val ready = provisioner.provision({ update -> onStep(update) }, fromIndex = 0)
            _state.update {
                it.copy(
                    setup = if (ready) SetupState.Ready
                    else SetupState.Failed("opencode could not be installed automatically."),
                )
            }
        }
    }

    private fun onStep(update: StepUpdate) {
        val detail = if (update.done) "${update.title} — ${if (update.success) "done" else "failed"}"
        else update.title
        _state.update {
            it.copy(
                setup = SetupState.Working("Setting up environment", detail),
                stepIndex = update.index + 1,
                stepTotal = update.total,
            )
        }
        update.line?.let { line -> if (line.isNotBlank()) append(line) }
    }

    private fun append(line: String) {
        _state.update { current ->
            val next = (current.log + line).takeLast(400)
            current.copy(log = next)
        }
    }
}
