package com.aicompanion.domain.repository

import com.aicompanion.domain.model.VoiceProfile
import kotlinx.coroutines.flow.Flow

interface VoiceRepository {
    fun getProfiles(): Flow<List<VoiceProfile>>
    suspend fun getActiveProfile(): VoiceProfile?
    suspend fun setActive(id: String)
    suspend fun synthesize(text: String, profile: VoiceProfile): ByteArray?
    suspend fun startListening(): Flow<String>
    suspend fun stopListening()
}
