package com.alejandro.super8cam

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Plays the two bundled, procedurally-synthesized SFX: a shutter click and a looping film-wind whir. */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(audioAttributes)
        .build()

    private val shutterSoundId = soundPool.load(appContext, R.raw.shutter_click, 1)
    private var windPlayer: MediaPlayer? = null

    fun playShutterClick() {
        soundPool.play(shutterSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun startWindLoop() {
        stopWindLoop()
        windPlayer = MediaPlayer.create(appContext, R.raw.film_wind)?.apply {
            isLooping = true
            setVolume(0.6f, 0.6f)
            start()
        }
    }

    fun stopWindLoop() {
        windPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        windPlayer = null
    }

    fun release() {
        stopWindLoop()
        soundPool.release()
    }
}
