package com.aicompanion.feature.live2d

import android.content.Context
import com.aicompanion.core.common.Emotion
import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.Live2DModelInfo
import com.aicompanion.domain.repository.Live2DModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class Live2DState(
    val isLoaded: Boolean = false,
    val currentModel: Live2DModelInfo? = null,
    val currentEmotion: Emotion = Emotion.NEUTRAL,
    val currentExpression: String = "neutral",
    val isPlayingMotion: Boolean = false,
    val motionName: String? = null,
    val mouthOpenY: Float = 0f,
    val mouthForm: Float = 0f,
    val isSpeaking: Boolean = false
)

@Singleton
class Live2DManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: Live2DModelRepository
) {
    private val emotionMapper = EmotionMapper()
    val lipSyncAnalyzer = LipSyncAnalyzer()

    private val _state = MutableStateFlow(Live2DState())
    val state: StateFlow<Live2DState> = _state.asStateFlow()

    private var motionScope: CoroutineScope? = null
    private var idleLoopJob: Job? = null

    fun initialize() {
        CoroutineScope(Dispatchers.Main).launch {
            val active = repository.getActive()
            if (active != null) {
                loadModel(active)
            } else {
                // Try to load built-in model
                createDefaultModel()
            }
        }
    }

    suspend fun loadModel(modelInfo: Live2DModelInfo): Result<Live2DModelInfo> {
        return try {
            // Validate model files
            val modelJsonFile = File(modelInfo.modelJsonPath)
            if (!modelJsonFile.exists()) {
                return Result.Error("模型文件不存在: ${modelInfo.modelJsonPath}")
            }

            repository.setActive(modelInfo.id)
            _state.value = _state.value.copy(isLoaded = true, currentModel = modelInfo)
            startIdleAnimation()
            Result.Success(modelInfo)
        } catch (e: Exception) {
            Result.Error("加载模型失败: ${e.message}", e)
        }
    }

    fun setEmotion(emotion: Emotion) {
        if (!_state.value.isLoaded) return

        val expression = emotionMapper.getExpression(emotion)
        val motions = emotionMapper.getMotions(emotion)
        val priority = emotionMapper.getPriority(emotion)

        _state.value = _state.value.copy(
            currentEmotion = emotion,
            currentExpression = expression
        )

        if (motions.isNotEmpty() && priority > 0) {
            playMotion(motions.first(), priority)
        }
    }

    fun playMotion(motionName: String, priority: Int = 2) {
        if (!_state.value.isLoaded) return

        _state.value = _state.value.copy(isPlayingMotion = true, motionName = motionName)

        motionScope?.cancel()
        motionScope = CoroutineScope(Dispatchers.Main)

        // Simulated motion playback - in production this triggers Cubism SDK
        motionScope?.launch {
            delay(2000) // Motion duration placeholder
            _state.value = _state.value.copy(isPlayingMotion = false, motionName = null)
            startIdleAnimation()
        }
    }

    fun stopMotion() {
        motionScope?.cancel()
        _state.value = _state.value.copy(isPlayingMotion = false, motionName = null)
        startIdleAnimation()
    }

    fun updateLipSync(audioFrame: FloatArray) {
        val params = lipSyncAnalyzer.analyzeFrame(audioFrame)
        _state.value = _state.value.copy(
            mouthOpenY = params.mouthOpenY,
            mouthForm = params.mouthForm,
            isSpeaking = params.isSpeaking
        )
    }

    fun updateLipSyncPCM(pcmFrame: ShortArray) {
        val params = lipSyncAnalyzer.analyzePCMFrame(pcmFrame)
        _state.value = _state.value.copy(
            mouthOpenY = params.mouthOpenY,
            mouthForm = params.mouthForm,
            isSpeaking = params.isSpeaking
        )
    }

    fun onTap() {
        val tapEmotions = listOf(Emotion.HAPPY, Emotion.SURPRISED, Emotion.SHY)
        setEmotion(tapEmotions.random())
    }

    fun onDrag(dx: Float, dy: Float) {
        // Head tracking - parameter manipulation
        if (!_state.value.isLoaded) return
        // In production: update model parameters for head rotation
    }

    private fun startIdleAnimation() {
        idleLoopJob?.cancel()
        if (_state.value.isPlayingMotion) return

        idleLoopJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                _state.value = _state.value.copy(currentExpression = "neutral")
                delay(3000)
                // Random blink / subtle movement cycle
            }
        }
    }

    private suspend fun createDefaultModel() {
        // Phase 1: create placeholder for built-in model
        val modelsDir = File(context.filesDir, "live2d_models")
        modelsDir.mkdirs()
    }

    fun getModels() = repository.getAll()

    suspend fun importModel(uri: String): Result<Live2DModelInfo> = repository.importModel(uri)

    suspend fun setActive(id: String) {
        repository.setActive(id)
        val model = repository.getActive()
        if (model != null) loadModel(model)
    }

    suspend fun deleteModel(id: String) = repository.delete(id)
}
