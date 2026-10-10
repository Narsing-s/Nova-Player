package com.narsing.novaplayer

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the single playback engine so audio/video playback and media-session
 * notification controls remain alive when the Activity goes to the background.
 */
class NovaPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val localBinder = PlaybackBinder()

    inner class PlaybackBinder : Binder() {
        fun playbackPlayer(): ExoPlayer = player
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(applicationContext).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            shuffleModeEnabled = false
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onBind(intent: Intent): IBinder? {
        return if (intent.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "com.narsing.novaplayer.LOCAL_PLAYBACK_BIND"
    }
}
