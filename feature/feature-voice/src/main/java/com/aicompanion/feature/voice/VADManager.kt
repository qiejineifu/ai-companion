package com.aicompanion.feature.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Voice Activity Detection using WebRTC VAD or Sherpa-ONNX VAD.
 * Detects speech vs silence in audio streams.
 */
class VADManager {

    enum class VADMode {
        QUIET,       // Least sensitive
        NORMAL,      // Default
        AGGRESSIVE   // Most sensitive to speech
    }

    enum class VADState {
        SILENCE,
        SPEECH_START,
        SPEAKING,
        SPEECH_END
    }

    data class VADEvent(
        val state: VADState,
        val timestamp: Long = System.currentTimeMillis(),
        val energy: Float = 0f
    )

    private var mode = VADMode.NORMAL
    private var silenceThresholdMs = 1500L
    private var speechThresholdMs = 200L
    private var lastSpeechTime = 0L
    private var lastSilenceTime = 0L
    private var currentState = VADState.SILENCE
    private var consecutiveSilenceFrames = 0
    private var consecutiveSpeechFrames = 0

    // WebRTC VAD requires 10/20/30ms frames at 8/16/32kHz
    private val frameSizeMs = 20
    private val sampleRate = 16000
    private val frameSizeSamples = sampleRate * frameSizeMs / 1000

    fun configure(
        mode: VADMode = VADMode.NORMAL,
        silenceTimeoutMs: Long = 1500L,
        speechStartMs: Long = 200L
    ) {
        this.mode = mode
        this.silenceThresholdMs = silenceTimeoutMs
        this.speechThresholdMs = speechStartMs
    }

    fun processAudioFrame(frame: ShortArray): Flow<VADEvent> = flow {
        val energy = calculateEnergy(frame)
        val isSpeech = detectVoiceActivity(frame, energy)

        val now = System.currentTimeMillis()

        if (isSpeech) {
            consecutiveSilenceFrames = 0
            consecutiveSpeechFrames++

            if (currentState == VADState.SILENCE || currentState == VADState.SPEECH_END) {
                if (consecutiveSpeechFrames * frameSizeMs >= speechThresholdMs) {
                    currentState = VADState.SPEECH_START
                    lastSpeechTime = now
                    emit(VADEvent(VADState.SPEECH_START, now, energy))
                }
            }
            if (currentState == VADState.SPEECH_START) {
                currentState = VADState.SPEAKING
                emit(VADEvent(VADState.SPEAKING, now, energy))
            }
        } else {
            consecutiveSpeechFrames = 0
            consecutiveSilenceFrames++

            if (currentState == VADState.SPEAKING || currentState == VADState.SPEECH_START) {
                val silenceDuration = consecutiveSilenceFrames * frameSizeMs
                if (silenceDuration >= silenceThresholdMs) {
                    currentState = VADState.SPEECH_END
                    lastSilenceTime = now
                    emit(VADEvent(VADState.SPEECH_END, now, energy))
                    // Reset for next utterance
                    currentState = VADState.SILENCE
                }
            }
        }
    }

    private fun detectVoiceActivity(frame: ShortArray, energy: Float): Boolean {
        if (frame.isEmpty()) return false

        // Energy-based detection
        val energyThreshold = when (mode) {
            VADMode.QUIET -> 500f
            VADMode.NORMAL -> 200f
            VADMode.AGGRESSIVE -> 50f
        }

        if (energy < energyThreshold) return false

        // Zero-crossing rate check (distinguishes speech from noise)
        val zcr = calculateZeroCrossingRate(frame)
        val zcrThreshold = when (mode) {
            VADMode.QUIET -> 0.15f
            VADMode.NORMAL -> 0.10f
            VADMode.AGGRESSIVE -> 0.05f
        }

        return zcr < zcrThreshold
    }

    private fun calculateEnergy(frame: ShortArray): Float {
        var sum = 0f
        for (sample in frame) {
            sum += sample.toFloat() * sample.toFloat()
        }
        return sum / frame.size
    }

    private fun calculateZeroCrossingRate(frame: ShortArray): Float {
        if (frame.size < 2) return 0f
        var crossings = 0
        for (i in 1 until frame.size) {
            if ((frame[i] >= 0) != (frame[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / (frame.size - 1)
    }

    fun reset() {
        currentState = VADState.SILENCE
        consecutiveSilenceFrames = 0
        consecutiveSpeechFrames = 0
    }
}
