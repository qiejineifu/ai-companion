package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val emotion: String, // happy/sad/angry/surprised/shy/thinking/neutral
    val imagePath: String,
    val createdAt: Long
)
