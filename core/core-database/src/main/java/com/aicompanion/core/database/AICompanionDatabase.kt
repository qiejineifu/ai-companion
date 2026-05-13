package com.aicompanion.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aicompanion.core.database.dao.*
import com.aicompanion.core.database.entity.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PersonaEntity::class,
        ApiProviderEntity::class,
        MemoryEntryEntity::class,
        VoiceProfileEntity::class,
        Live2DModelInfoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AICompanionDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun apiProviderDao(): ApiProviderDao
    abstract fun memoryEntryDao(): MemoryEntryDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun live2DModelInfoDao(): Live2DModelInfoDao
}
