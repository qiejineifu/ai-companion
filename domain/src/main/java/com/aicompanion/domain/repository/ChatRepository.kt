package com.aicompanion.domain.repository

import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getConversations(): Flow<List<Conversation>>
    fun getMessages(conversationId: String): Flow<List<Message>>
    suspend fun createConversation(personaId: String, apiProviderId: String, title: String): Result<Conversation>
    suspend fun saveMessage(message: Message)
    suspend fun getConversationById(id: String): Conversation?
    suspend fun createFullConversation(conversation: Conversation): Result<Conversation>
    suspend fun sendMessage(conversationId: String, content: String, context: ChatContext): Flow<Result<String>>
    suspend fun stopGeneration()
    suspend fun deleteConversation(conversationId: String)
    suspend fun hardDeleteConversation(conversationId: String)
    suspend fun getChatContext(conversationId: String): Result<ChatContext>
}
