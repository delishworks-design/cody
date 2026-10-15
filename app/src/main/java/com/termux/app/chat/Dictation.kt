package com.termux.app.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Dictation into the composer.
 *
 * On API 31+ this asks for the *on-device* recogniser (`createOnDeviceSpeechRecognizer`), which is
 * the platform's locally-hosted speech model — audio never leaves the phone and it works with no
 * data connection. Below 31 it falls back to whatever the system recogniser is, which may be a
 * network one.
 *
 * This is deliberately the platform recogniser rather than a bundled Whisper binary: shipping a
 * Whisper model means a ~75 MB download plus a native inference runtime inside the proot container,
 * which is a very different trade from "dictation just works". The agent inside the container can
 * run Whisper on a file if the user ever wants that, but doing it inline here would make the app
 * 80 MB heavier and markedly slower to first use.
 */
object Dictation {
    private const val TAG = "Dictation"

    const val PERMISSION = Manifest.permission.RECORD_AUDIO

    private var recognizer: SpeechRecognizer? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** True when some recogniser exists at all — emulators and AOSP builds often have none. */
    fun isAvailable(context: Context): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    /**
     * Starts listening. [onPartial] fires with the running transcript so the user can see it land;
     * [onFinal] fires once with the completed text. Errors are reported rather than swallowed so the
     * UI can say what went wrong instead of the button silently doing nothing.
     */
    fun start(
        context: Context,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit,
        onEnd: () -> Unit,
    ) {
        stop()
        val recognizer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not create a recogniser", t)
            onError("Speech recognition is unavailable on this device.")
            onEnd()
            return
        }
        this.recognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = onReady()

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                onEnd()
                // 7/8/9 are the common "noisy room, no network, no match" cases; a message would be
                // more alarming than useful there, so they are passed through as silence.
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onFinal("")
                } else {
                    onError(describe(error))
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                onFinal(text)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotEmpty()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.startListening(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "startListening failed", t)
            onError("Could not start listening.")
            stop()
            onEnd()
        }
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.stopListening() }
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Could not read the microphone."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "On-device speech recognition needs a working recogniser."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy. Try again."
        SpeechRecognizer.ERROR_SERVER -> "The speech service failed."
        else -> "Speech recognition failed."
    }
}
