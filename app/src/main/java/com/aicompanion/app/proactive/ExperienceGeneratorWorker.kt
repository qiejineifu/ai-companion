package com.aicompanion.app.proactive

import android.content.Context
import android.util.Log
import androidx.work.*
import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.domain.model.ExperienceEntry
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.repository.ApiProviderRepository
import com.aicompanion.domain.repository.PersonaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ExperienceGeneratorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ExperienceWorker"
        const val UNIQUE_WORK_PREFIX = "exp_gen_"

        fun schedule(context: Context, persona: Persona) {
            if (!persona.experiencesEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_PREFIX + persona.id)
                return
            }
            val delayMinutes = if (persona.experiencesRandomMode) {
                (persona.experiencesRandomMinMinutes..persona.experiencesRandomMaxMinutes).random()
            } else {
                persona.experiencesIntervalMinutes
            }
            Log.d(TAG, "Scheduling experience for ${persona.name} in ${delayMinutes}min")
            val input = workDataOf("personaId" to persona.id)
            val request = OneTimeWorkRequestBuilder<ExperienceGeneratorWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(input)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_PREFIX + persona.id, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancel(context: Context, personaId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_PREFIX + personaId)
        }

        fun generateNow(context: Context, personaId: String) {
            val input = workDataOf("personaId" to personaId)
            val request = OneTimeWorkRequestBuilder<ExperienceGeneratorWorker>()
                .setInputData(input)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun personaRepository(): PersonaRepository
        fun apiProviderRepository(): ApiProviderRepository
    }

    override suspend fun doWork(): Result {
        val personaId = inputData.getString("personaId") ?: return Result.success()
        Log.d(TAG, "Generating experience for persona: $personaId")

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val personaRepo = entryPoint.personaRepository()
        val apiRepo = entryPoint.apiProviderRepository()

        val persona = personaRepo.getById(personaId) ?: return Result.success()
        if (!persona.experiencesEnabled) return Result.success()

        val providers = apiRepo.getAll().first()
        val provider = providers.firstOrNull { it.isActive } ?: providers.firstOrNull()
            ?: return Result.success()

        val existing = persona.experiences.takeLast(3)
        val experience = generateExperience(persona, provider, existing)
        if (experience == null) {
            reschedule(persona)
            return Result.success()
        }

        val updated = persona.copy(experiences = persona.experiences + experience)
        personaRepo.update(updated)
        Log.d(TAG, "Experience saved for ${persona.name}: ${experience.title}")
        reschedule(updated)
        return Result.success()
    }

    private suspend fun generateExperience(
        persona: Persona, provider: com.aicompanion.domain.model.ApiProvider,
        previous: List<ExperienceEntry>
    ): ExperienceEntry? {
        val apiKey = try {
            CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { provider.apiKeyEncrypted }

        val previousContext = if (previous.isNotEmpty()) {
            buildString {
                append("以下是ta最近的经历，不要重复这些事件，可以继续发展或引出新事件：\n")
                previous.forEachIndexed { i, exp ->
                    append("${i + 1}. [${exp.title}] ${exp.content.take(150)}...\n")
                }
            }
        } else ""

        val categories = listOf(
            "日常" to "平凡的日常生活片段，温馨或有趣的小事",
            "冒险" to "遇到挑战或主动尝试新事物，有波折有收获",
            "奇遇" to "意外遇到了特别的人或事，带有一些巧合和惊喜",
            "回忆" to "想起了过去的某段经历，触发了情感和感悟",
            "成长" to "心态或能力上的变化和进步，体现了角色的成长弧光"
        )
        val chosenCategory = categories.random()

        val systemPrompt = buildString {
            append("你是「${persona.name}」。\n")
            append(persona.systemPrompt.take(500))
            append("\n\n说话风格：${persona.speakingStyle}。性格特征：")
            append(persona.traits.joinToString("、") { "${it.key}:${it.value}" })
            if (persona.scenario.isNotBlank()) append("\n世界观/背景设定：${persona.scenario}")
            append("\n\n你要写一段关于这个人最近经历的第三人称叙述。要求：")
            append("\n- 用第三人称（「${persona.name}」或「ta」），像在讲述一个人的近况")
            append("\n- 1-2个自然段，有具体的时间、地点、细节，让读者觉得真实")
            append("\n- 风格参考：生活中真实发生的小故事，不夸张不做作")
            append("\n- 本次经历类型：${chosenCategory.first}——${chosenCategory.second}")
            append("\n- 结尾可以留一个开放式的悬念或情感余韵，让人想继续关注")
            append("\n$previousContext")
            append("\n确保这篇新经历和之前的经历有连续的时间感，不要突然跳跃或重复。")
        }

        val userMessage = buildString {
            append("写一段「${persona.name}」的第三人称经历。")
            append("\n类型：${chosenCategory.first}")
            append("\n要求：有标题（4-8字），正文1-2段。不要用markdown格式。")
            append("\n\n输出格式：")
            append("\n第一行：标题")
            append("\n空一行")
            append("\n正文内容")
        }

        val requestBody = buildString {
            val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") }
            append("""{"model":"${provider.modelName}","messages":[{"role":"system","content":"${esc(systemPrompt)}"},{"role":"user","content":"${esc(userMessage)}"}],"temperature":1.0,"max_tokens":400}""")
        }

        return try {
            val apiUrl = provider.baseUrl.trimEnd('/')
            val url = if (apiUrl.endsWith("/chat/completions")) apiUrl else "$apiUrl/chat/completions"
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30000; readTimeout = 45000
            }
            conn.outputStream.use { it.write(requestBody.toByteArray()) }
            val resp = conn.inputStream.use { it.bufferedReader().readText() }
            conn.disconnect()

            val json = org.json.JSONObject(resp)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content") ?: ""

            // Parse title from first line, rest is body
            val lines = content.trim().split("\n").filter { it.isNotBlank() }
            val title = lines.firstOrNull()?.trim()?.take(30) ?: "${chosenCategory.first}的一日"
            val body = if (lines.size > 1) lines.drop(1).joinToString("\n").trim()
            else lines.joinToString("\n").trim()

            if (body.isNotBlank()) {
                ExperienceEntry(
                    id = newId(),
                    title = title,
                    content = body,
                    category = chosenCategory.first,
                    createdAt = now()
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Generate experience failed: ${e.message}", e)
            null
        }
    }

    private fun reschedule(persona: Persona) {
        schedule(applicationContext, persona)
    }
}
