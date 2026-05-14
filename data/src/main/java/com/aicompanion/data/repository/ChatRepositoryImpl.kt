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

            // 4. Save assistant message (skip for group speaker turns - ViewModel handles it)
            if (!context.isGroupSpeakerTurn) {
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
        val systemContent = buildString {
            // Role definition
            append(persona.systemPrompt)

            // Scenario / world building
            if (persona.scenario.isNotBlank()) {
                append("\n\n【场景】${persona.scenario}")
            }

            // Speaking style & relationship
            append("\n\n【说话风格】${persona.speakingStyle}")
            append("\n【关系定位】${persona.relationshipType}")

            // User identity
            if (persona.userDisplayName.isNotBlank()) {
                append("\n\n用户的名字是「${persona.userDisplayName}」，请在对话中用这个名字称呼用户。")
            }

            // Example chats for tone reference
            if (persona.exampleChats.isNotEmpty()) {
                append("\n\n【参考对话风格】")
                persona.exampleChats.forEach { append("\n$it") }
            }
        }
        messages.add(mapOf("role" to "system", "content" to systemContent))

        // Waifu mode: instruct AI to avoid action descriptions, let stickers handle emotions
        if (persona.waifuMode) {
            messages.add(mapOf("role" to "system", "content" to
                "【Waifu模式】不要使用动作描写或括号心理活动（如 *微笑*、（脸红）、【叹气】）。" +
                "用自然的对话表达情绪。每句话保持简短，像真人发消息一样。可以适当使用emoji但不要过多。"))
        }

        // If the AI initiated this conversation (proactive message), remind it
        val lastMsg = context.messages.lastOrNull()
        if (lastMsg != null && lastMsg.role == "assistant") {
            messages.add(mapOf("role" to "system", "content" to
                "注意：你刚才主动给用户发了一条消息，现在用户回复你了。这不是新话题的开始，请自然地接上你刚才说的话，继续聊下去。"))
        }

        // Group chat: light context only (each speaker gets their own persona card via swap-card)
        if (context.groupPersonas.isNotEmpty()) {
            val otherNames = context.groupPersonas.joinToString("、") { it.name }
            messages.add(mapOf("role" to "system", "content" to
                "你正在一个群聊中发言。群里还有：$otherNames。只以你的角色身份说话，不要替其他人发言。"))
        }

        // Inject relevant memories
        if (context.memories.isNotEmpty()) {
            val memoryText = context.memories.joinToString("\n") { "- ${it.content}" }
            messages.add(mapOf("role" to "system", "content" to "以下是关于用户的相关记忆:\n$memoryText"))
        }

        // World Book injection — scan recent user messages for keyword matches
        val worldBookBefore = mutableListOf<String>()
        val worldBookAfter = mutableListOf<String>()
        if (context.worldBookEntries.isNotEmpty()) {
            val recentUserMessages = context.messages.takeLast(10)
                .filter { it.role == "user" }
                .joinToString(" ") { it.content }
                .lowercase()

            val activeEntries = context.worldBookEntries
                .filter { it.enabled }
                .sortedByDescending { it.priority }

            for (entry in activeEntries) {
                val triggered = entry.constant || entry.keywords.any { kw ->
                    recentUserMessages.contains(kw.lowercase())
                } || entry.secondaryKeywords.any { sk ->
                    recentUserMessages.contains(sk.lowercase())
                }
                if (!triggered) continue

                val entryText = buildString {
                    append("【${entry.key}】")
                    append("\n${entry.content}")
                }
                when (entry.position) {
                    "after" -> worldBookAfter.add(entryText)
                    else -> worldBookBefore.add(entryText)
                }
            }
        }

        // "before" entries — go before the message history
        worldBookBefore.forEach { text ->
            messages.add(mapOf("role" to "system", "content" to text))
        }

        // Recent conversation history (last N messages)
        val recentMessages = context.messages.takeLast(Constants.DEFAULT_CONTEXT_WINDOW * 2)
        recentMessages.forEach { msg ->
            messages.add(mapOf("role" to msg.role, "content" to msg.content))
        }

        // Author's Note — injected after recent messages, before current user message
        if (persona.authorsNote.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to "[Author's Note: ${persona.authorsNote}]"))
        }

        // "after" entries — go after the message history, before current user message
        worldBookAfter.forEach { text ->
            messages.add(mapOf("role" to "system", "content" to text))
        }

        // Current message
        messages.add(mapOf("role" to "user", "content" to currentMsg))
        return messages
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
