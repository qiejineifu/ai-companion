package com.aicompanion.core.di

import android.content.Context
import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.Constants
import com.aicompanion.core.database.AICompanionDatabase
import com.aicompanion.core.database.DatabaseFactory
import com.aicompanion.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.crypto.SecretKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSecretKey(): SecretKey = CryptoUtil.getOrCreateKey()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, secretKey: SecretKey): AICompanionDatabase {
        val passphrase = secretKey.encoded ?: ByteArray(32) { it.toByte() }
        val keySize = minOf(passphrase.size, 32)
        val dbPassphrase = passphrase.copyOf(keySize).let {
            if (it.size < 32) it + ByteArray(32 - it.size) { 0 } else it
        }
        return DatabaseFactory.create(context, dbPassphrase)
    }

    @Provides fun provideConversationDao(db: AICompanionDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideMessageDao(db: AICompanionDatabase): MessageDao = db.messageDao()
    @Provides fun providePersonaDao(db: AICompanionDatabase): PersonaDao = db.personaDao()
    @Provides fun provideApiProviderDao(db: AICompanionDatabase): ApiProviderDao = db.apiProviderDao()
    @Provides fun provideMemoryEntryDao(db: AICompanionDatabase): MemoryEntryDao = db.memoryEntryDao()
    @Provides fun provideVoiceProfileDao(db: AICompanionDatabase): VoiceProfileDao = db.voiceProfileDao()
    @Provides fun provideLive2DModelDao(db: AICompanionDatabase): Live2DModelInfoDao = db.live2DModelInfoDao()
    @Provides fun provideStickerDao(db: AICompanionDatabase): StickerDao = db.stickerDao()
    @Provides fun provideWorldBookDao(db: AICompanionDatabase): WorldBookDao = db.worldBookDao()
}
