package com.termux.app.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.termux.app.MainActivity
import com.termux.app.chat.PermissionPrompt
import com.termux.app.chat.QuestionPrompt

/**
 * The "it needs you" notifications.
 *
 * The foreground notification is deliberately dumb — it says "OpenCode is working" and nothing more.
 * This handles the two cases where the app genuinely cannot proceed without a decision:
 *
 *  - **Finished.** The reply is ready. The user is usually somewhere else by now, so this is the
 *    only way they find out.
 *  - **Blocked.** A permission or a question is waiting. This is the dangerous one: the agent holds
 *    the reply open until answered, so a missed dialog is a permanently stuck conversation. Putting
 *    Approve / Reject (and Answer / Skip) on the notification means the decision can be made without
 *    opening the app.
 *
 * On Android 13+ these need POST_NOTIFICATIONS, which the app already requests.
 */
object AgentNotifications {
    private const val TAG = "AgentNotifications"
    private const val CHANNEL_ALERTS = "opencode_alerts"
    private const val ID_FINISHED = 4301
    private const val ID_PERMISSION = 4302
    private const val ID_QUESTION = 4303

    const val ACTION_ALLOW_ONCE = "com.termux.app.action.ALLOW_ONCE"
    const val ACTION_ALLOW_ALWAYS = "com.termux.app.action.ALLOW_ALWAYS"
    const val ACTION_REJECT = "com.termux.app.action.REJECT"
    const val ACTION_ANSWER = "com.termux.app.action.ANSWER"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            "OpenCode needs you",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "A reply finished, or the agent is waiting for your approval."
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int, promptId: String? = null): PendingIntent {
        val intent = Intent(context, AgentActionReceiver::class.java).apply {
            this.action = action
            if (promptId != null) putExtra(EXTRA_PROMPT_ID, promptId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    const val EXTRA_PROMPT_ID = "prompt_id"

    private fun builder(context: Context, id: Int): Notification.Builder {
        ensureChannel(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ALERTS)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
    }

    /** The reply landed while you were somewhere else. */
    fun notifyFinished(context: Context, sessionId: String?) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(
                ID_FINISHED,
                builder(context, ID_FINISHED)
                    .setContentTitle("OpenCode finished")
                    .setContentText("Tap to read the reply.")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(openAppIntent(context, 1))
                    .setAutoCancel(true)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Could not post the finished notification", it) }
    }

    /**
     * The agent is blocked on an approval.
     *
     * The three actions map onto opencode's own reply values rather than a UI-level decision, so
     * approving from the notification is exactly equivalent to tapping it in the app.
     */
    fun notifyPermission(context: Context, prompt: PermissionPrompt) {
        runCatching {
            val id = prompt.id.hashCode()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(
                ID_PERMISSION,
                builder(context, ID_PERMISSION)
                    .setContentTitle("OpenCode needs approval")
                    .setContentText(prompt.permission.ifBlank { "Run a tool" })
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentIntent(openAppIntent(context, 2))
                    .setAutoCancel(true)
                    .addAction(0, "Allow once", actionIntent(context, ACTION_ALLOW_ONCE, id, prompt.id))
                    .addAction(0, "Always", actionIntent(context, ACTION_ALLOW_ALWAYS, id + 1, prompt.id))
                    .addAction(0, "Reject", actionIntent(context, ACTION_REJECT, id + 2, prompt.id))
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Could not post the permission notification", it) }
    }

    /** The agent is blocked on a question; it cannot continue until this is answered. */
    fun notifyQuestion(context: Context, question: QuestionPrompt) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(
                ID_QUESTION,
                builder(context, ID_QUESTION)
                    .setContentTitle("OpenCode has a question")
                    .setContentText(question.blocks.firstOrNull()?.question ?: "Waiting for an answer")
                    .setStyle(
                        Notification.BigTextStyle().bigText(
                            question.blocks.firstOrNull()?.question ?: "",
                        ),
                    )
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentIntent(openAppIntent(context, 3))
                    .setAutoCancel(true)
                    .addAction(0, "Answer", actionIntent(context, ACTION_ANSWER, question.id.hashCode(), question.id))
                    .addAction(0, "Skip", actionIntent(context, ACTION_REJECT, question.id.hashCode() + 1, question.id))
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Could not post the question notification", it) }
    }

    fun clearAll(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(ID_FINISHED)
            manager.cancel(ID_PERMISSION)
            manager.cancel(ID_QUESTION)
        }
    }
}
