package com.aicompanion.app

import android.app.Application
import com.aicompanion.core.common.CrashHandler
import com.aicompanion.core.network.NetworkMonitor
import com.aicompanion.feature.live2d.Live2DManager
import com.aicompanion.feature.voice.TTSManager
import com.aicompanion.feature.voice.STTManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AICompanionApp : Application() {

    @Inject lateinit var live2DManager: Live2DManager
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var sttManager: STTManager

    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize crash handler first
        CrashHandler.init(this)

        // Initialize network monitoring (safe)
        try {
            networkMonitor = NetworkMonitor(this)
        } catch (_: Exception) {}

        // Initialize engines with crash protection
        try { live2DManager.initialize() } catch (_: Exception) {}
        try { ttsManager.initialize() } catch (_: Exception) {}
        try { sttManager.initialize() } catch (_: Exception) {}
    }

    override fun onTerminate() {
        try { ttsManager.shutdown() } catch (_: Exception) {}
        try { sttManager.destroy() } catch (_: Exception) {}
        try { networkMonitor.destroy() } catch (_: Exception) {}
        super.onTerminate()
    }
}
