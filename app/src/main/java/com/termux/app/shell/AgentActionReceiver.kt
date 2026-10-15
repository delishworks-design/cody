package com.termux.app.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.termux.app.chat.OpenCodeRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the Approve / Reject buttons on the permission and question notifications, and restarts
 * the service after a reboot.
 *
 * The point of answering here rather than forcing the user into the app is that the agent is
 * *blocked* — the reply does not continue until it is answered. A notification the user has to open
 * the app to deal with is one they will miss, and a missed one is a conversation that hangs forever.
 */
class AgentActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AgentActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // A proot container does not survive a reboot, but a prompt that was in flight does.
                Log.i(TAG, "Boot completed; restarting the service to resume any pending work")
                runCatching {
                    val service = Intent(context, OpenCodeService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(service)
                    } else {
                        context.startService(service)
                    }
                }.onFailure { Log.w(TAG, "Could not restart the service after boot", it) }
            }

            AgentNotifications.ACTION_ALLOW_ONCE,
            AgentNotifications.ACTION_ALLOW_ALWAYS,
            AgentNotifications.ACTION_REJECT -> {
                // `reject` is only meaningful for a permission here; a question is skipped by its
                // own button, which routes to the same intent and is told apart by the runtime.
                val response = when (intent.action) {
                    AgentNotifications.ACTION_ALLOW_ONCE -> "once"
                    AgentNotifications.ACTION_ALLOW_ALWAYS -> "always"
                    else -> "reject"
                }
                apply(context) { OpenCodeRuntime.replyPermissionFromNotification(response) }
            }

            AgentNotifications.ACTION_ANSWER ->
                apply(context) { OpenCodeRuntime.answerPendingQuestion() }

            else -> Unit
        }
    }

    /**
     * Runs [action] off the main thread.
     *
     * The pending prompt is resolved at the moment of the tap: the user may have moved on, and
     * answering the wrong one would silently approve something they never saw.
     */
    private inline fun apply(context: Context, crossinline action: () -> Unit) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                action()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to apply the notification action", t)
            } finally {
                result.finish()
            }
        }
    }
}
