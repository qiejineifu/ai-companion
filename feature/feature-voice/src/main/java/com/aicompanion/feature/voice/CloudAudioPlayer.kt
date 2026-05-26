package com.aicompanion.feature.voice

import android.media.MediaPlayer
import java.io.File

object CloudAudioPlayer {

    fun play(file: File) {
        if (!file.exists() || file.length() == 0L) return
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            player?.release()
            try { file.delete() } catch (_: Exception) {}
        }
    }
}
