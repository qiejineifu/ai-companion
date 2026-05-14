package com.aicompanion.domain.model

data class Conversation(
    val id: String,
    val personaId: String,
    val apiProviderId: String,
    val title: String,
    val lastMessagePreview: String = "",
    val createdAt: Long,
    val lastMessageAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isGroupChat: Boolean = false,
    val groupPersonaIds: List<String> = emptyList(),
    val avatarImageUri: String? = null
)

data class Message(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val emotion: String? = null,
    val tokenCount: Int = 0,
    val metadataJson: String? = null,
    val createdAt: Long,
    val branchParentId: String? = null,
    val branchIndex: Int = 0,
    val senderPersonaId: String? = null
)

data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val traits: List<Trait> = emptyList(),
    val speakingStyle: String = "温柔",
    val relationshipType: String = "朋友",
    val userDisplayName: String = "",
    // Character Card fields (酒馆风格)
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleChats: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val specVersion: String = "",
    val creator: String = "",
    // Per-persona overrides
    val voiceProfileId: String? = null,
    val apiProviderId: String? = null,
    val avatarImageUri: String? = null,
    val defaultModelPath: String? = null,
    val authorsNote: String = "",
    val waifuMode: Boolean = false,
    val isPreset: Boolean = false,
    val createdAt: Long
)

data class Trait(
    val key: String,
    val value: String,
    val weight: Float = 0.5f
)

data class ApiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyEncrypted: String,
    val modelName: String,
    val temperature: Float = 0.8f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val isActive: Boolean = false,
    val providerTemplate: String = "custom",
    val createdAt: Long
)

data class MemoryEntry(
    val id: String,
    val personaId: String,
    val content: String,
    val embeddingRef: String? = null,
    val sourceMessageIds: List<String> = emptyList(),
    val confidence: Float = 0.5f,
    val importance: Float = 0.5f,
    val decayFactor: Float = 1.0f,
    val createdAt: Long,
    val lastAccessedAt: Long = createdAt,
    val accessCount: Int = 0
)

data class VoiceProfile(
    val id: String,
    val name: String,
    val engineType: String = "system",
    val voiceId: String,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val isActive: Boolean = false,
    val createdAt: Long
)

data class Live2DModelInfo(
    val id: String,
    val name: String,
    val modelJsonPath: String,
    val thumbnailPath: String? = null,
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val importedAt: Long
)

data class StickerItem(
    val id: String,
    val personaId: String,
    val emotion: String,
    val imagePath: String,
    val createdAt: Long
)

data class WorldBookEntry(
    val id: String,
    val personaId: String,
    val key: String,
    val content: String,
    val keywords: List<String> = emptyList(),
    val secondaryKeywords: List<String> = emptyList(),
    val priority: Int = 10,
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val position: String = "before",
    val depth: Int = 4,
    val createdAt: Long
)

data class ChatContext(
    val persona: Persona,
    val messages: List<Message>,
    val memories: List<MemoryEntry>,
    val provider: ApiProvider,
    val worldBookEntries: List<WorldBookEntry> = emptyList(),
    val groupPersonas: List<Persona> = emptyList(),
    val isGroupSpeakerTurn: Boolean = false
)
