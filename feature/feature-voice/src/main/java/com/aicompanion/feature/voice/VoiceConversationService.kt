package com.aicompanion.feature.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aicompanion.core.common.Emotion
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Foreground service for full-duplex voice conversations.
 * Pipeline: Mic → VAD → STT → LLM → TTS → Speaker
 * Supports barge-in: user speech interrupts AI output.
 */
class VoiceConversationService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): VoiceConversationService = this@VoiceConversationService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Audio
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Components
    private val vadManager = VADManager()
    private var sttManager: STTManager? = null
    private var ttsManager: TTSManager? = null

    // State
    private val _state = MutableStateFlow(VoiceConversationState())
    val state: StateFlow<VoiceConversationState> = _state.asStateFlow()

    private val eventChannel = Channel<VoiceConversationEvent>(Channel.BUFFERED)
    val events: Flow<VoiceConversationEvent> = eventChannel.receiveAsFlow()

    // Barge-in
    private var aiSpeakingJob: Job? = null
    private var micListeningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(1001, notification)
        return START_STICKY
    }

    fun setEngines(stt: STTManager, tts: TTSManager) {
        this.sttManager = stt
        this.ttsManager = tts
    }

    fun startListening() {
        if (_state.value.isActive) return

        _state.update { it.copy(isActive = true, mode = VoiceMode.LISTENING) }

        micListeningJob = serviceScope.launch {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, bufferSize
                )

                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize)

                while (isActive && _state.value.isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val frame = buffer.copyOf(read)
                        vadManager.processAudioFrame(frame).collect { vadEvent ->
                            handleVADEvent(vadEvent)
                        }
                    }
                }
            } catch (e: Exception) {
                eventChannel.trySend(VoiceConversationEvent.Error("录音错误: ${e.message}"))
            }
        }
    }

    private suspend fun handleVADEvent(event: VADManager.VADEvent) {
        when (event.state) {
            VADManager.VADState.SPEECH_START -> {
                // User starts speaking → barge-in: stop AI playback
                if (_state.value.mode == VoiceMode.SPEAKING) {
                    aiSpeakingJob?.cancel()
                    ttsManager?.stop()
                    eventChannel.trySend(VoiceConversationEvent.BargeIn)
                }
                _state.update { it.copy(mode = VoiceMode.LISTENING) }
            }
            VADManager.VADState.SPEAKING -> {
                _state.update { it.copy(mode = VoiceMode.LISTENING, vadEnergy = event.energy) }
            }
            VADManager.VADState.SPEECH_END -> {
                _state.update { it.copy(mode = VoiceMode.PROCESSING) }
                // Trigger STT final result → will be sent to LLM
                eventChannel.trySend(VoiceConversationEvent.UserFinishedSpeaking)
            }
            VADManager.VADState.SILENCE -> {
                _state.update { it.copy(vadEnergy = 0f) }
            }
        }
    }

    fun startSpeaking(text: String, emotion: Emotion? = null) {
        _state.update { it.copy(mode = VoiceMode.SPEAKING, currentEmotion = emotion) }

        aiSpeakingJob = serviceScope.launch {
            try {
                ttsManager?.speak(text)
                eventChannel.trySend(VoiceConversationEvent.AIStartSpeaking(text))

                // Wait for TTS to finish
                ttsManager?.events?.collect { ttsEvent ->
                    if (ttsEvent is TTSEvent.DoneSpeaking) {
                        _state.update { it.copy(mode = VoiceMode.IDLE) }
                        eventChannel.trySend(VoiceConversationEvent.AIFinishedSpeaking)
                    }
                }
            } catch (e: Exception) {
                eventChannel.trySend(VoiceConversationEvent.Error("TTS 错误: ${e.message}"))
            }
        }
    }

    fun stopConversation() {
        aiSpeakingJob?.cancel()
        micListeningJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        vadManager.reset()
        _state.update { it.copy(isActive = false, mode = VoiceMode.IDLE) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "voice_conversation",
            "语音对话",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "voice_conversation")
            .setContentTitle("AI 语音对话")
            .setContentText("正在聆听...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopConversation()
        serviceScope.cancel()
        super.onDestroy()
    }
}

data class VoiceConversationState(
    val isActive: Boolean = false,
    val mode: VoiceMode = VoiceMode.IDLE,
    val currentEmotion: Emotion? = null,
    val vadEnergy: Float = 0f
)

enum class VoiceMode { IDLE, LISTENING, PROCESSING, SPEAKING }

sealed class VoiceConversationEvent {
    data class AIStartSpeaking(val text: String) : VoiceConversationEvent()
    object AIFinishedSpeaking : VoiceConversationEvent()
    object UserFinishedSpeaking : VoiceConversationEvent()
    object BargeIn : VoiceConversationEvent()
    data class Error(val message: String) : VoiceConversationEvent()
}
