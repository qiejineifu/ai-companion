package com.aicompanion.domain.usecase

import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.Conversation
import com.aicompanion.domain.model.Message
import com.aicompanion.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class SendMessageUseCase(private val repo: ChatRepository) {
    suspend operator fun invoke(
        conversationId: String,
        content: String,
        context: com.aicompanion.domain.model.ChatContext
    ): Flow<Result<String>> = repo.sendMessage(conversationId, content, context)
}

class GetConversationsUseCase(private val repo: ChatRepository) {
    operator fun invoke(): Flow<List<Conversation>> = repo.getConversations()
}

class GetMessagesUseCase(private val repo: ChatRepository) {
    operator fun invoke(conversationId: String): Flow<List<Message>> = repo.getMessages(conversationId)
}

class StopGenerationUseCase(private val repo: ChatRepository) {
    suspend operator fun invoke() = repo.stopGeneration()
}

class CreateConversationUseCase(private val repo: ChatRepository) {
    suspend operator fun invoke(personaId: String, apiProviderId: String, title: String): Result<Conversation> =
        repo.createConversation(personaId, apiProviderId, title)
}
