package com.termux.app.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.termux.app.MainActivity
import com.termux.app.install.TermuxPaths
import com.termux.app.terminal.OpenCodeTerminalSessionClient
import com.termux.terminal.TerminalSession
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Foreground service that owns the live [TerminalSession]s so they survive configuration changes and
 * the activity going to the background. The terminal view binds to this service and attaches to the
 * sessions it exposes.
 */
class OpenCodeService : Service() {

    companion object {
        private const val TAG = "OpenCodeService"
        private const val CHANNEL_ID = "opencode_session"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.termux.app.action.STOP"

        /**
         * Shown while the chat server is up but no terminal session exists, so the notification
         * explains what is actually being kept alive instead of staying on "Getting ready…".
         */
        @Volatile
        var statusText: String? = null

        /** Refreshes the standing notification from anywhere in the process. */
        fun publishStatus(context: Context, text: String) {
            statusText = text
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(context, 0))
            }
        }

        private fun buildNotification(context: Context, count: Int): Notification {
            val contentIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val caption = when {
                count > 0 -> "$count terminal session(s) running"
                statusText != null -> statusText.orEmpty()
                else -> "Getting ready…"
            }
            return builder
                .setContentTitle("OpenCode")
                .setContentText(caption)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build()
        }
    }

    inner class LocalBinder : Binder() {
        val service: OpenCodeService get() = this@OpenCodeService
    }

    private val binder = LocalBinder()
    private val sessions = CopyOnWriteArrayList<TerminalSession>()

    val sessionClient = OpenCodeTerminalSessionClient(this)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AgentNotifications.ensureChannel(this)
        startForeground(NOTIFICATION_ID, buildNotification(this, 0))
        // Android re-creates this service after killing the process (START_STICKY), and after a
        // reboot via AgentActionReceiver. Either way this is the one place that reliably runs, so
        // the resume of any interrupted prompt is kicked off from here rather than from the UI.
        com.termux.app.chat.OpenCodeRuntime.resumePending(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "onDestroy, finishing ${sessions.size} session(s)")
        for (session in sessions) {
            session.finishIfRunning()
        }
        sessions.clear()
        super.onDestroy()
    }

    fun getSessions(): List<TerminalSession> = sessions.toList()

    /**
     * Creates a new terminal session.
     *
     * @param executable null launches a login shell from the Termux prefix (the default, and what
     *   opencode users expect).
     */
    fun createSession(
        executable: String? = null,
        arguments: Array<String>? = null,
        workingDirectory: String? = TermuxPaths.HOME,
        sessionName: String? = null,
    ): TerminalSession {
        val environment = TermuxShellEnvironment.build(workingDirectory ?: TermuxPaths.HOME)
        val shellPath = executable ?: TermuxPaths.BASH
        // The Termux JNI uses execvp(cmd, argv): argv must be non-null, and argv[0] is the process
        // name. A leading '-' marks a login shell, which makes bash source /etc/profile and the
        // Termux environment. Using bash directly (rather than the `login` helper) is the most
        // reliable way to get an interactive shell in the embedded rootfs.
        val argv = when {
            arguments != null -> arguments
            executable == null -> arrayOf("-bash")
            else -> arrayOf(shellPath.substringAfterLast('/'))
        }
        val session = TerminalSession(shellPath, workingDirectory, argv, environment, null, sessionClient)
        session.mSessionName = sessionName
        sessions.add(session)
        Log.i(TAG, "Created session ${session.mHandle} (name=$sessionName)")
        updateNotification(sessions.size)
        return session
    }

    fun removeSession(session: TerminalSession) {
        sessions.remove(session)
        session.finishIfRunning()
        if (sessions.isEmpty()) {
            updateNotification(0)
        } else {
            updateNotification(sessions.size)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenCode session",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps your OpenCode terminal session running." }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(count: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(this, count))
    }
}
