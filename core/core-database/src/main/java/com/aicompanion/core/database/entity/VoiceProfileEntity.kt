package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engineType: String = "system",
    val voiceId: String,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val isActive: Boolean = false,
    val createdAt: Long
)
