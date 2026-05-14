package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "world_book_entries")
data class WorldBookEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val key: String,
    val content: String,
    val keywordsJson: String = "[]",
    val secondaryKeywordsJson: String = "[]",
    val priority: Int = 10,
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val position: String = "before",
    val depth: Int = 4,
    val createdAt: Long
)
