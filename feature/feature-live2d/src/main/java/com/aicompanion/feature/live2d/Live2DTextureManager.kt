package com.aicompanion.feature.live2d

import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils

class Live2DTextureManager {

    data class TextureInfo(val id: Int, val width: Int, val height: Int, val filePath: String)

    private val textures = mutableListOf<TextureInfo>()

    fun createTextureFromPngFile(path: String): TextureInfo {
        textures.find { it.filePath == path }?.let { return it }

        val stream = LAppPal.openFile(path)
        val bitmap = BitmapFactory.decodeStream(stream)
        stream.close()

        val textureId = IntArray(1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val info = TextureInfo(textureId[0], bitmap.width, bitmap.height, path)
        textures.add(info)
        bitmap.recycle()
        return info
    }
}
