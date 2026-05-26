package com.aicompanion.feature.voice

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import com.aicompanion.core.common.Result
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaOnnxSTT @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var isListening = false

    suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (recognizer != null) return@withContext Result.Success(true)
        try {
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(
                        model = "sherpa_models/stt/model.int8.onnx"
                    ),
                    tokens = "sherpa_models/stt/tokens.txt",
                    numThreads = 1,
                )
            )
            recognizer = OfflineRecognizer(context.assets, config)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error("STT初始化失败: ${e.message}", e)
        }
    }

    fun startStreaming(): Flow<STTResult> = flow {
        val rec = recognizer ?: run {
            emit(STTResult.Error("识别器未初始化"))
            return@flow
        }
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val allSamples = mutableListOf<Float>()
        val buffer = ShortArray(bufferSize)
        val maxSamples = sampleRate * 15

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                emit(STTResult.Error("麦克风不可用"))
                return@flow
            }
            audioRecord?.startRecording()
            isListening = true

            while (isListening) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: break
                if (read <= 0) continue
                for (i in 0 until read) allSamples.add(buffer[i].toFloat() / 32768f)

                if (allSamples.size >= sampleRate) {
                    val s = rec.createStream()
                    s.acceptWaveform(allSamples.toFloatArray(), sampleRate)
                    rec.decode(s)
                    val t = rec.getResult(s).text
                    s.release()
                    if (t.isNotBlank()) emit(STTResult.Partial(t))
                }
                if (allSamples.size >= maxSamples) break
            }

            if (allSamples.isNotEmpty()) {
                val s = rec.createStream()
                s.acceptWaveform(allSamples.toFloatArray(), sampleRate)
                rec.decode(s)
                val t = rec.getResult(s).text
                s.release()
                if (t.isNotBlank()) emit(STTResult.Final(t))
            }
        } catch (e: SecurityException) {
            emit(STTResult.Error("缺少录音权限"))
        } catch (e: Exception) {
            emit(STTResult.Error("录音错误: ${e.message}"))
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isListening = false
        }
    }

    fun stop() { isListening = false }
    fun release() {
        isListening = false
        audioRecord?.release()
        audioRecord = null
        recognizer?.release()
        recognizer = null
    }
}

sealed class STTResult {
    data class Partial(val text: String) : STTResult()
    data class Final(val text: String) : STTResult()
    data class Error(val message: String) : STTResult()
}
