package com.aicompanion.core.di

import android.content.Context
import com.aicompanion.core.database.dao.*
import com.aicompanion.core.network.HttpClientFactory
import com.aicompanion.core.network.SSEClient
import com.aicompanion.data.repository.*
import com.aicompanion.domain.repository.*
import com.aicompanion.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideSSEClient(): SSEClient = SSEClient(HttpClientFactory.create())

    @Provides @Singleton
    fun providePersonaRepository(dao: PersonaDao): PersonaRepository = PersonaRepositoryImpl(dao)

    @Provides @Singleton
    fun provideApiProviderRepository(dao: ApiProviderDao): ApiProviderRepository = ApiProviderRepositoryImpl(dao)

    @Provides @Singleton
    fun provideMemoryRepository(dao: MemoryEntryDao): MemoryRepository = MemoryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideChatRepository(
        convDao: ConversationDao, msgDao: MessageDao, sseClient: SSEClient
    ): ChatRepository = ChatRepositoryImpl(convDao, msgDao, sseClient)

    @Provides @Singleton
    fun provideVoiceRepository(
        dao: VoiceProfileDao, @ApplicationContext context: Context
    ): VoiceRepository = VoiceRepositoryImpl(dao, context)

    @Provides @Singleton
    fun provideLive2DModelRepository(
        dao: Live2DModelInfoDao, @ApplicationContext context: Context
    ): Live2DModelRepository = Live2DModelRepositoryImpl(dao, context)

    // UseCases
    @Provides fun provideSendMessageUseCase(repo: ChatRepository) = SendMessageUseCase(repo)
    @Provides fun provideGetConversationsUseCase(repo: ChatRepository) = GetConversationsUseCase(repo)
    @Provides fun provideGetMessagesUseCase(repo: ChatRepository) = GetMessagesUseCase(repo)
    @Provides fun provideStopGenerationUseCase(repo: ChatRepository) = StopGenerationUseCase(repo)
    @Provides fun provideCreateConversationUseCase(repo: ChatRepository) = CreateConversationUseCase(repo)
}
