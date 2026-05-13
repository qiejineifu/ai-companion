package com.aicompanion.feature.voice

import android.content.Context
import com.aicompanion.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sherpa-ONNX offline TTS engine wrapper.
 * Phase 3: Requires sherpa-onnx AAR in libs/ directory.
 * Model: sherpa-onnx-vits-zh-ll (or piper-zh) ~60-120MB.
 */
class SherpaOnnxTTS(private val context: Context) {

    data class ModelInfo(
        val name: String,
        val language: String,
        val sizeMB: Int,
        val url: String
    )

    companion object {
        val AVAILABLE_MODELS = listOf(
            ModelInfo("VITS 中文女声", "zh-CN", 64, "sherpa-onnx-vits-zh-ll"),
            ModelInfo("VITS 中文男声", "zh-CN", 68, "sherpa-onnx-vits-zh-aishell3"),
            ModelInfo("Piper 中文通用", "zh-CN", 52, "sherpa-onnx-piper-zh"),
        )
    }

    private var isInitialized = false
    private var modelDir: File

    init {
        modelDir = File(context.filesDir, "sherpa_models/tts")
        modelDir.mkdirs()
    }

    suspend fun initialize(modelName: String = "sherpa-onnx-vits-zh-ll"): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelDir, "$modelName/model.onnx")
                val tokensFile = File(modelDir, "$modelName/tokens.txt")

                if (!modelFile.exists() || !tokensFile.exists()) {
                    return@withContext Result.Error("模型文件未下载，请先下载离线语音模型")
                }

                // In production: initialize SherpaOnnxTTS engine
                // val config = OnlineTtsConfig(
                //     model = OfflineTtsVitsModelConfig(
                //         model = modelFile.absolutePath,
                //         tokens = tokensFile.absolutePath,
                //     )
                // )
                // tts = OfflineTts(config)

                isInitialized = true
                Result.Success(true)
            } catch (e: Exception) {
                Result.Error("初始化离线 TTS 失败: ${e.message}", e)
            }
        }
    }

    suspend fun synthesize(text: String, speed: Float = 1.0f): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    return@withContext Result.Error("离线 TTS 引擎未初始化")
                }

                // In production: generate audio
                // val audio = tts.generate(text, sid = 1, speed = speed)
                // Result.Success(audio.samples)

                // Placeholder: return empty bytes (model not bundled)
                Result.Success(ByteArray(0))
            } catch (e: Exception) {
                Result.Error("语音合成失败: ${e.message}", e)
            }
        }
    }

    suspend fun downloadModel(modelInfo: ModelInfo, onProgress: (Float) -> Unit): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val modelDir = File(modelDir, modelInfo.name)
                modelDir.mkdirs()

                // In production: download from modelInfo.url
                onProgress(0.5f)

                // Simulate download
                kotlinx.coroutines.delay(500)
                onProgress(1.0f)

                Result.Success(true)
            } catch (e: Exception) {
                Result.Error("模型下载失败: ${e.message}", e)
            }
        }
    }

    fun isModelDownloaded(modelName: String): Boolean {
        val modelFile = File(modelDir, "$modelName/model.onnx")
        return modelFile.exists()
    }

    fun getDownloadedModels(): List<String> {
        return modelDir.listFiles()
            ?.filter { File(it, "model.onnx").exists() }
            ?.map { it.name }
            ?: emptyList()
    }

    fun release() {
        isInitialized = false
    }
}
