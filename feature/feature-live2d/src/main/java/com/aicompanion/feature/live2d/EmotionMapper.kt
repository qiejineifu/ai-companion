package com.aicompanion.feature.live2d

import com.aicompanion.core.common.Emotion

data class Live2DEmotionConfig(
    val emotion: Emotion,
    val expressionName: String,
    val motionNames: List<String>,
    val priority: Int = 2
)

class EmotionMapper {

    private val configs = listOf(
        Live2DEmotionConfig(Emotion.HAPPY, "smile", listOf("happy_bounce", "happy_idle"), priority = 2),
        Live2DEmotionConfig(Emotion.SAD, "frown", listOf("sad_slow", "sad_idle"), priority = 2),
        Live2DEmotionConfig(Emotion.ANGRY, "angry_eyes", listOf("angry_shake"), priority = 3),
        Live2DEmotionConfig(Emotion.SURPRISED, "wide_eyes", listOf("surprise_jump"), priority = 3),
        Live2DEmotionConfig(Emotion.SHY, "blush", listOf("shy_turn"), priority = 2),
        Live2DEmotionConfig(Emotion.THINKING, "neutral", listOf("thinking_tilt"), priority = 1),
        Live2DEmotionConfig(Emotion.NEUTRAL, "neutral", listOf("idle_breath"), priority = 0)
    )

    private val configMap = configs.associateBy { it.emotion }

    fun getExpression(emotion: Emotion): String = configMap[emotion]?.expressionName ?: "neutral"

    fun getMotions(emotion: Emotion): List<String> = configMap[emotion]?.motionNames ?: listOf("idle_breath")

    fun getPriority(emotion: Emotion): Int = configMap[emotion]?.priority ?: 0

    fun detectEmotionFromText(text: String): Emotion {
        val lower = text.lowercase()
        return when {
            lower.contains("哈哈") || lower.contains("开心") || lower.contains("太好了") -> Emotion.HAPPY
            lower.contains("难过") || lower.contains("伤心") || lower.contains("呜呜") -> Emotion.SAD
            lower.contains("生气") || lower.contains("可恶") || lower.contains("哼") -> Emotion.ANGRY
            lower.contains("哇塞") || lower.contains("天哪") || lower.contains("不会吧") -> Emotion.SURPRISED
            lower.contains("害羞") || lower.contains("⁄") || lower.contains("别夸了") -> Emotion.SHY
            lower.contains("嗯...") || lower.contains("我想想") -> Emotion.THINKING
            else -> Emotion.NEUTRAL
        }
    }
}
