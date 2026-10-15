package com.termux.app.chat

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Records mono 16 kHz PCM straight to a WAV file — the exact format `whisper.cpp` expects.
 *
 * `MediaRecorder` would be less code but it only emits compressed container formats, and converting
 * them needs ffmpeg. `AudioRecord` plus a hand-written WAV header is about forty lines and has no
 * dependency, which matters inside proot where installing ffmpeg is a whole provisioning step.
 */
class PcmRecorder(val output: File) {

    private companion object {
        const val TAG = "PcmRecorder"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        /** Whisper wants 16 kHz; anything below 16 kHz destroys intelligibility on technical speech. */
        const val MAX_SECONDS = 120
    }

    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var recording = false
    private var bytesWritten = 0L

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
        if (minBuffer <= 0) {
            Log.w(TAG, "No usable buffer size for 16 kHz mono PCM")
            return false
        }
        val bufferSize = minBuffer * 2
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL_MASK, ENCODING, bufferSize)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not open the microphone", t)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord did not initialise")
            runCatching { rec.release() }
            return false
        }

        recorder = rec
        bytesWritten = 0L
        recording = true
        rec.startRecording()

        thread = Thread({
            val chunk = ByteArray(bufferSize)
            var out: RandomAccessFile? = null
            try {
                out = RandomAccessFile(output, "rw")
                out.setLength(0)
                writeWavHeader(out, 0)   // placeholder, rewritten with the real size on stop
                while (recording) {
                    val read = rec.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        out.write(chunk, 0, read)
                        bytesWritten += read
                        if (bytesWritten >= SAMPLE_RATE.toLong() * 2 * MAX_SECONDS) break
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Recording failed", t)
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
                recorder = null
                recording = false
                runCatching {
                    out?.let {
                        it.seek(0)
                        writeWavHeader(it, bytesWritten)
                        it.close()
                    }
                }
                if (bytesWritten == 0L) runCatching { output.delete() }
            }
        }, "pcm-recorder").apply { start() }
        return true
    }

    /** Stops and blocks until the WAV is complete, so the caller can transcribe a finished file. */
    fun stop() {
        recording = false
        runCatching { recorder?.stop() }
        thread?.join(2_000)
        thread = null
    }

    val hasAudio: Boolean get() = bytesWritten > 0

    private fun writeWavHeader(out: RandomAccessFile, dataLength: Long) {
        val byteRate = SAMPLE_RATE * 2 // mono, 16-bit
        val header = ByteArray(44)
        var i = 0
        fun ascii(s: String) { for (c in s) header[i++] = c.code.toByte() }
        fun le32(v: Long) {
            header[i++] = (v and 0xff).toByte()
            header[i++] = ((v shr 8) and 0xff).toByte()
            header[i++] = ((v shr 16) and 0xff).toByte()
            header[i++] = ((v shr 24) and 0xff).toByte()
        }
        fun le16(v: Int) {
            header[i++] = (v and 0xff).toByte()
            header[i++] = ((v shr 8) and 0xff).toByte()
        }

        ascii("RIFF"); le32(36 + dataLength); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)          // PCM, mono
        le32(SAMPLE_RATE.toLong()); le32(byteRate.toLong())
        le16(2); le16(16)
        ascii("data"); le32(dataLength)
        out.write(header)
    }
}
