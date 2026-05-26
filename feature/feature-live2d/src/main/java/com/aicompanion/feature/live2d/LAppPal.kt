package com.aicompanion.feature.live2d

import android.content.res.AssetManager
import java.io.InputStream

/**
 * File loading utilities for Live2D Cubism SDK.
 * Reads model files from Android assets.
 */
object LAppPal {

    lateinit var assets: AssetManager

    fun loadFileAsBytes(path: String): ByteArray {
        return assets.open(path).use { it.readBytes() }
    }

    fun openFile(path: String): InputStream = assets.open(path)
}
