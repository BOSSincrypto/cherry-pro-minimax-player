package io.cherry.player.player

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background audio playback service. Holds a single shared [ExoPlayer]
 * + [MediaSession] pair for the app's lifetime.
 *
 * Process priority: the system spins this up as a foreground service when
 * audio starts, which on Android 10+ means it runs in its own process
 * weight class (higher CPU/memory budget) than the Activity. We don't
 * explicitly call `Process.setThreadPriority` here — the system handles
 *   it via the foreground service promotion.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val exo = PlayerHolder.build(this)
        player = exo
        mediaSession = MediaSession.Builder(this, exo).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // If the user swipes the task away while paused, stop the service.
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            // release the session first so no new controllers can connect
            release()
        }
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        /**
         * Shared singleton ExoPlayer reference. Set by [PlaybackService.onCreate]
         * and read by the [PlayerViewModel] so both the UI and the background
         * service talk to the same player instance. Optional in v1 — the
         * ViewModel can still own a private ExoPlayer if this is null.
         */
        @Volatile
        var sharedPlayer: ExoPlayer? = null
            internal set
    }
}