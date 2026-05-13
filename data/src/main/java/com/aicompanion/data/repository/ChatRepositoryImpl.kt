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

    override suspend fun sendMessage(
        conversationId: String, content: String, context: ChatContext
    ): Flow<Result<String>> = flow {
        // 1. Save user message
        val userMsg = Message(
            id = newId(), conversationId = conversationId,
            role = "user", content = content, createdAt = now()
        )
        messageDao.insert(userMsg.toEntity())
        conversationDao.updateLastMessageTime(conversationId, now())

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

            // 4. Save assistant message
            val emotion = detectEmotion(fullResponse)
            val assistantMsg = Message(
                id = newId(), conversationId = conversationId,
                role = "assistant", content = fullResponse,
                emotion = emotion, createdAt = now()
            )
            messageDao.insert(assistantMsg.toEntity())
            conversationDao.updateLastMessageTime(conversationId, now())
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

    override suspend fun getChatContext(conversationId: String): Result<ChatContext> {
        // Built externally via use case coordination
        return Result.Error("Use orchestrated context building")
    }

    private fun buildApiMessages(context: ChatContext, currentMsg: String): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        val systemContent = buildString {
            append(context.persona.systemPrompt)
            if (context.persona.userDisplayName.isNotBlank()) {
                append("\n\n用户的名字是「${context.persona.userDisplayName}」，请在对话中用这个名字称呼用户。")
            }
        }
        messages.add(mapOf("role" to "system", "content" to systemContent))

        // Inject relevant memories
        if (context.memories.isNotEmpty()) {
            val memoryText = context.memories.joinToString("\n") { "- ${it.content}" }
            messages.add(mapOf("role" to "system", "content" to "以下是关于用户的相关记忆:\n$memoryText"))
        }

        // Recent conversation history (last N messages)
        val recentMessages = context.messages.takeLast(Constants.DEFAULT_CONTEXT_WINDOW * 2)
        recentMessages.forEach { msg ->
            messages.add(mapOf("role" to msg.role, "content" to msg.content))
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
