package com.takeyourbrain.app.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes

/** Короткие звуки приложения (логотип, "дзынь"). На беззвучном и на вибрации молчат. */
object AppSound {
    private var player: MediaPlayer? = null

    // Файл записан громко: на средней громкости телефона он бьёт по ушам
    private const val VOLUME = 0.35f

    /** Играет звук. Возвращает true, если он правда зазвучал. */
    @Synchronized
    fun play(context: Context, @RawRes sound: Int): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        stop()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = MediaPlayer.create(context, sound, attributes, audio.generateAudioSessionId()) ?: return false
        created.setVolume(VOLUME, VOLUME)
        created.setOnCompletionListener { stop() }
        created.start()
        player = created
        return true
    }

    @Synchronized
    fun stop() {
        player?.release()
        player = null
    }
}
