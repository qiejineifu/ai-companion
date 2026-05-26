package com.aicompanion.feature.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aicompanion.domain.model.VoiceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TTSManager"

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
    @ApplicationContext private val context: Context,
    private val sherpaOnnxTTS: SherpaOnnxTTS
) {
    private var tts: TextToSpeech? = null
    private val eventChannel = Channel<TTSEvent>(Channel.BUFFERED)
    val events: Flow<TTSEvent> = eventChannel.receiveAsFlow()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
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

    /** Pre-load a sherpa-onnx model. sid is default speaker ID. */
    fun enableSherpaOnnx(modelDir: String, modelFile: String) {
        scope.launch {
            when (val result = sherpaOnnxTTS.loadModel(modelDir, modelFile)) {
                is com.aicompanion.core.common.Result.Success -> {
                    state = state.copy(engine = TTSEngineType.SHERPA_ONNX)
                    Log.d(TAG, "SherpaOnnx TTS loaded: $modelFile")
                }
                is com.aicompanion.core.common.Result.Error -> {
                    Log.e(TAG, "SherpaOnnx TTS failed: ${result.message}")
                }
            }
        }
    }

    /** Switch back to system TTS */
    fun disableSherpaOnnx() {
        sherpaOnnxTTS.release()
        state = state.copy(engine = TTSEngineType.SYSTEM)
    }

    /** Lazy-load sherpa TTS then speak, auto-release after */
    fun speakLazyLoad(text: String, sid: Int, modelDir: String, modelFile: String) {
        scope.launch {
            // Load model on demand
            when (val result = sherpaOnnxTTS.loadModel(modelDir, modelFile)) {
                is com.aicompanion.core.common.Result.Success -> {
                    state = state.copy(engine = TTSEngineType.SHERPA_ONNX)
                    eventChannel.trySend(TTSEvent.StartSpeaking)
                    sherpaOnnxTTS.speak(text, sid = sid)
                    eventChannel.trySend(TTSEvent.DoneSpeaking)
                    // Release immediately to free memory
                    sherpaOnnxTTS.release()
                    state = state.copy(engine = TTSEngineType.SYSTEM)
                }
                is com.aicompanion.core.common.Result.Error -> {
                    // Fallback to system TTS
                    speak(text)
                }
            }
        }
    }

    /** Speak using already-loaded sherpa-onnx model (no load/release) */
    fun speakLoaded(text: String, sid: Int = 0, speed: Float = 1.0f) {
        if (state.engine != TTSEngineType.SHERPA_ONNX) {
            Log.w(TAG, "speakLoaded: engine not SHERPA_ONNX, falling back to system TTS")
            speak(text)
            return
        }
        scope.launch {
            eventChannel.trySend(TTSEvent.StartSpeaking)
            sherpaOnnxTTS.speak(text, sid = sid, speed = speed)
            eventChannel.trySend(TTSEvent.DoneSpeaking)
        }
    }

    /** Speak using DashScope cloud TTS. Falls back to offline AISHELL on failure. */
    fun speakCloud(text: String, apiKey: String, model: String = "cosyvoice-v1:zhitian_emo") {
        if (apiKey.isBlank()) {
            Log.w(TAG, "speakCloud: empty API key, falling back")
            speak(text)
            return
        }
        scope.launch {
            Log.d(TAG, "speakCloud: calling DashScope model=$model textLen=${text.length}")
            state = state.copy(engine = TTSEngineType.IFlyTEK)
            eventChannel.trySend(TTSEvent.StartSpeaking)
            val client = CloudTTSClient(apiKey)
            val cacheDir = File(context.cacheDir, "tts_cache").apply { mkdirs() }
            val audio = client.synthesize(text, model, cacheDir)
            if (audio != null) {
                Log.d(TAG, "speakCloud: got audio file ${audio.audioFile.name}, playing")
                CloudAudioPlayer.play(audio.audioFile)
                eventChannel.trySend(TTSEvent.DoneSpeaking)
                state = state.copy(engine = TTSEngineType.SYSTEM)
            } else {
                Log.w(TAG, "speakCloud: synthesize returned null, falling back to offline")
                state = state.copy(engine = TTSEngineType.SYSTEM)
                eventChannel.trySend(TTSEvent.Error("Cloud TTS failed, using offline"))
                speak(text)
            }
        }
    }

    fun speak(text: String, profile: VoiceProfile? = null) {
        // Priority: sherpa-onnx > system TTS
        if (state.engine == TTSEngineType.SHERPA_ONNX) {
            val sid = profile?.ttsSid ?: 0
            scope.launch {
                eventChannel.trySend(TTSEvent.StartSpeaking)
                sherpaOnnxTTS.speak(text, sid = sid, speed = profile?.speed ?: 1.0f)
                eventChannel.trySend(TTSEvent.DoneSpeaking)
            }
            return
        }

        // System TTS fallback
        tts?.let { ttsEngine ->
            if (profile != null) {
                ttsEngine.setPitch(profile.pitch)
                ttsEngine.setSpeechRate(profile.speed)
            }
            state = state.copy(isSpeaking = true)
            ttsEngine.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_${System.currentTimeMillis()}")
        }
    }

    /** Speak with explicit speaker ID (for sherpa-onnx test/preview) */
    fun speakWithSid(text: String, sid: Int, speed: Float = 1.0f) {
        if (state.engine == TTSEngineType.SHERPA_ONNX) {
            scope.launch {
                eventChannel.trySend(TTSEvent.StartSpeaking)
                sherpaOnnxTTS.speak(text, sid = sid, speed = speed)
                eventChannel.trySend(TTSEvent.DoneSpeaking)
            }
            return
        }
        // Fallback to system TTS if sherpa not loaded
        speak(text)
    }

    fun stop() {
        if (state.engine == TTSEngineType.SHERPA_ONNX) {
            sherpaOnnxTTS.stop()
        }
        tts?.stop()
        state = state.copy(isSpeaking = false)
    }

    fun shutdown() {
        sherpaOnnxTTS.release()
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
        } catch (_: Exception) { false }
    }
}
