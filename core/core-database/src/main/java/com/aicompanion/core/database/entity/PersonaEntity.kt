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
    val avatarImageUri: String? = null,
    val defaultModelPath: String? = null,
    val isPreset: Boolean = false,
    val createdAt: Long
)
