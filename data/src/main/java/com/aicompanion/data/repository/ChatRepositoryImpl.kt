package com.aicompanion.data.repository

import android.util.Log
import com.aicompanion.core.common.*
import com.aicompanion.core.database.dao.ConversationDao
import com.aicompanion.core.database.dao.MessageDao
import com.aicompanion.core.network.SSEClient
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.*
import com.aicompanion.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val sseClient: SSEClient
) : ChatRepository {

    override fun getConversations(): Flow<List<Conversation>> =
        conversationDao.getActiveConversations().map { list -> list.map { it.toDomain() } }

    override fun getMessages(conversationId: String): Flow<List<Message>> =
        messageDao.getMessages(conversationId).map { list -> list.map { it.toDomain() } }

    override suspend fun createConversation(
        personaId: String, apiProviderId: String, title: String
    ): Result<Conversation> {
        return try {
            val conv = Conversation(
                id = newId(), personaId = personaId, apiProviderId = apiProviderId,
                title = title, createdAt = now(), lastMessageAt = now()
            )
            conversationDao.insert(conv.toEntity())
            Result.Success(conv)
        } catch (e: Exception) {
            Result.Error("创建对话失败: ${e.message}", e)
        }
    }

    override suspend fun createFullConversation(conversation: Conversation): Result<Conversation> {
        return try {
            val existing = conversationDao.getById(conversation.id)
            if (existing != null) {
                conversationDao.update(conversation.toEntity())
            } else {
                conversationDao.insert(conversation.toEntity())
            }
            Result.Success(conversation)
        } catch (e: Exception) {
            Result.Error("保存对话失败: ${e.message}", e)
        }
    }

    override suspend fun saveMessage(message: Message) {
        messageDao.insert(message.toEntity())
    }

    override suspend fun getConversationById(id: String): Conversation? {
        return conversationDao.getById(id)?.toDomain()
    }

    override suspend fun sendMessage(
        conversationId: String, content: String, context: ChatContext
    ): Flow<Result<String>> = flow {
        // 1. Save user message (skip for group speaker turns)
        if (!context.isGroupSpeakerTurn) {
            val userMsg = Message(
                id = newId(), conversationId = conversationId,
                role = "user", content = content, createdAt = now()
            )
            messageDao.insert(userMsg.toEntity())
            conversationDao.updateLastMessage(conversationId, now(), content)
        }

        // 2. Build messages for API
        val apiMessages = buildApiMessages(context, content)

        // 3. Stream response
        val provider = context.provider
        val apiKey = try {
            CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { provider.apiKeyEncrypted }

        var fullResponse = ""
        try {
            Log.d("AIC", "SSE stream starting -> url=${provider.baseUrl.trimEnd('/')}, model=${provider.modelName}, apiKeyLen=${apiKey.length}")
            sseClient.streamChat(
                url = provider.baseUrl.trimEnd('/'),
                apiKey = apiKey,
                model = provider.modelName,
                messages = apiMessages,
                temperature = provider.temperature,
                maxTokens = provider.maxTokens
            ).collect { partial ->
                fullResponse = partial
                emit(Result.Success(partial))
            }
            Log.d("AIC", "SSE stream ended, fullResponse length=${fullResponse.length}")

            // 4. Save assistant message (skip for waifu mode & group speaker turns - ViewModel handles it)
            if (!context.isGroupSpeakerTurn && !context.persona.waifuMode) {
                val emotion = detectEmotion(fullResponse)
                val assistantMsg = Message(
                    id = newId(), conversationId = conversationId,
                    role = "assistant", content = fullResponse,
                    emotion = emotion, createdAt = now()
                )
                messageDao.insert(assistantMsg.toEntity())
            }
            conversationDao.updateLastMessage(conversationId, now(), fullResponse.take(200))
        } catch (e: Exception) {
            Log.e("AIC", "SSE stream exception: ${e.javaClass.simpleName}: ${e.message}", e)
            if (sseClient.isCancelled) {
                if (fullResponse.isNotEmpty()) {
                    val assistantMsg = Message(
                        id = newId(), conversationId = conversationId,
                        role = "assistant", content = fullResponse + " [已中断]",
                        createdAt = now()
                    )
                    messageDao.insert(assistantMsg.toEntity())
                }
            } else {
                emit(Result.Error("请求失败: ${e.message}", e))
            }
        }
    }

    override suspend fun stopGeneration() = sseClient.cancel()

    override suspend fun deleteConversation(conversationId: String) {
        conversationDao.archive(conversationId)
    }

    override suspend fun hardDeleteConversation(conversationId: String) {
        conversationDao.hardDelete(conversationId)
    }

    override suspend fun getChatContext(conversationId: String): Result<ChatContext> {
        // Built externally via use case coordination
        return Result.Error("Use orchestrated context building")
    }

    private fun buildApiMessages(context: ChatContext, currentMsg: String): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        val persona = context.persona

        // Layer 1: System prompt — short role statement (SillyTavern style)
        applySystemPrompt(messages, persona)

        // Layer 2: Character card — description fields
        applyCharacterCard(messages, context)

        // Layer 3: Example chats — separated section (SillyTavern style)
        applyExampleChats(messages, persona.exampleChats)

        // Layer 4: Chat guidance — rhythm, bans, mood
        applyChatGuidance(messages, context)

        // Layer 5: World book "before" entries
        val worldBookBefore = mutableListOf<String>()
        val worldBookAfter = mutableListOf<String>()
        collectWorldBookEntries(context, worldBookBefore, worldBookAfter)
        worldBookBefore.forEach { messages.add(mapOf("role" to "system", "content" to it)) }

        // Layer 6: Situational context
        applySituationalContext(messages, context)

        // Layer 7: Recent conversation history
        applyRecentMessages(messages, context)

        // Layer 8: World book "after" + Author's Note
        if (persona.authorsNote.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to "[Author's Note: ${persona.authorsNote}]"))
        }
        worldBookAfter.forEach { messages.add(mapOf("role" to "system", "content" to it)) }

        // Layer 9: System reminders — time awareness (AstrBot pattern)
        applySystemReminders(messages)

        // Layer 10: Mode-specific instructions
        if (persona.waifuMode) {
            messages.add(mapOf("role" to "system", "content" to
                "【重要】你在和人发消息聊天，不是写故事。\n" +
                "- 每条不超过20字，像微信聊天一样简短\n" +
                "- 用口语说话：说「好呀」不说「好的呢」，说「嗯嗯」不说「我明白了」\n" +
                "- 有话就说，没话就问。不要铺垫，不要叙述场景\n" +
                "- 禁止：任何形式的情节描述、环境描写、第三人称旁白\n" +
                "- emoji 每条约1个就够了"))
        } else if (persona.chuanYueMode) {
            messages.add(mapOf("role" to "system", "content" to
                "【穿越模式】你正和用户进行沉浸式互动。你们身处同一场景中，可以真实地互动。\n" +
                "- 你可以自由地、细致地用*动作*、表情、感受、环境细节来丰富互动\n" +
                "- 不限制篇幅长短，想写多少写多少，只要互动真实自然\n" +
                "- 行动和对话自然地交织，像真正的面对面相处\n" +
                "- 用户会描述ta的行为、场景、细节，你始终融入其中并自然地回应\n" +
                "- 保持你的角色性格和身份，把你的特质融入每一个动作和每一句话里"))
        }

        messages.add(mapOf("role" to "user", "content" to currentMsg))
        return messages
    }

    // ── Layer helpers ──

    /** Single consolidated persona prompt — role + character + rules in one message */
    private fun applySystemPrompt(messages: MutableList<Map<String, String>>, persona: com.aicompanion.domain.model.Persona) {
        val content = "你是${persona.name}。正在和用户一对一发消息聊天。\n\n" +
            (if (persona.chuanYueMode)
                "【模式】沉浸式互动。你正和用户在同一场景中相处。你可以自然且细致地用*动作*描述你在做什么、你的表情、你感受到的、你周围的一切。篇幅不限，尽情描写。你的每一个动作、每一句话都是你角色身份的自然流露。\n\n"
            else
                "【重要】你不是在写故事。直接说话，不要叙述场景、描写环境、第三人称旁白。\n" +
                "严禁：星号*动作*、括号（心理）、【描写】。不要用「好的呢」「没问题」开头。\n\n"
            ) +
            persona.systemPrompt
        messages.add(mapOf("role" to "system", "content" to content))
    }

    /** Character card — scenario, relationship, speaking style */
    private fun applyCharacterCard(messages: MutableList<Map<String, String>>, ctx: ChatContext) {
        val p = ctx.persona
        val parts = mutableListOf<String>()
        if (p.scenario.isNotBlank()) parts.add("场景：${p.scenario}")
        if (p.relationshipType.isNotBlank()) {
            var rel = "和用户的关系：${p.relationshipType}"
            if (p.userDisplayName.isNotBlank()) rel += "，用户叫「${p.userDisplayName}」"
            parts.add(rel)
        }
        if (p.speakingStyle.isNotBlank()) parts.add("说话方式：${p.speakingStyle}")
        if (parts.isEmpty()) return
        messages.add(mapOf("role" to "system", "content" to parts.joinToString("\n") { "$it。" }))
    }

    /** Example chats as separate section */
    private fun applyExampleChats(messages: MutableList<Map<String, String>>, examples: List<String>) {
        if (examples.isEmpty()) return
        messages.add(mapOf("role" to "system", "content" to
            "以下是你之前的对话示例：\n---\n${examples.joinToString("\n")}\n---"))
    }

    /** Chat guidance — rhythm tips, mood (no duplicate bans) */
    private fun applyChatGuidance(messages: MutableList<Map<String, String>>, ctx: ChatContext) {
        val p = ctx.persona
        val parts = mutableListOf("回复有长有短，偶尔主动提问。")
        if (!p.waifuMode && !p.chuanYueMode) parts.add("不要问「还有什么想聊的吗」。")
        if (ctx.conversationMood != "neutral") {
            val hint = when (ctx.conversationMood) {
                "happy" -> "对方心情不错，你可以活泼一点"
                "sad" -> "对方心情似乎不太好，先共情，别急着讲道理"
                "angry" -> "对方在生气，先表示理解"
                "excited" -> "对方很兴奋，一起开心"
                "flat" -> "对方话不多，主动找话题"
                else -> ""
            }
            if (hint.isNotBlank()) parts.add(hint)
        }
        messages.add(mapOf("role" to "system", "content" to parts.joinToString(" ") { "$it" }))
    }

    private fun collectWorldBookEntries(ctx: ChatContext, before: MutableList<String>, after: MutableList<String>) {
        if (ctx.worldBookEntries.isEmpty()) return
        val recentUserText = ctx.messages.takeLast(10)
            .filter { it.role == "user" }
            .joinToString(" ") { it.content }
            .lowercase()
        for (entry in ctx.worldBookEntries.filter { it.enabled }.sortedByDescending { it.priority }) {
            val triggered = entry.constant || entry.keywords.any { recentUserText.contains(it.lowercase()) }
                || entry.secondaryKeywords.any { recentUserText.contains(it.lowercase()) }
            if (!triggered) continue
            val text = "【${entry.key}】\n${entry.content}"
            if (entry.position == "after") after.add(text) else before.add(text)
        }
    }

    private fun applySituationalContext(messages: MutableList<Map<String, String>>, ctx: ChatContext) {
        // Proactive message reminder
        if (!ctx.isGroupSpeakerTurn) {
            val lastMsg = ctx.messages.lastOrNull()
            if (lastMsg != null && lastMsg.role == "assistant") {
                messages.add(mapOf("role" to "system", "content" to
                    "注意：你刚才主动给用户发了一条消息，现在用户回复你了。这不是新话题的开始，请自然地接上你刚才说的话，继续聊下去。"))
            }
        }
        // Group chat
        if (ctx.groupPersonas.isNotEmpty()) {
            val names = ctx.groupPersonas.joinToString("、") { it.name }
            messages.add(mapOf("role" to "system", "content" to
                "你正在一个群聊中发言。群里还有：$names。只以你的角色身份说话，不要替其他人发言。"))
        }
        // Memories
        if (ctx.memories.isNotEmpty()) {
            val text = ctx.memories.joinToString("；") { it.content.replace(Regex("^\\[\\w+\\]\\s*"), "") }
            messages.add(mapOf("role" to "system", "content" to "关于用户: $text"))
        }
        // User profile (compact, from persona)
        val profile = ctx.persona.userProfile
        if (profile != null && (profile.name != null || profile.facts.isNotEmpty() || profile.preferences.isNotEmpty())) {
            val parts = mutableListOf<String>()
            if (profile.name != null) parts.add("用户叫${profile.name}")
            parts.addAll(profile.facts)
            parts.addAll(profile.preferences)
            val profileText = parts.joinToString("；")
            messages.add(mapOf("role" to "system", "content" to "关于用户: $profileText"))
        }
        // Persona's recent moments (so AI knows what it posted on 朋友圈)
        val recentMoments = ctx.persona.moments.takeLast(3)
        if (recentMoments.isNotEmpty()) {
            val text = recentMoments.joinToString("；") { it.content }
            messages.add(mapOf("role" to "system", "content" to "你最近发过的朋友圈: $text"))
        }
        // Persona's recent experiences
        val recentExps = ctx.persona.experiences.takeLast(2)
        if (recentExps.isNotEmpty()) {
            val text = recentExps.joinToString("；") { "${it.title}: ${it.content.take(80)}" }
            messages.add(mapOf("role" to "system", "content" to "你最近的经历: $text"))
        }
        // Reply target
        if (!ctx.replyTargetContent.isNullOrBlank()) {
            val prompt = if (!ctx.replyTargetSenderName.isNullOrBlank()) {
                "用户引用了${ctx.replyTargetSenderName}的消息：「${ctx.replyTargetContent}」。请以你的角色身份直接回应这个引用。"
            } else {
                "用户引用了你之前说的这句话：「${ctx.replyTargetContent}」。请直接回应这句话。"
            }
            messages.add(mapOf("role" to "system", "content" to prompt))
        }
    }

    private fun applyRecentMessages(messages: MutableList<Map<String, String>>, ctx: ChatContext) {
        ctx.messages.takeLast(Constants.DEFAULT_CONTEXT_WINDOW * 2).forEach { msg ->
            messages.add(mapOf("role" to msg.role, "content" to msg.content))
        }
    }

    /** Inject time awareness so AI naturally adapts tone (AstrBot <system_reminder> pattern). */
    private fun applySystemReminders(messages: MutableList<Map<String, String>>) {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val hour = now.hour
        val timeHint = when {
            hour in 5..7 -> "现在是清晨"
            hour in 8..11 -> "现在是上午"
            hour in 12..13 -> "现在是中午"
            hour in 14..17 -> "现在是下午"
            hour in 18..22 -> "现在是晚上"
            else -> "现在是深夜"
        }
        messages.add(mapOf("role" to "system", "content" to
            "<system_reminder>\n$timeHint，当前时间：$timeStr (CST)\n</system_reminder>"))
    }

    private fun detectEmotion(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("哈哈") || lower.contains("开心") || lower.contains("😊") -> "happy"
            lower.contains("难过") || lower.contains("伤心") || lower.contains("😢") -> "sad"
            lower.contains("生气") || lower.contains("可恶") || lower.contains("😠") -> "angry"
            lower.contains("哇") || lower.contains("天哪") || lower.contains("😲") -> "surprised"
            lower.contains("害羞") || lower.contains("⁄") || lower.contains("😳") -> "shy"
            lower.contains("嗯") && lower.length < 10 -> "thinking"
            else -> "neutral"
        }
    }
}
