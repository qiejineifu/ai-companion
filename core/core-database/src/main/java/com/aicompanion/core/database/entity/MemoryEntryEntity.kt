package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_entries",
    indices = [Index("personaId")]
)
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val content: String,
    val embeddingRef: String? = null,
    val sourceMessageIds: String = "[]",
    val confidence: Float = 0.5f,
    val importance: Float = 0.5f,
    val decayFactor: Float = 1.0f,
    val createdAt: Long,
    val lastAccessedAt: Long = createdAt,
    val accessCount: Int = 0
)
