package com.aicompanion.feature.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aicompanion.domain.model.VoiceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TTSEngineType { SYSTEM, IFlyTEK, ALIYUN, SHERPA_ONNX }

data class TTSState(
    val isSpeaking: Boolean = false,
    val engine: TTSEngineType = TTSEngineType.SYSTEM,
    val utteranceId: String? = null
)

sealed class TTSEvent {
    object StartSpeaking : TTSEvent()
    object DoneSpeaking : TTSEvent()
    data class Error(val message: String) : TTSEvent()
}

@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private val eventChannel = Channel<TTSEvent>(Channel.BUFFERED)
    val events: Flow<TTSEvent> = eventChannel.receiveAsFlow()

    var state = TTSState()
        private set

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        state = state.copy(isSpeaking = true, utteranceId = utteranceId)
                        eventChannel.trySend(TTSEvent.StartSpeaking)
                    }
                    override fun onDone(utteranceId: String?) {
                        state = state.copy(isSpeaking = false, utteranceId = null)
                        eventChannel.trySend(TTSEvent.DoneSpeaking)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        state = state.copy(isSpeaking = false)
                        eventChannel.trySend(TTSEvent.Error("TTS error"))
                    }
                })
            }
        }
    }

    fun speak(text: String, profile: VoiceProfile? = null) {
        tts?.let { ttsEngine ->
            if (profile != null) {
                ttsEngine.setPitch(profile.pitch)
                ttsEngine.setSpeechRate(profile.speed)
            }
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
            state = state.copy(isSpeaking = true)
        }
    }

    fun stop() {
        tts?.stop()
        state = state.copy(isSpeaking = false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    fun setPitch(pitch: Float) { tts?.setPitch(pitch) }
    fun setSpeed(speed: Float) { tts?.setSpeechRate(speed) }

    fun synthesizeToFile(text: String, profile: VoiceProfile, outputFile: File): Boolean {
        return try {
            val tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    tts?.setPitch(profile.pitch)
                    tts?.setSpeechRate(profile.speed)
                }
            }
            tts?.synthesizeToFile(text, null, outputFile, "synth_1") == TextToSpeech.SUCCESS
        } catch (_: Exception) {
            false
        }
    }
}
