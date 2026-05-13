package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val apiProviderId: String,
    val title: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)
