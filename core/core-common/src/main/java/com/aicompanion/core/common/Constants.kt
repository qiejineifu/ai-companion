package com.aicompanion.core.common

object Constants {
    const val DATABASE_NAME = "ai_companion.db"
    const val DATABASE_PASSPHRASE_KEY = "db_passphrase"
    const val KEYSTORE_KEY_ALIAS = "ai_companion_keystore"
    const val ENCRYPTED_PREFS_NAME = "ai_companion_secure_prefs"
    const val PREFS_NAME = "ai_companion_prefs"

    const val DEFAULT_CONTEXT_WINDOW = 20
    const val DEFAULT_MAX_TOKENS = 2048
    const val DEFAULT_TEMPERATURE = 0.8f
    const val DEFAULT_TOP_P = 0.9f
    const val DEFAULT_MEMORY_TOP_K = 5
    const val DEFAULT_MEMORY_MIN_CONFIDENCE = 0.6f
    const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 30L

    const val SSE_TOKEN_BATCH_MS = 48L
    const val LIVE2D_TARGET_FPS = 30
    const val STT_SILENCE_THRESHOLD_MS = 3000L
}

enum class Emotion(val label: String, val emoji: String) {
    HAPPY("happy", "😊"),
    SAD("sad", "😢"),
    ANGRY("angry", "😠"),
    SURPRISED("surprised", "😲"),
    SHY("shy", "😳"),
    THINKING("thinking", "🤔"),
    NEUTRAL("neutral", "😶")
}

enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    MEMORY("memory")
}

enum class TraitType(val key: String) {
    PERSONALITY("personality"),
    SPEAKING_STYLE("speaking_style"),
    RELATIONSHIP("relationship"),
    BACKGROUND("background"),
    CUSTOM("custom")
}

enum class VoiceEngineType(val value: String) {
    SYSTEM("system"),
    IFlyTEK("iflytek"),
    ALIYUN("aliyun"),
    SHERPA_ONNX("sherpa_onnx")
}
