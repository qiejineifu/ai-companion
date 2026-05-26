package com.aicompanion.core.common

import android.content.Context

/** Persisted voice settings */
data class VoicePrefData(
    val selectedModelIndex: Int = 0,
    val sid: Int = 0,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val dashscopeApiKey: String = "",
    val dashscopeVoice: String = "cosyvoice-v1:zhitian_emo",
    val cloudTtsEnabled: Boolean = true
)

class VoicePrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)

    fun load(): VoicePrefData = VoicePrefData(
        selectedModelIndex = prefs.getInt("model_idx", 0),
        sid = prefs.getInt("sid", 0),
        pitch = prefs.getFloat("pitch", 1.0f),
        speed = prefs.getFloat("speed", 1.0f),
        dashscopeApiKey = prefs.getString("dashscope_key", "") ?: "",
        dashscopeVoice = prefs.getString("dashscope_voice", "cosyvoice-v1:zhitian_emo") ?: "cosyvoice-v1:zhitian_emo",
        cloudTtsEnabled = prefs.getBoolean("cloud_tts_enabled", true)
    )

    fun save(data: VoicePrefData) {
        prefs.edit()
            .putInt("model_idx", data.selectedModelIndex)
            .putInt("sid", data.sid)
            .putFloat("pitch", data.pitch)
            .putFloat("speed", data.speed)
            .putString("dashscope_key", data.dashscopeApiKey)
            .putString("dashscope_voice", data.dashscopeVoice)
            .putBoolean("cloud_tts_enabled", data.cloudTtsEnabled)
            .apply()
    }

    fun getApiKey(): String = prefs.getString("dashscope_key", "") ?: ""

    fun saveApiKey(key: String) {
        prefs.edit().putString("dashscope_key", key).apply()
    }

    fun getVoice(): String = prefs.getString("dashscope_voice", "cosyvoice-v1:zhitian_emo") ?: "cosyvoice-v1:zhitian_emo"

    fun isCloudEnabled(): Boolean = prefs.getBoolean("cloud_tts_enabled", true)

    fun setCloudEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cloud_tts_enabled", enabled).apply()
    }
}
