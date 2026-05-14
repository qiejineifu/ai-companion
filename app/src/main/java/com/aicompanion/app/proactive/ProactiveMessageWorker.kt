package com.aicompanion.app.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.aicompanion.app.MainActivity
import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.ProactiveSettings
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.ConversationDao
import com.aicompanion.core.database.dao.MessageDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.Conversation
import com.aicompanion.domain.model.Message
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.repository.ApiProviderRepository
import com.aicompanion.domain.repository.PersonaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveWorker"
        private const val CHANNEL_ID = "proactive_messages"
        private const val CHANNEL_NAME = "AI主动消息"
        private const val NOTIFICATION_ID = 2001
        const val UNIQUE_WORK_NAME = "proactive_message_loop"

        fun schedule(context: Context, settings: ProactiveSettings) {
            if (!settings.enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val delayMinutes = settings.getNextDelayMinutes()
            Log.d(TAG, "Scheduling proactive message in $delayMinutes minutes (mode=${settings.mode})")
            val request = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun personaRepository(): PersonaRepository
        fun apiProviderRepository(): ApiProviderRepository
        fun conversationDao(): ConversationDao
        fun messageDao(): MessageDao
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== Proactive message worker started ===")
        val settings = ProactiveSettings(applicationContext)

        if (!settings.enabled) {
            Log.d(TAG, "Disabled, exiting")
            return Result.success()
        }

        // Get Hilt dependencies
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val personaRepository = entryPoint.personaRepository()
        val apiProviderRepository = entryPoint.apiProviderRepository()

        // Get personas - filter by selection if any are selected
        val allPersonas = personaRepository.getAll().first()
        val eligiblePersonas = if (settings.selectedPersonaIds.isNotEmpty()) {
            allPersonas.filter { it.id in settings.selectedPersonaIds }
        } else {
            allPersonas
        }
        if (eligiblePersonas.isEmpty()) {
            Log.d(TAG, "No eligible personas available")
            reschedule(settings)
            return Result.success()
        }
        Log.d(TAG, "Found ${allPersonas.size} personas, ${eligiblePersonas.size} eligible")

        // Pick a random persona
        val persona = eligiblePersonas.random()
        Log.d(TAG, "Selected persona: ${persona.name}")

        // Get active API provider
        val providers = apiProviderRepository.getAll().first()
        val provider = providers.firstOrNull { it.isActive } ?: providers.firstOrNull()
        if (provider == null || provider.apiKeyEncrypted.isBlank()) {
            Log.d(TAG, "No API provider configured")
            reschedule(settings)
            return Result.success()
        }
        Log.d(TAG, "Using provider: ${provider.name}, model: ${provider.modelName}")

        // Generate proactive message via API
        Log.d(TAG, "Calling AI API...")
        val message = generateMessage(persona, provider)
        if (message == null) {
            Log.e(TAG, "Failed to generate message")
            reschedule(settings)
            return Result.success()
        }
        Log.d(TAG, "Got message: $message")

        // Save message to the persona's latest conversation
        val convDao = entryPoint.conversationDao()
        val msgDao = entryPoint.messageDao()
        val latestConv = convDao.getActiveConversations().first()
            .firstOrNull { it.personaId == persona.id && !it.isGroupChat }
        val convToUse = if (latestConv != null) {
            latestConv
        } else {
            // Create a new conversation for this persona if none exists
            val newConv = com.aicompanion.core.database.entity.ConversationEntity(
                id = newId(), personaId = persona.id,
                apiProviderId = provider.id,
                title = "与${persona.name}的日常聊天",
                lastMessagePreview = message.take(200),
                createdAt = now(), lastMessageAt = now()
            )
            convDao.insert(newConv)
            newConv
        }
        // Save the AI message
        val msg = Message(
            id = newId(), conversationId = convToUse.id,
            role = "assistant", content = message, createdAt = now()
        )
        msgDao.insert(msg.toEntity())
        convDao.updateLastMessage(convToUse.id, now(), message.take(200))
        Log.d(TAG, "Message saved to conversation: ${convToUse.id}")

        // Show notification
        showNotification(persona, message, convToUse.id)
        Log.d(TAG, "Notification shown, rescheduling")

        // Reschedule
        reschedule(settings)
        return Result.success()
    }

    private fun getTimeContext(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..6 -> "现在是深夜或清晨。"
            in 7..9 -> "现在是早上。"
            in 10..11 -> "现在是上午。"
            in 12..13 -> "现在是中午，午饭时间。"
            in 14..17 -> "现在是下午。"
            in 18..20 -> "现在是傍晚到晚上。"
            in 21..23 -> "现在是深夜了。"
            else -> ""
        }
    }

    private fun getScenarioPrompt(): String {
        val scenarios = listOf(
            // 分享日常
            "你刚刚做了一件有趣的事，想跟用户分享一下。可以是看到好玩的、吃到好吃的、路上遇到的事，或者生活中的小确幸。发一条简短的消息，像朋友发微信一样。",
            "你观察到了身边发生的一个小细节，觉得有意思，想跟用户说说。",
            "你突然想起今天发生的一件事，觉得用户会感兴趣，想告诉ta。",
            // 关心用户
            "你突然想到用户，不知道ta现在在干什么，想问问ta。表达你的关心，但不要像查岗。",
            "你注意到这个时间点，用户可能该吃饭了/该休息了/忙了一天了，发一条消息关心一下。",
            "你感觉到用户可能心情不太好，想用轻松的方式问问ta的情况。",
            // 表达心情
            "你现在有一个强烈的情绪（可以是开心、无聊、emo、兴奋、感动...），想跟用户聊聊。直接表达你的心情，像跟朋友倾诉一样。",
            "你今天心情特别好，想找人分享这份快乐。发一条元气满满的消息。",
            "你现在有点小情绪（不是生气），想用撒娇或吐槽的方式跟用户说。",
            // 发起话题
            "你脑子里突然冒出一个有趣的话题想跟用户讨论。可以是关于音乐、电影、游戏、美食、旅行、星座、最近的热梗，或者你刚看到的一条新闻。",
            "你刚看到一个好玩的东西（比如一张图、一段话、一个视频），想跟用户聊聊这个话题。",
            "你突然想跟用户玩个小游戏或者问ta一个有趣的问题。",
            // 撒娇/互动
            "你现在想要用户关注你。发一条可爱/有趣/撒娇的消息吸引ta回复，但不要肉麻。",
            "你有点无聊，想找用户聊天。用轻松的语气表达你想ta了。",
            "你想让用户跟你互动，可以问ta一个简单的选择题或者让ta帮你做个小决定。",
            // 回忆过去
            "你想起之前和用户聊过的某件事，觉得有趣，想提起来继续聊。不需要真的记得具体内容，用模糊的方式说「记得之前聊过...」就好。",
            "你回忆起你和用户之间发生过的暖心小事（可以模糊地说），想表达你的珍惜。"
        )
        return scenarios.random()
    }

    private suspend fun generateMessage(persona: Persona, provider: com.aicompanion.domain.model.ApiProvider): String? {
        val apiKey = try {
            CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { provider.apiKeyEncrypted }

        val timeCtx = getTimeContext()
        val scenario = getScenarioPrompt()

        // Build persona-specific tone guidance
        val personaTone = buildString {
            append(persona.systemPrompt.take(500))
            append("\n\n你的说话风格是「${persona.speakingStyle}」，你和用户的关系是「${persona.relationshipType}」。")
            if (persona.userDisplayName.isNotBlank()) {
                append("你称呼用户为「${persona.userDisplayName}」。")
            }
            if (persona.traits.isNotEmpty()) {
                append("你的性格特征包括：${persona.traits.joinToString("、") { "${it.key}:${it.value}" }}。")
            }
        }

        val systemPrompt = buildString {
            append(personaTone)
            append("\n\n你现在正在后台主动给用户发消息。这不是用户要求的回复，而是你自发想跟用户说话。")
            append("\n场景要求：$scenario")
            append("\n\n消息规则：")
            append("\n- 用你的说话风格和性格，只说一句话，像真人发微信消息，自然口语化")
            append("\n- 控制在50字以内")
            append("\n- 不要用角色扮演格式，不要加\\*动作描写\\*")
            append("\n- 直接说话，可以带语气词，可以带emoji但不要多")
            append("\n- $timeCtx")
        }

        val userHint = buildString {
            append(timeCtx)
            append(scenario)
            append("\n记住你是「${persona.speakingStyle}」风格，和用户的关系是「${persona.relationshipType}」。")
            append("\n用你的角色身份和性格，发一条简短自然的消息。只输出消息内容本身，不要加任何前缀或解释。")
        }

        val escapedSystemPrompt = systemPrompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val requestBody = """{"model":"${provider.modelName}","messages":[{"role":"system","content":"$escapedSystemPrompt"},{"role":"user","content":"${userHint.replace("\"", "\\\"").replace("\n", "\\n")}"}],"temperature":1.1,"max_tokens":150}"""

        return try {
            val base = provider.baseUrl.trimEnd('/')
            val apiUrl = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val url = java.net.URL(apiUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }
            conn.outputStream.use { it.write(requestBody.toByteArray()) }
            val response = conn.inputStream.use { it.bufferedReader().readText() }
            conn.disconnect()

            val json = org.json.JSONObject(response)
            val choices = json.optJSONArray("choices")
            val content = if (choices != null && choices.length() > 0) {
                choices.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            } else ""

            val cleaned = content.trim()
                .replace(Regex("^[\"「『]|[\"」』]$"), "")
                .replace(Regex("\\*.*?\\*"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .trim()
            if (cleaned.isNotBlank()) cleaned else null
        } catch (e: Exception) {
            Log.e(TAG, "Generate message failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun showNotification(persona: Persona, message: String, convId: String) {
        createChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_persona", persona.id)
            putExtra("navigate_to_conv", convId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, persona.id.hashCode(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${persona.name} 发来消息")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(
                persona.id, NOTIFICATION_ID, notification
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification permission")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI主动发来的消息"
                setShowBadge(true)
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun reschedule(settings: ProactiveSettings) {
        schedule(applicationContext, settings)
    }
}
