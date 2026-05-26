package com.aicompanion.feature.live2d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView rendering a Live2D model on transparent background.
 * Rendering is delegated to Cubism SDK via [Live2DModel.drawModel].
 */
class CubismRendererView(context: Context) : GLSurfaceView(context) {

    private var model: Live2DModel? = null
    private var renderer: CubismGLRenderer? = null
    private var isRendererSet = false

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setZOrderOnTop(true)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
    }

    fun setModel(live2DModel: Live2DModel) {
        model = live2DModel
        if (!isRendererSet) {
            renderer = CubismGLRenderer(live2DModel)
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
            isRendererSet = true
        } else {
            renderer?.switchModel(live2DModel)
        }
    }

    fun getCubismModel(): Live2DModel? = model

    fun cleanup() {
        model?.releaseModel()
        model = null
    }

    private inner class CubismGLRenderer(
        private var currentModel: Live2DModel
    ) : GLSurfaceView.Renderer {

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var rendererCreated = false
        private val projection = CubismViewMatrix()

        fun switchModel(newModel: Live2DModel) {
            currentModel = newModel
            if (surfaceWidth > 0 && surfaceHeight > 0 && !rendererCreated) {
                createRenderer()
            }
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            GLES20.glViewport(0, 0, width, height)

            val ratio = width.toFloat() / height.toFloat()
            projection.setScreenRect(-ratio, ratio, -1f, 1f)
            projection.setMaxScale(2f)
            projection.setMinScale(0.8f)
            projection.loadIdentity()

            if (!rendererCreated) {
                createRenderer()
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            if (currentModel.isReady) {
                currentModel.updateFrame()
                projection.loadIdentity()
                currentModel.drawModel(projection)
            }
        }

        private fun createRenderer() {
            if (surfaceWidth > 0 && surfaceHeight > 0) {
                currentModel.setupRenderer(surfaceWidth, surfaceHeight)
                currentModel.loadTextures()
                rendererCreated = true
            }
        }
    }
}
