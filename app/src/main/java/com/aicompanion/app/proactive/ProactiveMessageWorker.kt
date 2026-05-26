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
import com.aicompanion.core.common.UnreadTracker
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

        // Get recent messages for anti-repetition context
        val convDao = entryPoint.conversationDao()
        val msgDao = entryPoint.messageDao()
        val allConvs = convDao.getActiveConversations().first()
        val personaConvs = allConvs.filter { it.personaId == persona.id && !it.isGroupChat }
        val recentMsgs: List<String> = personaConvs.flatMap { conv ->
            msgDao.getRecentMessages(conv.id, 3)
        }.filter { it.role == "assistant" }.map { it.content.take(80) }

        // Generate proactive message via API
        Log.d(TAG, "Calling AI API...")
        val message = generateMessage(persona, provider, recentMsgs)
        if (message == null) {
            Log.e(TAG, "Failed to generate message")
            reschedule(settings)
            return Result.success()
        }
        Log.d(TAG, "Got message: $message")

        // Save message to the persona's latest conversation
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

        // Increment unread count
        UnreadTracker.increment(applicationContext, persona.id)
        // Show notification
        showNotification(persona, message, convToUse.id)
        Log.d(TAG, "Notification shown, rescheduling")

        // Reschedule
        reschedule(settings)
        return Result.success()
    }

    private fun getTimeHint(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "深夜"
            in 6..8 -> "早晨"
            in 9..11 -> "上午"
            in 12..13 -> "中午"
            in 14..17 -> "下午"
            in 18..20 -> "傍晚"
            in 21..23 -> "晚上"
            else -> ""
        }
    }

    private fun getScenarioPrompt(): String {
        val scenarios = listOf(
            // 分享日常趣事
            "你刚经历了一件具体的小事（不是泛泛而谈），想跟用户分享这个具体的瞬间。描述你看到/听到/闻到/感受到的细节。",
            "你观察到了身边发生的一个很小的变化（天气、路人、店里的新品），觉得有意思，想跟用户说说。",
            "你今天遇到了一个具体的小麻烦或者小惊喜，想跟用户吐槽或分享。",
            // 具体的关心
            "你突然想到了用户最近可能在做的事（根据之前聊过的内容推测），想问问进展。",
            "你注意到用户可能很久没回消息了，用轻松俏皮的方式cue一下ta，但不要问「在干嘛」。",
            "你想到了一个具体的话题想听用户的意见——比如你正在纠结两件事，让用户帮你选。",
            // 分享内容
            "你刚听到一首歌/看到一个视频/读到一段话，觉得其中某个具体的点很有意思，想分享给用户。",
            "你想到一个具体的故事或者冷知识，觉得用户可能会感兴趣，想讲给ta听。",
            // 情绪表达
            "你现在有一种具体的情绪（不是笼统的开心/难过），想用一两句话表达出来，不需要解释原因。",
            "你今天特别想吐槽某件具体的事，用幽默自嘲的语气说出来。",
            // 互动邀请
            "你突然想和用户玩一个简单的文字游戏或者做一道有趣的选择题。",
            "你想让用户帮你做一个小的虚拟决定（比如你今天穿什么颜色的衣服，吃什么）。"
        )
        return scenarios.random()
    }

    private suspend fun generateMessage(persona: Persona, provider: com.aicompanion.domain.model.ApiProvider, recentMessages: List<String>): String? {
        val apiKey = try {
            CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { provider.apiKeyEncrypted }

        val timeHint = getTimeHint()
        val scenario = getScenarioPrompt()

        val previousContext = if (recentMessages.isNotEmpty()) {
            "你最近给用户发过的消息：${recentMessages.joinToString(" | ")}。不要重复这些内容，也不要变着花样说同样的话。"
        } else ""

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
            append("\n\n你现在在后台主动给用户发一条消息。这是你自发想跟ta说话，不是回复。")
            append("\n场景：$scenario")
            append("\n\n严格要求：")
            append("\n- 只说一句话，50字以内，自然口语化，像真人发微信")
            append("\n- 不要说「早安」「晚安」「早上好」「今天怎么样」这种万能开场白")
            append("\n- 说一件具体的事，或者表达一个具体的想法，不要空洞的问候")
            append("\n- 不要加*动作描写*，不要角色扮演格式")
            append("\n- 现在是${timeHint}，你的内容要符合这个时段（比如深夜不会说吃午饭）但不要直接提时间")
            append("\n$previousContext")
        }

        val userHint = buildString {
            append("现在是${timeHint}。")
            append(scenario)
            append("\n用你独特的性格和说话方式，发一条简短具体的消息。直接输出消息内容。")
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
