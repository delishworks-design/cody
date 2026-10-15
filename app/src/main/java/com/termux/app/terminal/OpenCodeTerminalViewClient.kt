package com.termux.app.terminal

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * Bridges [TerminalView] to the rest of the app: modifier-key state, logging and scale handling.
 */
class OpenCodeTerminalViewClient(
    private val context: Context,
    private val extraKeys: ExtraKeysState,
    private val onScale: (Float) -> Float,
    private val onSingleTap: () -> Unit,
) : TerminalViewClient {

    var terminalView: TerminalView? = null
    var onTitleChanged: ((String) -> Unit)? = null
    private val tag = "TerminalViewClient"

    override fun onScale(scale: Float): Float = onScale.invoke(scale)

    override fun onSingleTapUp(e: MotionEvent) = onSingleTap.invoke()

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {
        // Selection is initiated from the long-press / toolbar instead.
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && extraKeys.shift) {
            extraKeys.shift = false
            session.write("\n".toByteArray(Charsets.UTF_8), 0, 1)
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean {
        terminalView?.startTextSelectionMode(event)
        return true
    }

    override fun readControlKey(): Boolean = extraKeys.control

    override fun readAltKey(): Boolean = extraKeys.alt

    override fun readShiftKey(): Boolean = extraKeys.shift

    override fun readFnKey(): Boolean = extraKeys.fn

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        terminalView?.getCurrentSession()?.title?.let { onTitleChanged?.invoke(it) }
    }

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stack trace", e) }
}
