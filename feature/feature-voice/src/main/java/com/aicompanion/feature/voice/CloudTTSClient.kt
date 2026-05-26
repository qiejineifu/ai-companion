package com.aicompanion.feature.voice

import android.util.Log
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CloudTTSClient(private val apiKey: String) {

    data class TTSAudio(val audioFile: File)

    suspend fun synthesize(text: String, model: String = "cosyvoice-v1:zhitian_emo", outputDir: File): TTSAudio? = withContext(Dispatchers.IO) {
        try {
            // Format: "model:voice" splits into model + voice; plain model name used as-is
            val parts = model.split(":", limit = 2)
            val actualModel = parts[0]
            val voice = parts.getOrNull(1)

            val param = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(actualModel)
                .apply { if (voice != null) voice(voice) }
                .build()

            val synthesizer = SpeechSynthesizer(param, null)
            var audio: java.nio.ByteBuffer? = null
            try {
                audio = synthesizer.call(text)
            } finally {
                synthesizer.getDuplexApi().close(1000, "bye")
            }

            if (audio == null || audio.remaining() == 0) {
                Log.e("CloudTTSClient", "No audio data returned (likely quota exceeded)")
                return@withContext null
            }

            val bytes = ByteArray(audio.remaining())
            audio.get(bytes)

            val file = File(outputDir, "cloud_tts_${System.currentTimeMillis()}.mp3")
            file.outputStream().use { it.write(bytes) }
            Log.d("CloudTTSClient", "Saved ${bytes.size} bytes to ${file.name}")
            TTSAudio(file)
        } catch (e: Exception) {
            Log.e("CloudTTSClient", "Synthesize failed: ${e.message}", e)
            null
        }
    }
}
