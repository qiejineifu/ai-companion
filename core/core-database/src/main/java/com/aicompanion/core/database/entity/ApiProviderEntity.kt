package com.aicompanion.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_providers")
data class ApiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyEncrypted: String,
    val modelName: String,
    val temperature: Float = 0.8f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val isActive: Boolean = false,
    val providerTemplate: String = "custom",
    val createdAt: Long
)
