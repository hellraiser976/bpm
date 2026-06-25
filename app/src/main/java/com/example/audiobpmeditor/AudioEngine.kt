package com.example.audiobpmeditor

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SonicAudioProcessor

class AudioEngine(private val context: Context) {

    private var player: ExoPlayer? = null
    var onProgress: ((positionMs: Long, durationMs: Long)->Unit)? = null
    var onEnded: (() -> Unit)? = null

    private var sonicProcessor = SonicAudioProcessor()
    private var currentSpeed: Float = 1f
    private var currentPitch: Float = 1f
    private var isReverse = false
    private var baseBpm: Float = 120f

    fun init(onReady: () -> Unit = {}) {
        release()
        sonicProcessor = SonicAudioProcessor()
        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(sonicProcessor))
            .build()

        player = ExoPlayer.Builder(context)
            .setAudioSink(audioSink)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object: Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            onEnded?.invoke()
                        }
                    }
                })
            }
        onReady()
    }

    fun load(uri: Uri, reverse: Boolean = false) {
        isReverse = reverse
        player?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            seekTo(0)
            pause()
        }
        applyAudioParams()
    }

    fun setBpm(bpm: Int, base: Float = baseBpm) {
        baseBpm = base
        val target = bpm.coerceIn(1,999).toFloat()
        currentSpeed = target / baseBpm
        applyAudioParams()
    }

    fun setBaseBpm(bpm: Float) {
        baseBpm = bpm.coerceAtLeast(20f)
    }

    fun setPitchSemitones(semitones: Float) {
        // Sonic pitch = 2^(semitones/12)
        currentPitch = Math.pow(2.0, semitones.toDouble()/12.0).toFloat()
        applyAudioParams()
    }

    private fun applyAudioParams() {
        // SonicAudioProcessor expects speed (time-stretch) and pitch separately
        // ExoPlayer PlaybackParameters: speed = tempo, pitch = pitch
        player?.setPlaybackParameters(
            PlaybackParameters(currentSpeed.coerceIn(0.1f, 8f), currentPitch.coerceIn(0.5f, 2f))
        )
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun stop() { player?.pause(); player?.seekTo(0) }
    fun seekTo(ms: Long) { player?.seekTo(ms) }
    fun isPlaying(): Boolean = player?.isPlaying == true
    fun getPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
    fun setLooping(loop: Boolean) {
        player?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun pollProgress() {
        val p = getPosition()
        val d = getDuration()
        if (d > 0) onProgress?.invoke(p, d)
    }

    fun release() {
        player?.release()
        player = null
    }
}
