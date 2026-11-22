package com.termux.app.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * Receives the result of an APK install started by [AppUpdater.install].
 *
 * The system delivers this to a PendingIntent rather than returning a result from commit(), so
 * without it "Install" would report success even when signature checks or a bad APK made the
 * installer reject the package.
 */
class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "Cody updated", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                context,
                "Update failed: ${detail ?: "installer error $status"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
