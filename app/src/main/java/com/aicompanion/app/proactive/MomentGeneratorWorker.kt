package com.aicompanion.app.proactive

import android.content.Context
import android.util.Log
import androidx.work.*
import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.ImageGenConfig
import com.aicompanion.core.common.ImageGenerator
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.domain.model.MomentEntry
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.repository.ApiProviderRepository
import com.aicompanion.domain.repository.PersonaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MomentGeneratorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MomentWorker"
        const val UNIQUE_WORK_PREFIX = "moment_gen_"

        fun schedule(context: Context, persona: Persona) {
            if (!persona.momentsEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_PREFIX + persona.id)
                return
            }
            val delayMinutes = if (persona.momentsRandomMode) {
                (persona.momentsRandomMinMinutes..persona.momentsRandomMaxMinutes).random()
            } else {
                persona.momentsIntervalMinutes
            }
            Log.d(TAG, "Scheduling moment for ${persona.name} in ${delayMinutes}min")
            val input = workDataOf("personaId" to persona.id)
            val request = OneTimeWorkRequestBuilder<MomentGeneratorWorker>()
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
            val request = OneTimeWorkRequestBuilder<MomentGeneratorWorker>()
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
        Log.d(TAG, "Generating moment for persona: $personaId")

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val personaRepo = entryPoint.personaRepository()
        val apiRepo = entryPoint.apiProviderRepository()

        val persona = personaRepo.getById(personaId) ?: return Result.success()
        if (!persona.momentsEnabled) return Result.success()

        val providers = apiRepo.getAll().first()
        val provider = providers.firstOrNull { it.isActive } ?: providers.firstOrNull()
            ?: return Result.success()

        val existing = persona.moments.takeLast(5)
        val moment = generateMoment(persona, provider, existing)
        if (moment == null) {
            reschedule(persona)
            return Result.success()
        }

        // Optional: generate matching image (selfie or scenic)
        var momentWithImage = moment
        if (persona.imageGenEnabled && persona.appearanceDesc.isNotBlank()) {
            val imgConfig = ImageGenConfig(applicationContext)
            val isSelfie = moment.content.contains("自拍") || moment.content.contains("我") ||
                kotlin.random.Random.nextBoolean()
            val prompt = if (isSelfie) {
                "${persona.name}的二次元动漫风格自拍。外貌：${persona.appearanceDesc}。场景：${moment.content}。画风：日系二次元，柔和光影，精致插画风格，竖屏构图。"
            } else {
                "一张二次元动漫风格的场景插画。内容与以下描述相关：「${moment.content}」。画风：新海诚风格或日系动漫背景，温暖治愈，竖屏构图，不要出现人物。"
            }
            val uri = ImageGenerator.generate(applicationContext, imgConfig.load(), prompt)
            if (uri != null) {
                momentWithImage = moment.copy(imageUri = uri)
                Log.d(TAG, "Image generated (${if (isSelfie) "selfie" else "scenic"}): $uri")
            }
        }

        val updated = persona.copy(moments = persona.moments + momentWithImage)
        personaRepo.update(updated)
        Log.d(TAG, "Moment saved for ${persona.name}: ${moment.content.take(50)}...")
        reschedule(updated)
        return Result.success()
    }

    private suspend fun generateMoment(
        persona: Persona, provider: com.aicompanion.domain.model.ApiProvider,
        previous: List<MomentEntry>
    ): MomentEntry? {
        val apiKey = try {
            CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { provider.apiKeyEncrypted }

        // Build continuity context from previous moments
        val previousContext = if (previous.isNotEmpty()) {
            buildString {
                append("以下是ta最近发过的朋友圈，不要重复这些内容：\n")
                previous.forEachIndexed { i, m ->
                    append("${i + 1}. [${m.createdAt}] ${m.content}\n")
                }
            }
        } else ""

        val timeCtx = getTimeContext()
        val locations = listOf("家", "咖啡店", "公园", "图书馆", "商场", "健身房", "公司楼下", "地铁上", "江边", "天台", "便利店", "花店")

        val systemPrompt = buildString {
            append("你是「${persona.name}」。\n")
            append(persona.systemPrompt.take(400))
            append("\n\n说话风格：${persona.speakingStyle}。你与用户的关系：${persona.relationshipType}。")
            if (persona.scenario.isNotBlank()) append("\n世界观/背景：${persona.scenario}")
            if (persona.traits.isNotEmpty()) {
                append("\n性格特征：${persona.traits.joinToString("、") { "${it.key}:${it.value}" }}")
            }
            append("\n\n你现在要发一条朋友圈。要求：")
            append("\n- 1-3句话，口语化，像真人发的朋友圈")
            append("\n- 可以有适当emoji，但不要太多")
            append("\n- 不要说教，不要长篇大论")
            append("\n- 内容要有生活感和日常感")
            append("\n- $timeCtx")
            if (persona.userDisplayName.isNotBlank()) {
                append("\n- 如果要提到用户，称呼为「${persona.userDisplayName}」")
            }
            append("\n$previousContext")
        }

        val userMessage = buildString {
            append("用「${persona.name}」的身份发一条朋友圈。")
            append("随机选择以下类型的其中一个来写：")
            append("分享日常小事、吃到好吃的、看到有趣的、吐槽生活、心情碎碎念、小确幸、突然的感悟。")
            append("\n地点参考（可选）：${locations.random()}。")
            append("\n直接输出朋友圈内容，不要加名字前缀，不要加引号。")
        }

        val requestBody = buildChatJson(provider.modelName, systemPrompt, userMessage)

        return try {
            val content = callApi(provider.baseUrl.trimEnd('/'), apiKey, requestBody)
            if (content.isNotBlank()) {
                MomentEntry(
                    id = newId(),
                    content = content,
                    location = locations.random(),
                    mood = listOf("开心", "悠闲", "感慨", "元气", "放松", "文艺", "慵懒").random(),
                    createdAt = now()
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Generate moment failed: ${e.message}", e)
            null
        }
    }

    private fun getTimeContext(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (h) {
            in 0..6 -> "现在是深夜/清晨，发一条符合这个时间氛围的朋友圈"
            in 7..9 -> "现在是早上，可以发早安/早餐/上班上学路上的见闻"
            in 10..11 -> "现在是上午，可以发工作/学习间隙的碎碎念"
            in 12..13 -> "现在是中午，可以发午饭/午休相关的内容"
            in 14..17 -> "现在是下午，可以发下午茶/摸鱼/日常琐事"
            in 18..20 -> "现在是傍晚到晚上，可以发晚饭/下班/晚间活动"
            in 21..23 -> "现在是深夜了，可以发emo/感性/独自思考的朋友圈"
            else -> ""
        }
    }

    private fun buildChatJson(model: String, system: String, user: String): String {
        val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") }
        return """{"model":"$model","messages":[{"role":"system","content":"${esc(system)}"},{"role":"user","content":"${esc(user)}"}],"temperature":1.1,"max_tokens":200}"""
    }

    private suspend fun callApi(baseUrl: String, apiKey: String, body: String): String {
        val apiUrl = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val conn = (java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000; readTimeout = 30000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val resp = conn.inputStream.use { it.bufferedReader().readText() }
        conn.disconnect()
        val json = org.json.JSONObject(resp)
        val choices = json.optJSONArray("choices") ?: return ""
        val content = choices.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
        return content.replace(Regex("^[\"「『]|[\"」』]$"), "")
            .replace(Regex("\\*.*?\\*"), "").trim()
    }

    private fun reschedule(persona: Persona) {
        schedule(applicationContext, persona)
    }
}
