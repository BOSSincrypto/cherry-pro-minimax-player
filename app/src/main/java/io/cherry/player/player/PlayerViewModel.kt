package io.cherry.player.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.cherry.player.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the [ExoPlayer] instance for the lifetime of the host Activity.
 *
 * Lifecycle:
 *  - Constructed on Activity creation (AndroidViewModel needs app context).
 *  - `releasePlayer()` called from `MainActivity.onDestroy()` AFTER the
 *    Compose tree is gone so the surface reference is already null.
 *
 * The `playbackSpeed` StateFlow is observed by the screen so changes to
 * the global setting propagate to every newly-created player; the active
 * player also gets its `playbackParameters` updated in place.
 */
@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)

    /** Latest persisted playback speed, projected from the full settings flow. */
    val playbackSpeed: StateFlow<Float> = settingsRepo.settings
        .map { it.playbackSpeed }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.DEFAULT_SPEED,
        )

    private var player: ExoPlayer? = null

    /** Create the player on first use. Idempotent — returns the existing instance. */
    fun obtainPlayer(): ExoPlayer {
        return player ?: PlayerHolder.build(
            context = getApplication(),
            initialSpeed = playbackSpeed.value,
        ).also { player = it }
    }

    fun prepareUri(uri: Uri) {
        val p = obtainPlayer()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = true
    }

    fun togglePlay() {
        val p = obtainPlayer()
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekRelative(deltaMs: Long) {
        val p = obtainPlayer()
        val target = (p.currentPosition + deltaMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
        p.seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = SettingsRepository.clampSpeed(speed)
        obtainPlayer().playbackParameters =
            obtainPlayer().playbackParameters.withSpeed(clamped)
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }

    /** Read-only view of the live player (or null until created). */
    val current: Player? get() = player
}