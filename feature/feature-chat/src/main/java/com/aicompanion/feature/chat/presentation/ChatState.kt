package com.aicompanion.feature.chat.presentation

import com.aicompanion.core.common.Emotion
import com.aicompanion.domain.model.*

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversation: Conversation? = null,
    val messages: List<MessageUi> = emptyList(),
    val inputText: String = "",
    val streamState: StreamState = StreamState.Idle,
    val currentEmotion: Emotion = Emotion.NEUTRAL,
    val isRecording: Boolean = false,
    val voiceMode: Boolean = false,
    val activePersona: Persona? = null,
    val activeApiProvider: ApiProvider? = null,
    val retrievedMemories: List<MemoryEntry> = emptyList(),
    val showPersonaPicker: Boolean = false,
    val showSidebar: Boolean = false,
    val error: String? = null
)

sealed class StreamState {
    object Idle : StreamState()
    object Connecting : StreamState()
    data class Streaming(val partialText: String, val emotion: Emotion? = null) : StreamState()
    data class Completed(val fullText: String, val emotion: Emotion? = null) : StreamState()
    data class Error(val message: String) : StreamState()
}

sealed class ChatIntent {
    data class SendMessage(val text: String) : ChatIntent()
    object SendVoice : ChatIntent()
    object StopGeneration : ChatIntent()
    data class DeleteMessage(val messageId: String) : ChatIntent()
    data class SelectConversation(val id: String) : ChatIntent()
    object NewConversation : ChatIntent()
    data class SelectPersona(val personaId: String) : ChatIntent()
    object ToggleVoiceMode : ChatIntent()
    object ToggleSidebar : ChatIntent()
    data class UpdateInput(val text: String) : ChatIntent()
    object DismissError : ChatIntent()
}

data class MessageUi(
    val id: String,
    val role: String,
    val content: String,
    val emotion: String? = null,
    val isStreaming: Boolean = false,
    val createdAt: Long
)

fun Message.toUi(isStreaming: Boolean = false) = MessageUi(
    id = id, role = role, content = content,
    emotion = emotion, isStreaming = isStreaming, createdAt = createdAt
)
