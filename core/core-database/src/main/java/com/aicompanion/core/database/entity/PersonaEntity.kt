package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val traitsJson: String = "[]",
    val speakingStyle: String = "温柔",
    val relationshipType: String = "朋友",
    val userDisplayName: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleChatsJson: String = "[]",
    val tagsJson: String = "[]",
    val specVersion: String = "",
    val creator: String = "",
    val voiceProfileId: String? = null,
    val apiProviderId: String? = null,
    val modelName: String? = null,
    val avatarImageUri: String? = null,
    val defaultModelPath: String? = null,
    val authorsNote: String = "",
    val waifuMode: Boolean = false,
    val chuanYueMode: Boolean = false,
    val isPreset: Boolean = false,
    val createdAt: Long,
    val voiceSid: Int = 0,
    val appearanceDesc: String = "",
    val imageGenEnabled: Boolean = false,
    val momentsJson: String = "[]",
    val experiencesJson: String = "[]",
    val momentsEnabled: Boolean = false,
    val momentsIntervalMinutes: Int = 60,
    val momentsRandomMode: Boolean = true,
    val momentsRandomMinMinutes: Int = 30,
    val momentsRandomMaxMinutes: Int = 120,
    val experiencesEnabled: Boolean = false,
    val experiencesIntervalMinutes: Int = 720,
    val experiencesRandomMode: Boolean = true,
    val experiencesRandomMinMinutes: Int = 360,
    val experiencesRandomMaxMinutes: Int = 1440,
    val userProfileJson: String = "{}"
)
