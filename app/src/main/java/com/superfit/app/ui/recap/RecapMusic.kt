package com.superfit.app.ui.recap

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.superfit.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The recap's background track: an original loop made for Superfit, so there are no rights to clear.
 * Plays quietly, fades in and out, and starts muted when the phone is on silent or vibrate, or when
 * another app is already playing music.
 */
class RecapMusic(private val context: Context) {
    private var player: MediaPlayer? = null
    private var fade: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var volume = 0f

    /** Muted when the phone is on silent or already playing music; otherwise the user's last choice. */
    var muted: Boolean = phoneIsQuiet(context) || savedMuted(context) == true
        private set

    fun toggleMute(): Boolean {
        muted = !muted
        prefs(context).edit().putBoolean(KEY_MUTED, muted).apply()
        if (muted) pause() else play()
        return muted
    }

    fun play() {
        if (muted) return
        val p = player ?: runCatching {
            MediaPlayer.create(
                context,
                R.raw.recap_music,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
            )?.apply { isLooping = true }
        }.getOrNull()?.also { player = it } ?: return
        runCatching { if (!p.isPlaying) p.start() }
        fadeTo(MAX_VOLUME)
    }

    fun pause() {
        fadeTo(0f) { runCatching { player?.takeIf { it.isPlaying }?.pause() } }
    }

    fun release() {
        fade?.cancel()
        runCatching { player?.release() }
        player = null
    }

    private fun fadeTo(target: Float, then: () -> Unit = {}) {
        fade?.cancel()
        fade = scope.launch {
            val steps = 12
            val start = volume
            for (i in 1..steps) {
                volume = start + (target - start) * i / steps
                runCatching { player?.setVolume(volume, volume) }
                delay(40)
            }
            then()
        }
    }

    companion object {
        private const val MAX_VOLUME = 0.6f
        private const val PREFS = "superfit_recap_music"
        private const val KEY_MUTED = "muted"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** The user's own choice, once they have tapped the speaker. */
        private fun savedMuted(context: Context): Boolean? =
            prefs(context).takeIf { it.contains(KEY_MUTED) }?.getBoolean(KEY_MUTED, false)

        private fun phoneIsQuiet(context: Context): Boolean {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
            return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 ||
                audio.isMusicActive
        }
    }
}
