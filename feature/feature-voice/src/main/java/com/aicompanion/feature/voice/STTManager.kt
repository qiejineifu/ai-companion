package com.aicompanion.feature.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

data class STTState(
    val isListening: Boolean = false,
    val partialResult: String = "",
    val finalResult: String = ""
)

sealed class STTEvent {
    data class PartialResult(val text: String) : STTEvent()
    data class FinalResult(val text: String) : STTEvent()
    data class Error(val message: String) : STTEvent()
    object SilenceDetected : STTEvent()
}

@Singleton
class STTManager @Inject constructor(
    @ApplicationContext val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val eventChannel = Channel<STTEvent>(Channel.BUFFERED)
    val events: Flow<STTEvent> = eventChannel.receiveAsFlow()

    var state = STTState()
        private set

    fun initialize() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                state = state.copy(isListening = true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                eventChannel.trySend(STTEvent.SilenceDetected)
            }

            override fun onError(error: Int) {
                state = state.copy(isListening = false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                    else -> "识别错误: $error"
                }
                eventChannel.trySend(STTEvent.Error(msg))
            }

            override fun onResults(results: Bundle?) {
                state = state.copy(isListening = false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                state = state.copy(finalResult = text)
                eventChannel.trySend(STTEvent.FinalResult(text))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                state = state.copy(partialResult = text)
                eventChannel.trySend(STTEvent.PartialResult(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
        speechRecognizer?.startListening(intent)
        state = state.copy(isListening = true, partialResult = "", finalResult = "")
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        state = state.copy(isListening = false)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
