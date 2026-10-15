package com.termux.app.terminal

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.termux.app.R
import com.termux.app.install.TermuxPaths
import com.termux.app.shell.OpenCodeService
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * Full-screen terminal. Hosts a [TerminalView] attached to a session owned by [OpenCodeService],
 * plus a toolbar for extra keys, font sizing and session management.
 */
class TerminalActivity : Activity() {

    companion object {
        const val EXTRA_SESSION_TYPE = "session_type"
        const val EXTRA_TITLE = "title"
        /** A command typed into the session once it starts, e.g. `opencode auth login`. */
        const val EXTRA_INITIAL_COMMAND = "initial_command"

        const val SESSION_OPENCODE = "opencode"
        const val SESSION_SHELL = "termux"
        const val SESSION_DEBIAN = "debian"
    }

    /** Written once the shell is up; null when the terminal was opened without one. */
    private var initialCommand: String? = null

    private lateinit var terminalView: TerminalView
    private lateinit var titleView: TextView
    private val extraKeys = ExtraKeysState()

    private var service: OpenCodeService? = null
    private var bound = false
    private var session: TerminalSession? = null
    private var sessionStarted = false

    private var fontSizeSp = 14f
    private var modifiersRow: LinearLayout? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as OpenCodeService.LocalBinder).service
            bound = true
            startRequestedSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialCommand = intent.getStringExtra(EXTRA_INITIAL_COMMAND)?.trim()?.takeIf { it.isNotEmpty() }
        buildUi()

        val serviceIntent = Intent(this, OpenCodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        // Detach so the service does not redraw a destroyed view.
        service?.sessionClient?.terminalViewRef = null
        // Preserve the session: it keeps running in the background service.
        super.onDestroy()
    }

    private fun startRequestedSession() {
        if (sessionStarted || service == null) return
        sessionStarted = true

        val type = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: SESSION_SHELL
        val termuxService = service ?: return

        val newSession = termuxService.createSession(
            executable = null,
            workingDirectory = TermuxPaths.HOME,
            sessionName = titleFor(type),
        )
        session = newSession
        attach(newSession)

        when (type) {
            SESSION_DEBIAN -> sendRaw("proot-distro login debian --shared-tmp\n")
            SESSION_OPENCODE -> sendRaw(
                "proot-distro login debian --shared-tmp -- /bin/bash -lc " +
                        "'export PATH=\$PATH:\$HOME/.opencode/bin:\$HOME/.local/bin:/usr/local/bin; exec opencode'\n",
            )
            else -> Unit
        }
        titleView.text = titleFor(type)

        // A pending command has to wait for the shell it is meant for: sending it immediately would
        // race the `proot-distro login` line above and be typed into the Termux shell instead.
        initialCommand?.let { command ->
            initialCommand = null
            terminalView.postDelayed({
                sendRaw(command + "\n")
                showKeyboard()
            }, if (type == SESSION_SHELL) 400L else 2_500L)
        }
    }

    private fun titleFor(type: String): String = when (type) {
        SESSION_OPENCODE -> "opencode"
        SESSION_DEBIAN -> "Debian"
        SESSION_SHELL -> "Termux shell"
        else -> "Terminal"
    }

    private fun attach(newSession: TerminalSession) {
        terminalView.setTextSize(fontSizeSp.toInt())
        terminalView.setTypeface(Typeface.MONOSPACE)

        val client = OpenCodeTerminalViewClient(
            context = this,
            extraKeys = extraKeys,
            onScale = { scale ->
                fontSizeSp = (fontSizeSp * scale).coerceIn(8f, 32f)
                terminalView.setTextSize(fontSizeSp.toInt())
                terminalView.onScreenUpdated()
                terminalView.updateSize()
                fontSizeSp
            },
            onSingleTap = { showKeyboard() },
        ).apply {
            terminalView = this@TerminalActivity.terminalView
            onTitleChanged = { title -> if (title.isNotBlank()) titleView.text = title }
        }
        terminalView.setTerminalViewClient(client)

        // The terminal session is owned by the service, so route its text/title/finish callbacks
        // back to this activity's view. Without this the terminal would render no output.
        service?.sessionClient?.let { sessionClient ->
            sessionClient.terminalViewRef = java.lang.ref.WeakReference(terminalView)
            sessionClient.onTitleChanged = { _, title ->
                if (!title.isNullOrBlank()) titleView.text = title
            }
            sessionClient.onSessionFinished = { }
        }

        if (terminalView.attachSession(newSession)) {
            terminalView.requestFocus()
        }
        terminalView.post { showKeyboard() }
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun sendRaw(text: String) {
        val s = session ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        s.write(bytes, 0, bytes.size)
    }

    // ---------------------------------------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------------------------------------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@TerminalActivity, R.color.term_bg))
        }

        root.addView(buildTopBar())

        val terminalContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        terminalView = TerminalView(this, null).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // A programmatically created View is not focusable by default. Without this the
            // terminal never receives hardware key events (only soft-keyboard text would work).
            isFocusable = true
            isFocusableInTouchMode = true
        }
        terminalContainer.addView(terminalView)
        root.addView(
            terminalContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        root.addView(buildExtraKeysBar())
        setContentView(root)
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The window is edge-to-edge; without the status-bar inset the back arrow sits under
            // the clock and cannot be tapped.
            setPadding(dp(8), dp(8) + statusBarInset(), dp(8), dp(8))
            setBackgroundColor(ContextCompat.getColor(this@TerminalActivity, R.color.surface))
        }

        bar.addView(backButton())

        titleView = TextView(this).apply {
            text = "Terminal"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bar.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            },
        )

        bar.addView(iconButton("A-") { adjustFont(-1f) })
        bar.addView(iconButton("A+") { adjustFont(1f) })
        bar.addView(iconButton("\u2328") { showKeyboard() })
        bar.addView(iconButton("Ctrl-C") { sendControl('C') })
        return bar
    }

    /**
     * The top-bar back control.
     *
     * Drawn as a vector rather than the "←" text the other buttons use: the glyph is laid out with
     * the label font, so it drifted off-centre and did not match the arrow icon used by the Compose
     * screens. [VectorDrawable] is available on every release this app supports (minSdk 24).
     */
    private fun backButton(): View {
        val button = ImageButton(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@TerminalActivity, R.drawable.ic_arrow_back))
            // The vector is a dark glyph; tint it to match the white title text beside it.
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        return button.apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(2) }
        }
    }

    /** Height of the status bar, so the top bar can clear it on an edge-to-edge window. */
    private fun statusBarInset(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
        }
    } catch (t: Throwable) {
        0
    }

    private fun adjustFont(delta: Float) {
        fontSizeSp = (fontSizeSp + delta).coerceIn(8f, 32f)
        terminalView.setTextSize(fontSizeSp.toInt())
        terminalView.onScreenUpdated()
        terminalView.updateSize()
    }

    private fun buildExtraKeysBar(): View {
        val scroll = HorizontalScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@TerminalActivity, R.color.surface))
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        modifiersRow = row
        scroll.addView(row)

        row.addView(keyButton("ESC") { sendRaw("\u001b") })
        row.addView(keyButton("TAB") { sendRaw("\t") })
        row.addView(modifierButton("CTRL") { toggleModifier(ModifierKey.CTRL) })
        row.addView(modifierButton("ALT") { toggleModifier(ModifierKey.ALT) })
        row.addView(keyButton("SHIFT") { toggleModifier(ModifierKey.SHIFT) })
        row.addView(keyButton("/") { sendRaw("/") })
        row.addView(keyButton("-") { sendRaw("-") })
        row.addView(keyButton("|") { sendRaw("|") })
        row.addView(keyButton("~") { sendRaw("~") })
        row.addView(keyButton("<") { sendRaw("<") })
        row.addView(keyButton(">") { sendRaw(">") })
        row.addView(keyButton("\u2191") { sendRaw("\u001b[A") })
        row.addView(keyButton("\u2193") { sendRaw("\u001b[B") })
        row.addView(keyButton("\u2192") { sendRaw("\u001b[C") })
        row.addView(keyButton("\u2190") { sendRaw("\u001b[D") })
        row.addView(keyButton("HOME") { sendRaw("\u001b[H") })
        row.addView(keyButton("END") { sendRaw("\u001b[F") })
        row.addView(keyButton("Ctrl+C") { sendControl('C') })
        row.addView(keyButton("Ctrl+D") { sendControl('D') })
        row.addView(keyButton("Ctrl+Z") { sendControl('Z') })

        return scroll
    }

    private enum class ModifierKey { CTRL, ALT, SHIFT }

    private fun toggleModifier(key: ModifierKey) {
        when (key) {
            ModifierKey.CTRL -> extraKeys.control = !extraKeys.control
            ModifierKey.ALT -> extraKeys.alt = !extraKeys.alt
            ModifierKey.SHIFT -> extraKeys.shift = !extraKeys.shift
        }
        refreshModifierStyles()
    }

    private fun refreshModifierStyles() {
        val row = modifiersRow ?: return
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            val label = (child as? Button)?.text?.toString() ?: continue
            val active = when (label) {
                "CTRL" -> extraKeys.control
                "ALT" -> extraKeys.alt
                "SHIFT" -> extraKeys.shift
                else -> false
            }
            val hasState = label == "CTRL" || label == "ALT" || label == "SHIFT"
            if (hasState) {
                child.setBackgroundColor(
                    if (active) ContextCompat.getColor(this, R.color.accent) else Color.parseColor("#1B2130"),
                )
                child.setTextColor(if (active) Color.WHITE else Color.parseColor("#C7CEDB"))
            }
        }
    }

    private fun sendControl(letter: Char) {
        val byte = (letter.uppercaseChar().code - '@'.code).toByte()
        val s = session ?: return
        s.write(byteArrayOf(byte), 0, 1)
    }

    private fun iconButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundColor(Color.TRANSPARENT)
            // Never take focus, otherwise a hardware ENTER/DPAD press would "click" this button
            // instead of being delivered to the terminal.
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener { onClick() }
        }

    private fun keyButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12.5f
            setTextColor(Color.parseColor("#C7CEDB"))
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(Color.parseColor("#1B2130"))
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                onClick()
                showKeyboard()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = dp(6)
            layoutParams = lp
        }

    private fun modifierButton(label: String, onClick: () -> Unit): Button =
        keyButton(label, onClick)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
    ).toInt()
}
