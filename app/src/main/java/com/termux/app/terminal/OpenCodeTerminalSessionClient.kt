package com.termux.app.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.lang.ref.WeakReference

/**
 * Bridges [TerminalSession] back to the UI. Holds only a weak reference to the active [TerminalView]
 * because sessions can outlive the activity that is currently displaying them.
 */
class OpenCodeTerminalSessionClient(private val context: Context) : TerminalSessionClient {

    var terminalViewRef: WeakReference<TerminalView>? = null
    var onTitleChanged: ((TerminalSession, String?) -> Unit)? = null
    var onSessionFinished: ((TerminalSession) -> Unit)? = null
    var onColorsChanged: (() -> Unit)? = null

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalViewRef?.get()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitleChanged?.invoke(changedSession, changedSession.title)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        onSessionFinished?.invoke(finishedSession)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OpenCode terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount <= 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        if (text.isNotEmpty()) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            session.write(bytes, 0, bytes.size)
        }
    }

    override fun onBell(session: TerminalSession) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {
        }
    }

    override fun onColorsChanged(session: TerminalSession) {
        onColorsChanged?.invoke()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int? = null

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
