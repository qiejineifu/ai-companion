package com.aicompanion.data.mapper

import com.aicompanion.core.database.entity.*
import com.aicompanion.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private val gson = Gson()

fun ConversationEntity.toDomain(): Conversation {
    val groupIds: List<String> = try {
        gson.fromJson(groupPersonaIdsJson, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) { emptyList() }
    return Conversation(
        id = id, personaId = personaId, apiProviderId = apiProviderId,
        title = title, lastMessagePreview = lastMessagePreview,
        createdAt = createdAt, lastMessageAt = lastMessageAt,
        isPinned = isPinned, isArchived = isArchived,
        isGroupChat = isGroupChat, groupPersonaIds = groupIds,
        avatarImageUri = avatarImageUri
    )
}

fun MessageEntity.toDomain() = Message(
    id = id, conversationId = conversationId, role = role,
    content = content, emotion = emotion, tokenCount = tokenCount,
    metadataJson = metadataJson, createdAt = createdAt,
    branchParentId = branchParentId, branchIndex = branchIndex,
    senderPersonaId = senderPersonaId
)

fun PersonaEntity.toDomain(): Persona {
    val traits: List<Trait> = try {
        gson.fromJson(traitsJson, object : TypeToken<List<Trait>>() {}.type)
    } catch (_: Exception) { emptyList() }
    val exampleChats: List<String> = try {
        gson.fromJson(exampleChatsJson, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) { emptyList() }
    val tags: List<String> = try {
        gson.fromJson(tagsJson, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) { emptyList() }
    val moments: List<MomentEntry> = try {
        gson.fromJson(momentsJson, object : TypeToken<List<MomentEntry>>() {}.type)
    } catch (_: Exception) { emptyList() }
    val experiences: List<ExperienceEntry> = try {
        gson.fromJson(experiencesJson, object : TypeToken<List<ExperienceEntry>>() {}.type)
    } catch (_: Exception) { emptyList() }
    val userProfile: UserProfile? = try {
        gson.fromJson(userProfileJson, UserProfile::class.java)
    } catch (_: Exception) { null }
    return Persona(
        id = id, name = name, description = description,
        systemPrompt = systemPrompt, traits = traits,
        speakingStyle = speakingStyle, relationshipType = relationshipType,
        userDisplayName = userDisplayName,
        scenario = scenario, firstMessage = firstMessage,
        exampleChats = exampleChats, tags = tags,
        specVersion = specVersion, creator = creator,
        voiceProfileId = voiceProfileId, apiProviderId = apiProviderId,
        modelName = modelName,
        avatarImageUri = avatarImageUri, defaultModelPath = defaultModelPath,
        authorsNote = authorsNote, waifuMode = waifuMode, chuanYueMode = chuanYueMode, isPreset = isPreset, createdAt = createdAt,
        voiceSid = voiceSid,
        appearanceDesc = appearanceDesc, imageGenEnabled = imageGenEnabled,
        moments = moments, experiences = experiences,
        momentsEnabled = momentsEnabled, momentsIntervalMinutes = momentsIntervalMinutes,
        momentsRandomMode = momentsRandomMode, momentsRandomMinMinutes = momentsRandomMinMinutes,
        momentsRandomMaxMinutes = momentsRandomMaxMinutes,
        experiencesEnabled = experiencesEnabled, experiencesIntervalMinutes = experiencesIntervalMinutes,
        experiencesRandomMode = experiencesRandomMode, experiencesRandomMinMinutes = experiencesRandomMinMinutes,
        experiencesRandomMaxMinutes = experiencesRandomMaxMinutes,
        userProfile = userProfile
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
    isActive = isActive, createdAt = createdAt,
    ttsModel = ttsModel, ttsSid = ttsSid
)

fun VoiceProfile.toEntity() = VoiceProfileEntity(
    id = id, name = name, engineType = engineType,
    voiceId = voiceId, pitch = pitch, speed = speed,
    isActive = isActive, createdAt = createdAt,
    ttsModel = ttsModel, ttsSid = ttsSid
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
    scenario = scenario, firstMessage = firstMessage,
    exampleChatsJson = gson.toJson(exampleChats), tagsJson = gson.toJson(tags),
    specVersion = specVersion, creator = creator,
    voiceProfileId = voiceProfileId, apiProviderId = apiProviderId,
    modelName = modelName,
    avatarImageUri = avatarImageUri, defaultModelPath = defaultModelPath,
    authorsNote = authorsNote, waifuMode = waifuMode, chuanYueMode = chuanYueMode, isPreset = isPreset, createdAt = createdAt,
    voiceSid = voiceSid,
    appearanceDesc = appearanceDesc, imageGenEnabled = imageGenEnabled,
    momentsJson = gson.toJson(moments), experiencesJson = gson.toJson(experiences),
    momentsEnabled = momentsEnabled, momentsIntervalMinutes = momentsIntervalMinutes,
    momentsRandomMode = momentsRandomMode, momentsRandomMinMinutes = momentsRandomMinMinutes,
    momentsRandomMaxMinutes = momentsRandomMaxMinutes,
    experiencesEnabled = experiencesEnabled, experiencesIntervalMinutes = experiencesIntervalMinutes,
    experiencesRandomMode = experiencesRandomMode, experiencesRandomMinMinutes = experiencesRandomMinMinutes,
    experiencesRandomMaxMinutes = experiencesRandomMaxMinutes,
    userProfileJson = gson.toJson(userProfile)
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
    title = title, lastMessagePreview = lastMessagePreview,
    createdAt = createdAt, lastMessageAt = lastMessageAt,
    isPinned = isPinned, isArchived = isArchived,
    isGroupChat = isGroupChat, groupPersonaIdsJson = gson.toJson(groupPersonaIds),
    avatarImageUri = avatarImageUri
)

fun Message.toEntity() = MessageEntity(
    id = id, conversationId = conversationId, role = role,
    content = content, emotion = emotion, tokenCount = tokenCount,
    metadataJson = metadataJson, createdAt = createdAt,
    branchParentId = branchParentId, branchIndex = branchIndex,
    senderPersonaId = senderPersonaId
)

// World Book
fun WorldBookEntity.toDomain(): WorldBookEntry {
    val keywords: List<String> = try {
        gson.fromJson(keywordsJson, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) { emptyList() }
    val secondary: List<String> = try {
        gson.fromJson(secondaryKeywordsJson, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) { emptyList() }
    return WorldBookEntry(
        id = id, personaId = personaId, key = key, content = content,
        keywords = keywords, secondaryKeywords = secondary,
        priority = priority, enabled = enabled, constant = constant,
        position = position, depth = depth, createdAt = createdAt
    )
}

fun WorldBookEntry.toEntity() = WorldBookEntity(
    id = id, personaId = personaId, key = key, content = content,
    keywordsJson = gson.toJson(keywords), secondaryKeywordsJson = gson.toJson(secondaryKeywords),
    priority = priority, enabled = enabled, constant = constant,
    position = position, depth = depth, createdAt = createdAt
)
