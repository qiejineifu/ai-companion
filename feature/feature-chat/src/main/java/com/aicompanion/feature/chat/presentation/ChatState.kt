package com.aicompanion.feature.chat.presentation

import com.aicompanion.core.common.Emotion
import com.aicompanion.domain.model.*
import com.aicompanion.feature.live2d.Live2DModel
import com.aicompanion.feature.live2d.Live2DManager

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
    val showGroupPicker: Boolean = false,
    val showSidebar: Boolean = false,
    val error: String? = null,
    val currentStickerUri: String? = null,
    val branchSelections: Map<String, Int> = emptyMap(),
    val pendingConversationId: String? = null,
    val replyTarget: MessageUi? = null,
    // Group chat round-robin state
    val groupSpeakingQueue: List<String> = emptyList(),
    val currentSpeakerPersonaId: String? = null,
    val groupRoundIndex: Int = 0,
    val callMode: Boolean = false,
    val live2DModel: Live2DModel? = null
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
    object ShowGroupPicker : ChatIntent()
    data class StartGroupChat(val personaIds: List<String>, val conversationId: String = com.aicompanion.core.common.newId()) : ChatIntent()
    data class SelectPersona(val personaId: String) : ChatIntent()
    object ToggleVoiceMode : ChatIntent()
    object ToggleCallMode : ChatIntent()
    data class Regenerate(val messageId: String) : ChatIntent()
    data class SwitchBranch(val branchKey: String, val index: Int) : ChatIntent()
    object ToggleSidebar : ChatIntent()
    data class UpdateInput(val text: String) : ChatIntent()
    object DismissError : ChatIntent()
    data class StartReply(val messageId: String) : ChatIntent()
    object CancelReply : ChatIntent()
}

data class MessageUi(
    val id: String,
    val role: String,
    val content: String,
    val emotion: String? = null,
    val isStreaming: Boolean = false,
    val createdAt: Long,
    val branchParentId: String? = null,
    val branchIndex: Int = 0,
    val senderPersonaId: String? = null,
    val senderPersonaName: String? = null,
    val senderAvatarUri: String? = null,
    val stickerUri: String? = null
) {
    val branchKey: String get() = branchParentId ?: id
}

fun Message.toUi(isStreaming: Boolean = false) = MessageUi(
    id = id, role = role, content = content,
    emotion = emotion, isStreaming = isStreaming, createdAt = createdAt,
    branchParentId = branchParentId, branchIndex = branchIndex,
    senderPersonaId = senderPersonaId
)
