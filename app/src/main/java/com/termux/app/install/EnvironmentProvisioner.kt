package com.termux.app.install

import android.content.Context
import android.util.Log
import com.termux.app.shell.ShellRunner
import com.termux.app.shell.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Provisions the sandbox that opencode actually runs in:
 *
 *   Termux bootstrap  ->  proot-distro  ->  Debian rootfs  ->  opencode
 *
 * The Termux prefix provides `proot-distro` (a Termux package) which downloads and manages a real
 * Debian root filesystem (with glibc). opencode is then installed inside that Debian container,
 * which is what opencode's official Linux builds expect. Everything runs without root.
 *
 * The steps themselves live as plain shell scripts under `assets/provision/` and are unpacked to
 * `$HOME/.opencode-provision/` before running — keeping the setup logic in real `.sh` files rather
 * than escaped string literals. They are deliberately written to be idempotent so setup can be
 * re-run after a partial failure (for example once network access is fixed).
 *
 * Step 0 additionally mirrors the Android trust store into the Termux CA bundle so HTTPS works on
 * corporate networks that intercept TLS (see [TlsTrustSync]).
 */
class EnvironmentProvisioner(private val context: Context) {

    companion object {
        private const val TAG = "EnvironmentProvisioner"
        private const val PREFS = "opencode_env"
        private const val KEY_PROVISIONED = "provisioned"

        /**
         * How many provisioning steps this device has already been *offered*.
         *
         * This exists because `provisioned` alone could not tell "this environment is ready" from
         * "this environment is ready for the scripts this build shipped". Steps 09 and 10 were added
         * after the device was already provisioned, so the flag was true, [AppViewModel] returned
         * early, and the two scripts never ran — Whisper was silently never installed. Recording the
         * count lets an app update notice it ships steps the device has not seen and re-run them.
         *
         * Attempted, not succeeded: an optional step that fails (a blocked download, say) must not
         * make every launch retry forever.
         */
        private const val KEY_STEPS_OFFERED = "steps_offered"

        /** Bundled script (in `assets/provision/`) paired with the progress title shown for it. */
        private val SCRIPT_STEPS = listOf(
            "00-tls-sync.sh" to "Syncing TLS certificates",
            "01-apt-update.sh" to "Updating Termux package lists",
            "02-install-tools.sh" to "Installing proot and helper tools",
            "03-install-debian.sh" to "Installing Debian container",
            "04-debian-tls.sh" to "Syncing TLS certificates into Debian",
            "05-install-opencode.sh" to "Installing opencode inside Debian",
            "06-install-tooling.sh" to "Installing gh, Vercel and Supabase CLIs",
            "07-install-blender.sh" to "Installing Blender CLI",
            "08-fix-tls.sh" to "Re-enabling TLS verification",
            "09-workspace-agents-md.sh" to "Teaching the agent about previews",
            "10-install-whisper.sh" to "Installing Whisper for dictation",
            "11-install-skills.sh" to "Installing agent skills",
        )

        /** Script used to decide whether opencode is reachable inside the Debian container. */
        const val VERIFY_SCRIPT = "verify-opencode.sh"

        /**
         * Paths that prove an expensive step already ran, relative to the Debian rootfs.
         *
         * The step counter cannot bootstrap itself. A device provisioned by a build that predates
         * `steps_offered` has no record at all, so the first catch-up run would start at zero and
         * reinstall Debian, re-download opencode and re-download Blender over an environment that is
         * already working — slow, and a real chance to break something that was fine.
         *
         * These are the actual locations, read off a running device rather than assumed. Only the
         * heavy steps are listed: the TLS and apt steps are cheap and are genuinely worth repeating,
         * and `09` is meant to rewrite its file every run.
         */
        private val STEP_SENTINELS = mapOf(
            "03-install-debian.sh" to "etc/debian_version",
            "05-install-opencode.sh" to "root/.opencode/bin/opencode",
            "07-install-blender.sh" to "usr/bin/blender",
        )

        /**
         * Add-ons whose failure must not mark provisioning as failed.
         *
         * opencode itself is verified separately; a flaky npm registry or a `gh` download that the
         * network blocks should leave the app usable rather than reporting a broken setup.
         */
        private val OPTIONAL_STEPS =
            setOf("06-install-tooling.sh", "07-install-blender.sh", "10-install-whisper.sh")

        /** Command used to launch opencode interactively inside the Debian container. */
        const val OPENCODE_LAUNCH_COMMAND =
            "export PATH=\$PATH:\$HOME/.opencode/bin:/usr/local/bin; exec opencode"

        /** Full command typed into a Termux login shell to open opencode in the terminal. */
        const val OPENCODE_TERMINAL_COMMAND =
            "proot-distro login debian --shared-tmp -- /bin/bash -lc " +
                    "'export PATH=\$PATH:\$HOME/.opencode/bin:\$HOME/.local/bin:/usr/local/bin; exec opencode'"

        /** Full command typed into a Termux login shell to open a Debian bash login shell. */
        const val DEBIAN_TERMINAL_COMMAND =
            "proot-distro login debian --shared-tmp"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isProvisioned(): Boolean = prefs.getBoolean(KEY_PROVISIONED, false)

    /**
     * True when this build ships provisioning steps the device has never been offered.
     *
     * Drives the "app updated, so catch the environment up" path. Without it, adding a provisioning
     * script to a new build has no effect on any device that was set up by an older one.
     */
    fun hasPendingSteps(): Boolean = prefs.getInt(KEY_STEPS_OFFERED, 0) < SCRIPT_STEPS.size

    /** Index of the first step this device has never been offered; [SCRIPT_STEPS.size] when none. */
    fun stepsOffered(): Int = prefs.getInt(KEY_STEPS_OFFERED, 0)

    fun setProvisioned(value: Boolean) = prefs.edit().putBoolean(KEY_PROVISIONED, value).apply()

    data class Step(val title: String, val command: List<String>)

    private val env: Array<String> get() = TermuxShellEnvironment.build(TermuxPaths.HOME)

    /** A single human-readable provisioning step with a title and streamed output lines. */
    data class StepUpdate(
        val index: Int,
        val total: Int,
        val title: String,
        val line: String?,
        val done: Boolean,
        val success: Boolean,
    )

    private fun scriptCommand(scriptDir: File, name: String): List<String> =
        listOf(TermuxPaths.BASH, File(scriptDir, name).absolutePath)

    /** Unpacks every bundled script; see [ProvisionAssets]. */
    private fun ensureScripts(): File = ProvisionAssets.ensureAll(context)

    /**
     * Runs all provisioning steps sequentially. [onUpdate] is invoked for every streamed output line
     * and once when each step completes. Returns true only if every step succeeded *and* opencode
     * was verified inside Debian.
     */
    suspend fun provision(onUpdate: (StepUpdate) -> Unit, fromIndex: Int = 0): Boolean = withContext(Dispatchers.IO) {
        // Mirror Android's trust store (system + any corporate/user CA) before anything downloads.
        TlsTrustSync.installIntoTermux()
        val scriptDir = ensureScripts()

        val steps = SCRIPT_STEPS.map { (file, title) ->
            Step(title, scriptCommand(scriptDir, file))
        }

        val total = steps.size
        var allOk = true

        // Steps already offered on an earlier build are skipped rather than repeated. Re-running all
        // of them to add one new script would mean reinstalling Debian and Blender on a phone whose
        // environment is already working, which is slow and is a chance to break something that was
        // fine. A fresh install passes 0 here and runs everything.
        steps.forEachIndexed { index, step ->
            val script = SCRIPT_STEPS[index].first
            val sentinel = STEP_SENTINELS[script]
            // Two independent reasons to skip: the device already ran it in an earlier pass, or it
            // is a heavy step whose result is demonstrably still on disk.
            val alreadySatisfied = index < fromIndex ||
                (sentinel != null && containerHas(sentinel))
            if (alreadySatisfied) {
                val why = if (index < fromIndex) "already done" else "already installed"
                onUpdate(StepUpdate(index, total, step.title, why, true, true))
                prefs.edit().putInt(KEY_STEPS_OFFERED, index + 1).apply()
                return@forEachIndexed
            }
            onUpdate(StepUpdate(index, total, step.title, null, false, true))
            val exit = ShellRunner.run(step.command, env, TermuxPaths.HOME) { line ->
                Log.i(TAG, "[${step.title}] $line")
                onUpdate(StepUpdate(index, total, step.title, line, false, true))
            }
            val ok = exit == 0
            if (!ok && SCRIPT_STEPS[index].first !in OPTIONAL_STEPS) allOk = false
            onUpdate(StepUpdate(index, total, step.title, null, true, ok))
            // Recorded per step, not once at the end: a crash or a force-stop halfway through must
            // not make this device look like it was offered the steps it never reached.
            prefs.edit().putInt(KEY_STEPS_OFFERED, index + 1).apply()
        }

        val verified = verifyOpencodeAt(scriptDir)
        if (verified) setProvisioned(true)
        allOk && verified
    }

    /**
     * True when a path exists inside the Debian container.
     *
     * Reads the rootfs straight off the Android filesystem rather than shelling into the container:
     * the container paths are just a prefix away from `$PREFIX`, and a [File] check costs nothing
     * next to starting proot just to ask whether a file is there. A missing rootfs reads as "not
     * installed", which is the safe answer — it makes the step run rather than silently skip.
     */
    private fun containerHas(relativePath: String): Boolean =
        File(TermuxPaths.DEBIAN_CONTAINER_ROOTFS + "/" + relativePath).exists()

    /** Returns true if an opencode executable can be found inside the Debian container. */
    suspend fun verifyOpencode(): Boolean = withContext(Dispatchers.IO) {
        verifyOpencodeAt(ensureScripts())
    }

    private suspend fun verifyOpencodeAt(scriptDir: File): Boolean {
        val output = StringBuilder()
        val exit = ShellRunner.run(scriptCommand(scriptDir, VERIFY_SCRIPT), env, TermuxPaths.HOME) {
            line -> output.append(line).append('\n')
        }
        val text = output.toString()
        Log.i(TAG, "verifyOpencode exit=$exit output=${text.trim()}")
        return exit == 0 && !text.contains("OPENCODE_MISSING")
    }
}
