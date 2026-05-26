package com.aicompanion.feature.live2d

import android.content.Context
import android.util.Log
import com.aicompanion.core.common.Emotion
import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.Live2DModelInfo
import com.aicompanion.domain.repository.Live2DModelRepository
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismFrameworkConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Live2DManager"

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

    var activeModel: Live2DModel? = null
        private set

    private var frameworkInitialized = false

    fun initialize() {
        initFramework()
        LAppPal.assets = context.assets
        CoroutineScope(Dispatchers.Main).launch {
            val active = repository.getActive()
            if (active != null) {
                loadModel(active)
            } else {
                loadDefaultModel()
            }
        }
    }

    private suspend fun loadDefaultModel() {
        try {
            val model = Live2DModel()
            if (model.loadModelAssets("live2d_models/Haru/", "Haru.model3.json")) {
                activeModel = model
                _state.value = _state.value.copy(isLoaded = true)
                Log.d(TAG, "Default Haru model loaded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default model: ${e.message}", e)
        }
    }

    private fun initFramework() {
        if (frameworkInitialized) return
        try {
            val option = CubismFramework.Option().apply {
                loggingLevel = CubismFrameworkConfig.LogLevel.VERBOSE
            }
            CubismFramework.cleanUp()
            CubismFramework.startUp(option)
            CubismFramework.initialize()
            frameworkInitialized = true
            Log.d(TAG, "CubismFramework initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init CubismFramework: ${e.message}", e)
        }
    }

    suspend fun loadModel(modelInfo: Live2DModelInfo): Result<Live2DModelInfo> {
        return withContext(Dispatchers.IO) {
            try {
                if (!frameworkInitialized) initFramework()
                LAppPal.assets = context.assets

                // Release previous model
                activeModel?.releaseModel()

                val modelDir = determineModelDir(modelInfo)
                val settingName = modelInfo.modelJsonPath
                    .substringAfterLast('/')
                    .ifEmpty { "${modelInfo.name}.model3.json" }

                val model = Live2DModel()
                if (!model.loadModelAssets(modelDir, settingName)) {
                    return@withContext Result.Error("模型加载失败")
                }

                activeModel = model
                repository.setActive(modelInfo.id)
                _state.value = _state.value.copy(isLoaded = true, currentModel = modelInfo)

                Log.d(TAG, "Model loaded: ${modelInfo.name}")
                Result.Success(modelInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Load model failed: ${e.message}", e)
                Result.Error("加载模型失败: ${e.message}", e)
            }
        }
    }

    private fun determineModelDir(modelInfo: Live2DModelInfo): String {
        val path = modelInfo.modelJsonPath
        if (path.isNotBlank()) {
            val normalized = path.trimStart('/')
            // Remove the file name, keep the directory
            val lastSlash = normalized.lastIndexOf('/')
            return if (lastSlash > 0) "${normalized.substring(0, lastSlash + 1)}"
            else "live2d_models/"
        }
        return "live2d_models/${modelInfo.name}/"
    }

    fun setEmotion(emotion: Emotion) {
        val expression = emotionMapper.getExpression(emotion)
        activeModel?.setExpressionByName(expression)

        val motions = emotionMapper.getMotions(emotion)
        val priority = emotionMapper.getPriority(emotion)

        _state.value = _state.value.copy(
            currentEmotion = emotion,
            currentExpression = expression
        )

        if (motions.isNotEmpty() && priority > 0) {
            activeModel?.startRandomMotion("idle", priority)
        }
    }

    fun setSpeaking(speaking: Boolean) {
        _state.value = _state.value.copy(isSpeaking = speaking)
    }

    fun updateLipSync(audioFrame: FloatArray) {
        val params = lipSyncAnalyzer.analyzeFrame(audioFrame)
        _state.value = _state.value.copy(
            mouthOpenY = params.mouthOpenY,
            mouthForm = params.mouthForm,
            isSpeaking = params.isSpeaking
        )
        activeModel?.setMouthOpen(params.mouthOpenY)
    }

    fun updateLipSyncPCM(pcmFrame: ShortArray) {
        val params = lipSyncAnalyzer.analyzePCMFrame(pcmFrame)
        _state.value = _state.value.copy(
            mouthOpenY = params.mouthOpenY,
            mouthForm = params.mouthForm,
            isSpeaking = params.isSpeaking
        )
        activeModel?.setMouthOpen(params.mouthOpenY)
    }

    fun onTap() {
        activeModel?.setExpressionByName(listOf("happy", "surprise", "shy").random())
    }

    fun onDrag(dx: Float, dy: Float) {
        activeModel?.dragTarget?.set(dx * 0.01f, dy * 0.01f)
    }

    fun getModels() = repository.getAll()

    suspend fun importModel(uri: String): Result<Live2DModelInfo> = repository.importModel(uri)

    suspend fun setActive(id: String) {
        repository.setActive(id)
        val model = repository.getActive()
        if (model != null) loadModel(model)
    }

    suspend fun deleteModel(id: String) = repository.delete(id)

    fun release() {
        activeModel?.releaseModel()
        activeModel = null
        _state.value = Live2DState()
    }
}
