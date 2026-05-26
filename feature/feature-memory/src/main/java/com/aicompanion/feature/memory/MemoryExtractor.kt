package com.aicompanion.feature.memory

import android.util.Log
import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.newId
import com.aicompanion.domain.model.ApiProvider
import com.aicompanion.domain.model.MemoryEntry
import com.aicompanion.domain.model.Message
private const val TAG = "MemoryExtractor"

/**
 * Hybrid memory extraction: LLM-based extraction + regex fallback.
 */
class MemoryExtractor {

    /** Regex fallback — synchronous, used when LLM extraction not available */
    fun extractFromText(text: String, personaId: String): List<MemoryEntry> =
        extractWithRegex(text, personaId)

    fun extractFromMessages(messages: List<Message>, personaId: String): List<MemoryEntry> {
        val combined = messages.filter { it.role == "user" }.takeLast(10)
            .joinToString("\n") { "用户: ${it.content}" }
        return extractWithRegex(combined, personaId)
    }

    /** LLM-based extraction. Call async after conversation turn. */
    suspend fun extractWithLLM(
        userMessages: List<String>,
        assistantResponses: List<String>,
        personaId: String,
        provider: ApiProvider
    ): List<MemoryEntry> {
        if (userMessages.isEmpty() || provider.apiKeyEncrypted.isBlank()) return emptyList()

        val combined = buildString {
            userMessages.takeLast(5).forEach { append("用户: $it\n") }
            assistantResponses.takeLast(2).forEach { append("AI: $it\n") }
        }

        val apiKey = try {
            CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { provider.apiKeyEncrypted }

        val prompt = buildString {
            append("从以下对话中提取关于「用户」的关键信息。\n")
            append("只输出JSON数组。每个元素: type(personal/preference/event/relationship), content(一句话), importance(0.0-1.0)\n\n")
            append("对话：\n$combined\n\nJSON:")
        }

        return try {
            val content = callLLM(provider.baseUrl, apiKey, provider.modelName, prompt)
            val clean = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val facts = try {
                val arr = org.json.JSONArray(clean)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val type = obj.optString("type", "personal")
                    val factContent = obj.optString("content", "")
                    val importance = obj.optDouble("importance", 0.5).toFloat()
                    Triple(type, factContent, importance)
                }
            } catch (_: Exception) { emptyList<Triple<String, String, Float>>() }

            facts.map { (type, factContent, importance) ->
                MemoryEntry(
                    id = newId(), personaId = personaId,
                    content = "[$type] $factContent",
                    importance = importance.coerceIn(0f, 1f),
                    confidence = 0.7f,
                    createdAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM extraction failed: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun callLLM(baseUrl: String, apiKey: String, model: String, prompt: String): String {
        val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") }
        val body = """{"model":"$model","messages":[{"role":"user","content":"${esc(prompt)}"}],"temperature":0.3,"max_tokens":400}"""

        val apiUrl = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val conn = (java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15000; readTimeout = 15000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val resp = conn.inputStream.use { it.bufferedReader().readText() }
        conn.disconnect()

        val json = org.json.JSONObject(resp)
        return json.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: "[]"
    }

    // === Regex fallback ===

    private data class RegexRule(val category: String, val importance: Float, val patterns: List<Regex>)

    private val rules = listOf(
        RegexRule("personal", 0.9f, listOf(
            Regex("我(叫|是)\\s*([^\\s，。！？,.!?]{2,20})"),
            Regex("我(今年)?\\s*(\\d{1,3})\\s*岁"),
            Regex("我(在|住在|生活在)\\s*([^\\s，。！？,.!?]{2,20})"),
            Regex("我(来自|从)\\s*([^\\s，。！？,.!?]{2,20})")
        )),
        RegexRule("preference", 0.7f, listOf(
            Regex("我(喜欢|爱好|爱吃|爱喝|特别喜欢|很喜欢|最喜欢)\\s*([^\\s，。！？,.!?]{2,30})"),
            Regex("我(不喜欢|讨厌|受不了)\\s*([^\\s，。！？,.!?]{2,30})")
        )),
        RegexRule("event", 0.6f, listOf(
            Regex("我(刚|最近|今天|昨天|上周)\\s*([^\\s，。！？,.!?]{3,40})"),
            Regex("我(要|准备|打算|计划)\\s*([^\\s，。！？,.!?]{3,40})")
        )),
        RegexRule("emotional", 0.5f, listOf(
            Regex("我(觉得|感觉|感到|好|很|非常)\\s*([^\\s，。！？,.!?]{2,20})")
        ))
    )

    private fun extractWithRegex(text: String, personaId: String): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        val seen = mutableSetOf<String>()
        for (rule in rules) {
            for (pattern in rule.patterns) {
                pattern.findAll(text).forEach { match ->
                    val content = match.groupValues.lastOrNull()?.trim()?.take(50) ?: return@forEach
                    if (content.length < 2) return@forEach
                    val tagged = "[${rule.category}] $content"
                    if (seen.add(tagged.lowercase())) {
                        results.add(MemoryEntry(
                            id = newId(), personaId = personaId, content = tagged,
                            importance = rule.importance, confidence = 0.5f,
                            createdAt = System.currentTimeMillis()
                        ))
                    }
                }
            }
        }
        return results
    }
}
