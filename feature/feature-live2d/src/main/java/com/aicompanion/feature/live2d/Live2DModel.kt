package com.aicompanion.feature.live2d

import android.util.Log
import com.live2d.sdk.cubism.framework.CubismDefaultParameterId.ParameterId
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.ICubismModelSetting
import com.live2d.sdk.cubism.framework.effect.CubismBreath
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink
import com.live2d.sdk.cubism.framework.id.CubismId
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismTargetPoint
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.motion.ACubismMotion
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid

private const val TAG = "Live2DModel"

class Live2DModel : CubismUserModel() {

    private var modelSetting: ICubismModelSetting? = null
    private var modelHomeDirectory: String = ""
    private var userTimeSeconds: Float = 0f

    val dragTarget = CubismTargetPoint()
    private val textureManager = Live2DTextureManager()

    private val expressionMap = mutableMapOf<String, ACubismMotion>()
    private val motionMap = mutableMapOf<String, ACubismMotion>()
    private val eyeBlinkIds = mutableListOf<CubismId>()
    private val lipSyncIds = mutableListOf<CubismId>()

    private val idManager = CubismFramework.getIdManager()
    private val idParamAngleX = idManager.getId(ParameterId.ANGLE_X.id)
    private val idParamAngleY = idManager.getId(ParameterId.ANGLE_Y.id)
    private val idParamAngleZ = idManager.getId(ParameterId.ANGLE_Z.id)
    private val idParamBodyAngleX = idManager.getId(ParameterId.BODY_ANGLE_X.id)
    private val idParamEyeBallX = idManager.getId(ParameterId.EYE_BALL_X.id)
    private val idParamEyeBallY = idManager.getId(ParameterId.EYE_BALL_Y.id)

    private val mvpMatrix = CubismViewMatrix() // extends CubismMatrix44, has public ctor

    val isReady: Boolean get() = isInitialized

    fun loadModelAssets(assetsDir: String, settingFileName: String): Boolean {
        modelHomeDirectory = assetsDir
        try {
            val jsonPath = assetsDir + settingFileName
            val jsonBytes = LAppPal.loadFileAsBytes(jsonPath)
            val setting = CubismModelSettingJson(jsonBytes) ?: return false
            modelSetting = setting

            if (setting.json == null) {
                Log.e(TAG, "model3.json parse failed: $jsonPath")
                return false
            }

            // Load MOC3
            val mocFileName = setting.modelFileName
            if (mocFileName.isNotEmpty()) {
                loadModel(LAppPal.loadFileAsBytes(assetsDir + mocFileName), mocConsistency)
            }

            // Expressions
            for (i in 0 until setting.expressionCount) {
                val name = setting.getExpressionName(i)
                val path = "$assetsDir${setting.getExpressionFileName(i)}"
                val motion = loadExpression(LAppPal.loadFileAsBytes(path))
                if (motion != null) expressionMap[name] = motion
            }

            // Physics
            val physFile = setting.physicsFileName
            if (physFile.isNotEmpty()) {
                loadPhysics(LAppPal.loadFileAsBytes(assetsDir + physFile))
            }

            // Pose
            val poseFile = setting.poseFileName
            if (poseFile.isNotEmpty()) {
                loadPose(LAppPal.loadFileAsBytes(assetsDir + poseFile))
            }

            // User data
            val userDataFile = setting.userDataFile
            if (userDataFile.isNotEmpty()) {
                loadUserData(LAppPal.loadFileAsBytes(assetsDir + userDataFile))
            }

            // Eye blink
            if (setting.eyeBlinkParameterCount > 0) {
                eyeBlink = CubismEyeBlink.create(setting)
                for (i in 0 until setting.eyeBlinkParameterCount) {
                    eyeBlinkIds.add(setting.getEyeBlinkParameterId(i))
                }
            }

            // Breath
            breath = CubismBreath.create()
            breath.setParameters(listOf(
                CubismBreath.BreathParameterData(idParamAngleX, 0f, 15f, 6.5345f, 0.5f),
                CubismBreath.BreathParameterData(idParamAngleY, 0f, 8f, 3.5345f, 0.5f),
                CubismBreath.BreathParameterData(idParamAngleZ, 0f, 10f, 5.5345f, 0.5f),
                CubismBreath.BreathParameterData(idParamBodyAngleX, 0f, 4f, 15.5345f, 0.5f),
            ))

            // Lip sync
            for (i in 0 until setting.lipSyncParameterCount) {
                lipSyncIds.add(setting.getLipSyncParameterId(i))
            }

            // Model matrix from layout
            val layout = HashMap<String, Float>()
            if (setting.getLayoutMap(layout)) {
                modelMatrix.setupFromLayout(layout)
            } else {
                modelMatrix.setWidth(1.0f)
                modelMatrix.setHeight(1.0f)
                modelMatrix.setCenterPosition(0f, 0f)
            }

            model.saveParameters()

            // Preload motions
            for (i in 0 until setting.motionGroupCount) {
                preloadMotionGroup(setting.getMotionGroupName(i))
            }
            motionManager.stopAllMotions()

            isInitialized = true
            Log.d(TAG, "Model loaded: $settingFileName")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            return false
        }
    }

