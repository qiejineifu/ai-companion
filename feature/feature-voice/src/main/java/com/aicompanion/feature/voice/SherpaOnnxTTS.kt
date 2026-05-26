package com.aicompanion.feature.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Context
import android.util.Log
import com.aicompanion.core.common.Result
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaOnnxTTS @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: OfflineTts? = null
    private var currentModel: String = ""
    private var isSpeaking = false
    private var isReleased = false
    private val stateLock = Any()
    private val nativeLock = Any()

    data class TTSResult(val samples: FloatArray, val sampleRate: Int)

    suspend fun loadModel(modelDir: String, modelFile: String): Result<Boolean> = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            if (tts != null && currentModel == modelFile) return@withContext Result.Success(true)
            try {
                releaseInternal()
                val hasLexicon = try {
                    context.assets.open("$modelDir/lexicon.txt").use { true }
                } catch (_: Exception) { false }
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = "$modelDir/$modelFile",
                    lexicon = if (hasLexicon) "$modelDir/lexicon.txt" else "",
                    tokens = "$modelDir/tokens.txt"
                )
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(vits = vitsConfig, numThreads = 1)
                )
                tts = OfflineTts(context.assets, config)
                isReleased = false
                currentModel = modelFile
                Log.i("SherpaOnnxTTS", "Model loaded: $modelFile (lexicon=${if (hasLexicon) "yes" else "no"})")
                Result.Success(true)
            } catch (e: Exception) {
                Log.e("SherpaOnnxTTS", "Load failed for $modelFile: ${e.message}", e)
                Result.Error("TTS模型加载失败: ${e.message}", e)
            }
        }
    }

    fun generate(text: String, sid: Int = 0, speed: Float = 1.0f): TTSResult? {
        synchronized(nativeLock) {
            val t = tts ?: return null
            val audio = t.generate(text, sid = sid, speed = speed)
            return TTSResult(audio.samples, audio.sampleRate)
        }
    }

    suspend fun speak(text: String, sid: Int = 0, speed: Float = 1.0f) {
        // Wait for previous speech
        var acquired = false
        while (!acquired) {
            synchronized(stateLock) {
                if (isReleased) return
                if (!isSpeaking) {
                    isSpeaking = true
                    acquired = true
                }
            }
            if (!acquired) kotlinx.coroutines.delay(50)
        }

        withContext(Dispatchers.IO) {
            try {
                synchronized(stateLock) {
                    if (isReleased) { isSpeaking = false; return@withContext }
                }
                val audio = synchronized(nativeLock) {
                    tts?.generate(text, sid = sid, speed = speed)
                } ?: run { synchronized(stateLock) { isSpeaking = false }; return@withContext }
                synchronized(stateLock) {
                    if (isReleased) { isSpeaking = false; return@withContext }
                }
                playPCM(audio.samples, audio.sampleRate)
            } catch (_: Exception) {}
        }

        synchronized(stateLock) { isSpeaking = false }
    }

    fun stop() {
        synchronized(stateLock) { isSpeaking = false }
    }

    val speaking: Boolean get() = synchronized(stateLock) { isSpeaking }

    private fun playPCM(samples: FloatArray, sampleRate: Int) {
        val shorts = ShortArray(samples.size) { i ->
            (samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        val totalBytes = shorts.size * 2

        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(totalBytes)
            .setTransferMode(AudioTrack.MODE_STATIC).build()

        track.write(shorts, 0, shorts.size)
        track.play()
        val durationMs = (samples.size.toLong() * 1000 / sampleRate) + 300
        Thread.sleep(durationMs)
        try { track.stop(); track.release() } catch (_: Exception) {}
    }

    fun release() {
        synchronized(stateLock) {
            isReleased = true
            isSpeaking = false
        }
        synchronized(nativeLock) {
            tts?.release()
            tts = null
            currentModel = ""
        }
    }

    private fun releaseInternal() {
        // Called from loadModel's synchronized(stateLock) block
        isReleased = true
        isSpeaking = false
        synchronized(nativeLock) {
            tts?.release()
            tts = null
            currentModel = ""
        }
    }
}
