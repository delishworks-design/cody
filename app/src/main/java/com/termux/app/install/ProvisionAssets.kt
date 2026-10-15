package com.termux.app.install

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Unpacks the shell scripts under `assets/provision/` into `$HOME/.opencode-provision/` so they can
 * be executed by the Termux bash.
 *
 * Everything lives in one place because two consumers need them: [EnvironmentProvisioner] runs the
 * numbered setup steps, and the chat feature runs `serve.sh` to start the opencode HTTP server.
 */
object ProvisionAssets {
    private const val TAG = "ProvisionAssets"
    const val ASSET_DIR = "provision"
    const val DIR_NAME = ".opencode-provision"

    fun dir(): File = File(TermuxPaths.HOME, DIR_NAME)

    /** Copies every script out of the APK, overwriting stale copies, and returns the directory. */
    fun ensureAll(context: Context): File {
        val dir = dir()
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG, "Could not create $dir")
            return dir
        }
        val names = try {
            context.assets.list(ASSET_DIR) ?: emptyArray()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not list $ASSET_DIR", t)
            emptyArray()
        }
        for (name in names) {
            try {
                val out = File(dir, name)
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out.setExecutable(true, true)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to unpack $name", t)
            }
        }
        return dir
    }

    /** [ensureAll] followed by the path of a single script. */
    fun script(context: Context, name: String): File {
        ensureAll(context)
        return File(dir(), name)
    }
}
