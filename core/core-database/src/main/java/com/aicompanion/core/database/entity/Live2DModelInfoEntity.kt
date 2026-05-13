package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live2d_models")
data class Live2DModelInfoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelJsonPath: String,
    val thumbnailPath: String? = null,
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val importedAt: Long
)
