package com.aicompanion.feature.voice

import android.content.Context
import com.aicompanion.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sherpa-ONNX offline STT engine wrapper.
 * Phase 3: Requires sherpa-onnx AAR.
 * Models: Zipformer-ZH / SenseVoice-ZH ~80-120MB.
 */
class SherpaOnnxSTT(private val context: Context) {

    data class ModelInfo(
        val name: String,
        val language: String,
        val type: String,
        val sizeMB: Int,
        val url: String
    )

    companion object {
        val AVAILABLE_MODELS = listOf(
            ModelInfo("Zipformer 中文", "zh-CN", "zipformer", 85, "sherpa-onnx-zipformer-zh"),
            ModelInfo("SenseVoice 中文", "zh-CN", "sense_voice", 95, "sherpa-onnx-sense-voice-zh"),
            ModelInfo("Paraformer 中文", "zh-CN", "paraformer", 78, "sherpa-onnx-paraformer-zh"),
        )
    }

    private var isInitialized = false
    private var isListening = false
    private val modelDir: File

    init {
        modelDir = File(context.filesDir, "sherpa_models/stt")
        modelDir.mkdirs()
    }

    suspend fun initialize(modelName: String = "sherpa-onnx-zipformer-zh"): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val modelDir = File(modelDir, modelName)
                val encoder = File(modelDir, "encoder.onnx")
                val decoder = File(modelDir, "decoder.onnx")
                val joiner = File(modelDir, "joiner.onnx")
                val tokens = File(modelDir, "tokens.txt")

                if (!encoder.exists() || !decoder.exists() || !tokens.exists()) {
                    return@withContext Result.Error("STT 模型文件未下载")
                }

                // In production: init SherpaOnnxRecognizer
                // val config = OnlineRecognizerConfig(
                //     model = OnlineTransducerModelConfig(
                //         encoder = encoder.absolutePath,
                //         decoder = decoder.absolutePath,
                //         joiner = joiner.absolutePath,
                //     ),
                //     tokens = tokens.absolutePath,
                // )
                // recognizer = OnlineRecognizer(config)

                isInitialized = true
                Result.Success(true)
            } catch (e: Exception) {
                Result.Error("初始化离线 STT 失败: ${e.message}", e)
            }
        }
    }

    fun startStreaming(): Flow<STTResult> = flow {
        if (!isInitialized) {
            emit(STTResult.Error("STT 引擎未初始化"))
            return@flow
        }

        isListening = true

        // In production: audio recording loop + streaming recognition
        // recognizer.createStream()
        // audioRecord.startRecording()
        // while (isListening) {
        //     audioRecord.read(buffer)
        //     recognizer.acceptWaveform(samples)
        //     while (recognizer.isReady()) recognizer.decode()
        //     emit(STTResult.Partial(recognizer.getResult()))
        // }

        // Placeholder
        while (isListening) {
            kotlinx.coroutines.delay(100)
        }
    }

    fun stop() {
        isListening = false
    }

    suspend fun downloadModel(modelInfo: ModelInfo, onProgress: (Float) -> Unit): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(modelDir, modelInfo.name)
                dir.mkdirs()
                onProgress(0.3f)
                kotlinx.coroutines.delay(300)
                onProgress(0.7f)
                kotlinx.coroutines.delay(300)
                onProgress(1.0f)
                Result.Success(true)
            } catch (e: Exception) {
                Result.Error("STT 模型下载失败: ${e.message}", e)
            }
        }
    }

    fun isModelDownloaded(modelName: String): Boolean {
        return File(modelDir, "$modelName/encoder.onnx").exists()
    }

    fun release() {
        isListening = false
        isInitialized = false
    }
}

sealed class STTResult {
    data class Partial(val text: String) : STTResult()
    data class Final(val text: String) : STTResult()
    data class Error(val message: String) : STTResult()
}
