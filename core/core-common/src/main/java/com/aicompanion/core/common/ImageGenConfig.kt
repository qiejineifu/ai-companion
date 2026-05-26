package com.aicompanion.core.common

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

data class ImageGenSettings(
    val enabled: Boolean = false,
    val provider: String = "openai",
    val apiUrl: String = "",
    val apiKeyEncrypted: String = "",
    val model: String = "",
    val size: String = "1024x1024"
)

class ImageGenConfig(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("image_gen", Context.MODE_PRIVATE))

    fun load(): ImageGenSettings = ImageGenSettings(
        enabled = prefs.getBoolean("enabled", false),
        provider = prefs.getString("provider", "openai") ?: "openai",
        apiUrl = prefs.getString("api_url", "") ?: "",
        apiKeyEncrypted = prefs.getString("api_key", "") ?: "",
        model = prefs.getString("model", "") ?: "",
        size = prefs.getString("size", "1024x1024") ?: "1024x1024"
    )

    fun save(settings: ImageGenSettings) {
        val encrypted = try {
            val key = CryptoUtil.getOrCreateKey()
            val bytes = CryptoUtil.encrypt(settings.apiKeyEncrypted, key)
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) { settings.apiKeyEncrypted }

        prefs.edit()
            .putBoolean("enabled", settings.enabled)
            .putString("provider", settings.provider)
            .putString("api_url", settings.apiUrl)
            .putString("api_key", encrypted)
            .putString("model", settings.model)
            .putString("size", settings.size)
            .apply()
    }
}
