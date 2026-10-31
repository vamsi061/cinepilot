package com.cinepilot.app.domain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.cinepilot.app.data.models.GeminiIntentResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Captures speech with the platform recogniser and turns the transcript into an intent.
 *
 * The transcript-to-intent step is delegated to [GeminiClient] and [IntentParser], so
 * this class holds no prompt or model-specific logic. Callbacks are delivered on the
 * main thread.
 */
class VoiceProcessor(
    context: Context,
    private val geminiClient: GeminiClient,
) {

    data class Callbacks(
        val onListening: () -> Unit = {},
        val onProcessing: () -> Unit = {},
        val onTranscript: (String) -> Unit = {},
        val onIntent: (GeminiIntentResponse) -> Unit = {},
        val onError: (String) -> Unit = {},
    )

    private companion object {
        const val TAG = "VoiceProcessor"
    }

    /** One recogniser per processor instance; created on the main thread. */
    private val speechRecognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context.applicationContext)) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } else {
            null
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callbacks = Callbacks()

    fun setCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
    }

    init {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                callbacks.onError(errorText(error))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                if (transcript.isEmpty()) {
                    callbacks.onError("No speech recognised")
                    return
                }
                callbacks.onTranscript(transcript)
                interpret(transcript)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun isAvailable(): Boolean = speechRecognizer != null

    fun startListening() {
        if (speechRecognizer == null) {
            callbacks.onError("Speech recognition is not available on this device")
            return
        }
        callbacks.onListening()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private fun interpret(transcript: String) {
        callbacks.onProcessing()
        scope.launch {
            val intent = try {
                val raw = geminiClient.classifyIntent(transcript)
                IntentParser.parse(raw, transcript)
            } catch (e: Exception) {
                Log.w(TAG, "Intent classification failed", e)
                callbacks.onError("Could not understand the command: ${e.message}")
                return@launch
            }
            callbacks.onIntent(intent)
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        scope.cancel()
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech error ($code)"
    }
}