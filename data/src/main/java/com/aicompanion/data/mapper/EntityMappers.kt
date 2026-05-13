package com.aicompanion.data.mapper

import com.aicompanion.core.database.entity.*
import com.aicompanion.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private val gson = Gson()

fun ConversationEntity.toDomain() = Conversation(
    id = id, personaId = personaId, apiProviderId = apiProviderId,
    title = title, createdAt = createdAt, lastMessageAt = lastMessageAt,
    isPinned = isPinned, isArchived = isArchived
)

fun MessageEntity.toDomain() = Message(
    id = id, conversationId = conversationId, role = role,
    content = content, emotion = emotion, tokenCount = tokenCount,
    metadataJson = metadataJson, createdAt = createdAt
)

fun PersonaEntity.toDomain(): Persona {
    val traits: List<Trait> = try {
        gson.fromJson(traitsJson, object : TypeToken<List<Trait>>() {}.type)
    } catch (_: Exception) { emptyList() }
    return Persona(
        id = id, name = name, description = description,
        systemPrompt = systemPrompt, traits = traits,
        speakingStyle = speakingStyle, relationshipType = relationshipType,
        userDisplayName = userDisplayName,
        avatarImageUri = avatarImageUri, defaultModelPath = defaultModelPath,
        isPreset = isPreset, createdAt = createdAt
    )
}

fun ApiProviderEntity.toDomain() = ApiProvider(
    id = id, name = name, baseUrl = baseUrl,
    apiKeyEncrypted = apiKeyEncrypted, modelName = modelName,
    temperature = temperature, topP = topP, maxTokens = maxTokens,
    isActive = isActive, providerTemplate = providerTemplate, createdAt = createdAt
)

fun MemoryEntryEntity.toDomain(): MemoryEntry {
    val sourceIds: List<String> = try {
        gson.fromJson(sourceMessageIds, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) { emptyList() }
    return MemoryEntry(
        id = id, personaId = personaId, content = content,
        embeddingRef = embeddingRef, sourceMessageIds = sourceIds,
        confidence = confidence, importance = importance, decayFactor = decayFactor,
        createdAt = createdAt, lastAccessedAt = lastAccessedAt, accessCount = accessCount
    )
}

fun VoiceProfileEntity.toDomain() = VoiceProfile(
    id = id, name = name, engineType = engineType,
    voiceId = voiceId, pitch = pitch, speed = speed,
    isActive = isActive, createdAt = createdAt
)

fun Live2DModelInfoEntity.toDomain() = Live2DModelInfo(
    id = id, name = name, modelJsonPath = modelJsonPath,
    thumbnailPath = thumbnailPath, isBuiltIn = isBuiltIn,
    isActive = isActive, importedAt = importedAt
)

// Domain to Entity
fun Persona.toEntity() = PersonaEntity(
    id = id, name = name, description = description,
    systemPrompt = systemPrompt, traitsJson = gson.toJson(traits),
    speakingStyle = speakingStyle, relationshipType = relationshipType,
    userDisplayName = userDisplayName,
    avatarImageUri = avatarImageUri, defaultModelPath = defaultModelPath,
    isPreset = isPreset, createdAt = createdAt
)

fun ApiProvider.toEntity() = ApiProviderEntity(
    id = id, name = name, baseUrl = baseUrl,
    apiKeyEncrypted = apiKeyEncrypted, modelName = modelName,
    temperature = temperature, topP = topP, maxTokens = maxTokens,
    isActive = isActive, providerTemplate = providerTemplate, createdAt = createdAt
)

fun MemoryEntry.toEntity() = MemoryEntryEntity(
    id = id, personaId = personaId, content = content,
    embeddingRef = embeddingRef, sourceMessageIds = gson.toJson(sourceMessageIds),
    confidence = confidence, importance = importance, decayFactor = decayFactor,
    createdAt = createdAt, lastAccessedAt = lastAccessedAt, accessCount = accessCount
)

fun Conversation.toEntity() = ConversationEntity(
    id = id, personaId = personaId, apiProviderId = apiProviderId,
    title = title, createdAt = createdAt, lastMessageAt = lastMessageAt,
    isPinned = isPinned, isArchived = isArchived
)

fun Message.toEntity() = MessageEntity(
    id = id, conversationId = conversationId, role = role,
    content = content, emotion = emotion, tokenCount = tokenCount,
    metadataJson = metadataJson, createdAt = createdAt
)
