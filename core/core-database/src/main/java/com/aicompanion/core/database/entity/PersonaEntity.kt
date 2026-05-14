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
    val avatarImageUri: String? = null,
    val defaultModelPath: String? = null,
    val authorsNote: String = "",
    val waifuMode: Boolean = false,
    val isPreset: Boolean = false,
    val createdAt: Long
)