    fun setupRenderer(width: Int, height: Int) {
        val renderer = CubismRendererAndroid.create()
        setupRenderer(renderer)
    }

    fun loadTextures() {
        val setting = modelSetting ?: return
        val renderer = getRenderer() as? CubismRendererAndroid ?: return

        for (i in 0 until setting.textureCount) {
            val fileName = setting.getTextureFileName(i)
            if (fileName.isEmpty()) continue
            val path = modelHomeDirectory + fileName
            try {
                val tex = textureManager.createTextureFromPngFile(path)
                renderer.bindTexture(i, tex.id)
            } catch (e: Exception) {
                Log.w(TAG, "Texture $i failed: ${e.message}")
            }
        }
        renderer.isPremultipliedAlpha(true)
    }

    fun updateFrame(deltaTimeSeconds: Float = 1f / 30f) {
        if (!isInitialized || model == null) return

        userTimeSeconds += deltaTimeSeconds
        dragTarget.update(deltaTimeSeconds)
        val dragX = dragTarget.x
        val dragY = dragTarget.y

        model.loadParameters()

        var motionUpdated = false
        if (motionManager.isFinished) {
            startRandomMotion("idle", 1)
        } else {
            motionUpdated = motionManager.updateMotion(model, deltaTimeSeconds)
        }

        model.saveParameters()

        if (!motionUpdated && eyeBlink != null) {
            eyeBlink!!.updateParameters(model, deltaTimeSeconds)
        }

        expressionManager?.updateMotion(model, deltaTimeSeconds)

        model.addParameterValue(idParamAngleX, dragX * 30f)
        model.addParameterValue(idParamAngleY, dragY * 30f)
        model.addParameterValue(idParamAngleZ, dragX * dragY * (-30f))
        model.addParameterValue(idParamBodyAngleX, dragX * 10f)
        model.addParameterValue(idParamEyeBallX, dragX)
        model.addParameterValue(idParamEyeBallY, dragY)

        breath?.updateParameters(model, deltaTimeSeconds)
        physics?.evaluate(model, deltaTimeSeconds)
        pose?.updateParameters(model, deltaTimeSeconds)

        model.update()
    }

    fun drawModel(matrix: CubismMatrix44) {
        val m = model ?: return
        val r = getRenderer() as? CubismRendererAndroid ?: return
        CubismMatrix44.multiply(modelMatrix.array, matrix.array, mvpMatrix.array)
        r.setMvpMatrix(mvpMatrix)
        r.drawModel()
    }

    fun setExpressionByName(name: String) {
        val exp = expressionMap[name] as? CubismExpressionMotion ?: return
        expressionManager?.startMotionPriority(exp, 3)
    }

    fun setMouthOpen(value: Float) {
        for (id in lipSyncIds) {
            model.addParameterValue(id, value.coerceIn(0f, 1f), 0.8f)
        }
    }

    fun startRandomMotion(group: String, priority: Int = 1) {
        val setting = modelSetting ?: return
        val count = setting.getMotionCount(group)
        if (count > 0) {
            startMotion(group, (0 until count).random(), priority)
        }
    }

    fun startMotion(group: String, no: Int, priority: Int = 2) {
        val setting = modelSetting ?: return
        if (priority == 3) {
            motionManager.setReservationPriority(priority)
        } else if (!motionManager.reserveMotion(priority)) {
            return
        }

        val name = "${group}_$no"
        var motion = motionMap[name] as? CubismMotion
        if (motion == null) {
            val path = modelHomeDirectory + setting.getMotionFileName(group, no)
            if (path == modelHomeDirectory) return
            motion = loadMotion(LAppPal.loadFileAsBytes(path)) as? CubismMotion ?: return
            val fadeIn = setting.getMotionFadeInTimeValue(group, no)
            if (fadeIn != -1f) motion.setFadeInTime(fadeIn)
            val fadeOut = setting.getMotionFadeOutTimeValue(group, no)
            if (fadeOut != -1f) motion.setFadeOutTime(fadeOut)
            motion.setEffectIds(eyeBlinkIds, lipSyncIds)
            motionMap[name] = motion
        }
        motionManager.startMotionPriority(motion, priority)
    }

    override fun setOpacity(alpha: Float) {
        opacity = alpha
    }

    private fun preloadMotionGroup(group: String) {
        val setting = modelSetting ?: return
        val count = setting.getMotionCount(group)
        for (i in 0 until count) {
            val name = "${group}_$i"
            val path = modelHomeDirectory + setting.getMotionFileName(group, i)
            if (path == modelHomeDirectory) continue
            val tmp = loadMotion(LAppPal.loadFileAsBytes(path)) ?: continue
            val motion = tmp as? CubismMotion ?: continue
            val fadeIn = setting.getMotionFadeInTimeValue(group, i)
            if (fadeIn != -1f) motion.setFadeInTime(fadeIn)
            val fadeOut = setting.getMotionFadeOutTimeValue(group, i)
            if (fadeOut != -1f) motion.setFadeOutTime(fadeOut)
            motion.setEffectIds(eyeBlinkIds, lipSyncIds)
            motionMap[name] = motion
        }
    }

    fun releaseModel() {
        delete()
    }
}
