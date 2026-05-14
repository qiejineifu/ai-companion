package com.aicompanion.data.repository

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.aicompanion.core.database.dao.VoiceProfileDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.VoiceProfile
import com.aicompanion.domain.repository.VoiceRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale

class VoiceRepositoryImpl(
    private val dao: VoiceProfileDao,
    private val context: Context
) : VoiceRepository {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun getProfiles(): Flow<List<VoiceProfile>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveProfile(): VoiceProfile? = dao.getActive()?.toDomain()

    override suspend fun setActive(id: String) {
        dao.deactivateAll()
        dao.setActive(id)
    }

    override suspend fun createProfile(profile: VoiceProfile) {
        dao.deactivateAll()
        dao.insert(profile.toEntity())
        dao.setActive(profile.id)
    }

    override suspend fun deleteProfile(id: String) {
        dao.deleteById(id)
    }

    override suspend fun synthesize(text: String, profile: VoiceProfile): ByteArray? {
        return try {
            when (profile.engineType) {
                "system" -> synthesizeSystem(text, profile)
                "iflytek" -> synthesizeCloud(text, profile) // Phase 1: placeholder
                else -> synthesizeSystem(text, profile)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun synthesizeSystem(text: String, profile: VoiceProfile): ByteArray? {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    tts?.setPitch(profile.pitch)
                    tts?.setSpeechRate(profile.speed)

                    val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
                    val result = tts?.synthesizeToFile(text, null, tempFile, "tts_${System.currentTimeMillis()}")
                    if (result == TextToSpeech.SUCCESS) {
                        cont.resume(tempFile.readBytes()) { _, _, _ -> }
                    } else {
                        cont.resume(null) { _, _, _ -> }
                    }
                } else {
                    cont.resume(null) { _, _, _ -> }
                }
            }
        }
    }

    private suspend fun synthesizeCloud(text: String, profile: VoiceProfile): ByteArray? {
        // Phase 2: integrate iFlytek / Aliyun Cloud TTS
        return synthesizeSystem(text, profile)
    }

    override suspend fun startListening(): Flow<String> = callbackFlow {
        // Phase 1: use Android SpeechRecognizer
        // Placeholder - will be implemented in feature-voice module
        awaitClose {}
    }

    override suspend fun stopListening() {
        // Stop SpeechRecognizer
    }
}
