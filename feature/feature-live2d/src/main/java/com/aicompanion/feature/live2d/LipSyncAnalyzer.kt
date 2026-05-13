package com.aicompanion.feature.live2d

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Analyzes audio waveform to drive Live2D mouth parameters.
 * Maps audio energy/frequency to mouthOpenY parameter.
 */
class LipSyncAnalyzer {

    data class LipSyncParams(
        val mouthOpenY: Float = 0f,      // 0.0 - 1.0
        val mouthForm: Float = 0f,       // mouth shape (smile/frown)
        val energy: Float = 0f,
        val isSpeaking: Boolean = false
    )

    private var smoothingFactor = 0.3f
    private var lastMouthOpen = 0f
    private var gainMultiplier = 2.5f
    private var silenceThreshold = 0.02f

    fun configure(
        smoothing: Float = 0.3f,
        gain: Float = 2.5f,
        threshold: Float = 0.02f
    ) {
        smoothingFactor = smoothing.coerceIn(0f, 1f)
        gainMultiplier = gain.coerceIn(0.5f, 5f)
        silenceThreshold = threshold.coerceIn(0f, 0.5f)
    }

    fun analyzeAudioStream(audioFrames: Flow<FloatArray>): Flow<LipSyncParams> = flow {
        audioFrames.collect { frame ->
            val params = analyzeFrame(frame)
            emit(params)
        }
    }

    fun analyzeFrame(frame: FloatArray): LipSyncParams {
        if (frame.isEmpty()) return LipSyncParams()

        // Calculate RMS energy
        var sumSquares = 0f
        for (sample in frame) {
            sumSquares += sample * sample
        }
        val rms = kotlin.math.sqrt(sumSquares / frame.size)

        // Detect if speaking
        val isSpeaking = rms > silenceThreshold

        // Map RMS to mouth open (with gain)
        val rawMouthOpen = (rms * gainMultiplier).coerceIn(0f, 1f)

        // Apply exponential smoothing
        val mouthOpenY = lastMouthOpen + smoothingFactor * (rawMouthOpen - lastMouthOpen)
        lastMouthOpen = mouthOpenY

        // Detect mouth form from frequency content
        val mouthForm = calculateMouthForm(frame)

        return LipSyncParams(
            mouthOpenY = if (isSpeaking) mouthOpenY else 0f,
            mouthForm = mouthForm,
            energy = rms,
            isSpeaking = isSpeaking
        )
    }

    /**
     * Calculate mouth form from frequency distribution.
     * Higher = spread/smile, Lower = rounded.
     * Simplified: uses zero-crossing rate as proxy for frequency.
     */
    private fun calculateMouthForm(frame: FloatArray): Float {
        if (frame.size < 2) return 0f

        var crossings = 0
        for (i in 1 until frame.size) {
            if ((frame[i] >= 0) != (frame[i - 1] >= 0)) {
                crossings++
            }
        }

        val zcr = crossings.toFloat() / (frame.size - 1)
        // Normalize ZCR to [-1, 1] range for mouth form
        // Low ZCR = rounded (ooh), High ZCR = spread (aah)
        return (zcr * 8f - 1f).coerceIn(-1f, 1f)
    }

    /**
     * Analyze from raw Int16 PCM samples (Android AudioRecord format).
     */
    fun analyzePCMFrame(pcmData: ShortArray): LipSyncParams {
        val floatFrame = FloatArray(pcmData.size) {
            pcmData[it].toFloat() / Short.MAX_VALUE.toFloat()
        }
        return analyzeFrame(floatFrame)
    }

    fun reset() {
        lastMouthOpen = 0f
    }
}
