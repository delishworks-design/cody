package com.termux.app.install

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Unpacks the files bundled in the APK that the shell side needs.
 *
 * Two trees, because they have different shapes:
 *  - `assets/provision/` — a flat list of shell scripts, executed by [EnvironmentProvisioner]
 *    and by the chat feature (`serve.sh`, to start the opencode HTTP server).
 *  - `assets/skills/` — a nested tree of `SKILL.md` files, one directory per skill.
 *
 * The nested one cannot reuse the flat loop: `AssetManager.list` returns subdirectory names as
 * plain strings, so opening one as a file fails. Hence [copyTree].
 */
object ProvisionAssets {
    private const val TAG = "ProvisionAssets"
    const val ASSET_DIR = "provision"
    const val SKILL_ASSET_DIR = "skills"
    const val DIR_NAME = ".opencode-provision"

    fun dir(): File = File(TermuxPaths.HOME, DIR_NAME)

    /** Where the bundled skills land, on the Termux side. The step script copies them into Debian. */
    fun skillsDir(): File = File(dir(), SKILL_ASSET_DIR)

    /** Copies every script and skill out of the APK, overwriting stale copies. */
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
                copyFile(context, "$ASSET_DIR/$name", File(dir, name), executable = true)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to unpack $name", t)
            }
        }

        copyTree(context, SKILL_ASSET_DIR, skillsDir())

        return dir
    }

    /** [ensureAll] followed by the path of a single script. */
    fun script(context: Context, name: String): File {
        ensureAll(context)
        return File(dir(), name)
    }

    /**
     * Recursively copies an asset directory onto disk.
     *
     * The destination is cleared first. Skills are replaced wholesale rather than merged, so a
     * skill deleted from the app also disappears from the device instead of lingering as a stale
     * copy the agent would keep loading.
     */
    private fun copyTree(context: Context, assetPath: String, destination: File) {
        try {
            val children = context.assets.list(assetPath) ?: emptyArray()
            destination.deleteRecursively()
            if (!destination.isDirectory && !destination.mkdirs()) {
                Log.w(TAG, "Could not create $destination")
                return
            }
            for (child in children) {
                val childPath = "$assetPath/$child"
                val target = File(destination, child)
                // Distinguish a file from a directory by trying to open it: AssetManager.list
                // returns an array for both (empty for a file), so a length check would misclassify.
                val isFile = try {
                    context.assets.open(childPath).close()
                    true
                } catch (t: Throwable) {
                    false
                }
                if (isFile) {
                    copyFile(context, childPath, target, executable = false)
                } else {
                    copyTree(context, childPath, target)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to unpack $assetPath", t)
        }
    }

    private fun copyFile(context: Context, assetPath: String, target: File, executable: Boolean) {
        try {
            target.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (executable) target.setExecutable(true, true)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to unpack $assetPath", t)
        }
    }
}