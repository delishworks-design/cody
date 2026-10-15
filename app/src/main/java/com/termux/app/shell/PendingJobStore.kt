package com.termux.app.shell

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * The prompt that is currently in flight, written to disk before the request goes out.
 *
 * Without this, killing the app loses the work outright: the reply keeps generating inside the
 * container, but there is no record that we ever asked, so nothing can pick it up afterwards and the
 * answer is never shown.
 *
 * What survives, and what does not:
 *  - **App swiped away / process killed** — `START_STICKY` brings the service back, the process
 *    restarts, and this file lets the send be replayed. The model may well be asked twice; opencode
 *    dedupes poorly here, so the resume is deliberately conservative (see [PendingJob]).
 *  - **Phone rebooted** — a `BOOT_COMPLETED` receiver restarts the service, which re-reads this.
 *  - **Battery pulled mid-request** — nothing can save that, and we do not pretend otherwise.
 */
object PendingJobStore {
    private const val TAG = "PendingJobStore"
    private const val FILE = ".opencode-pending.json"

    /** A prompt that was sent and has not been confirmed finished. */
    data class PendingJob(
        val sessionId: String?,
        val text: String,
        val imagePaths: List<String>,
        val fileRefs: List<String>,
        val modelKey: String?,
        val agent: String?,
        val startedAt: Long,
    )

    private fun file(context: Context): File =
        File(com.termux.app.install.TermuxPaths.HOME, FILE)

    fun save(context: Context, job: PendingJob) {
        runCatching {
            val json = JSONObject()
                .put("sessionId", job.sessionId ?: JSONObject.NULL)
                .put("text", job.text)
                .put("imagePaths", org.json.JSONArray(job.imagePaths))
                .put("fileRefs", org.json.JSONArray(job.fileRefs))
                .put("modelKey", job.modelKey ?: JSONObject.NULL)
                .put("agent", job.agent ?: JSONObject.NULL)
                .put("startedAt", job.startedAt)
            file(context).writeText(json.toString())
            Log.i(TAG, "Persisted pending job (${job.text.take(40)})")
        }.onFailure { Log.w(TAG, "Could not persist pending job", it) }
    }

    /** Reads and clears the pending job, or returns null when there is nothing to resume. */
    fun take(context: Context): PendingJob? {
        val f = file(context)
        if (!f.isFile) return null
        return runCatching {
            val json = JSONObject(f.readText())
            f.delete()
            fun strings(key: String): List<String> {
                val array = json.optJSONArray(key) ?: return emptyList()
                return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
            }
            PendingJob(
                sessionId = json.optString("sessionId").takeIf { it.isNotEmpty() },
                text = json.optString("text"),
                imagePaths = strings("imagePaths"),
                fileRefs = strings("fileRefs"),
                modelKey = json.optString("modelKey").takeIf { it.isNotEmpty() },
                agent = json.optString("agent").takeIf { it.isNotEmpty() },
                startedAt = json.optLong("startedAt"),
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** Peek without consuming — used to decide whether the reply actually needs surfacing. */
    fun exists(context: Context): Boolean = file(context).isFile
}
