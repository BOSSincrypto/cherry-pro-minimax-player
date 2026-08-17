package io.cherry.player.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.cherry.player.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Last playback error, surfaced so the UI can render a friendly
     * message instead of a silently-black surface. Cleared every time
     * a new [prepareUri] is called.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var player: ExoPlayer? = null
    private var errorListener: Player.Listener? = null

    /** Create the player on first use. Idempotent — returns the existing instance. */
    fun obtainPlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        return PlayerHolder.build(
            context = getApplication(),
            initialSpeed = playbackSpeed.value,
        ).also { p ->
            player = p
            attachErrorListener(p)
        }
    }

    /**
     * Build a [MediaItem] from a URI and prepare playback.
     *
     * For `content://` URIs returned by SAF / the document picker we
     * attach a MIME hint so Media3's container detector has something
     * to chew on: many providers don't expose `openFileDescriptor`
     * sniffing, and `MediaItem.fromUri()` alone leaves ExoPlayer guessing
     * the format — which silently fails for unrecognised containers.
     *
     * We clear any previous error before starting so the UI banner hides
     * on the next successful prepare.
     */
    fun prepareUri(uri: Uri) {
        val p = obtainPlayer()
        _error.value = null

        val mime = guessMimeType(uri)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply { if (mime != null) setMimeType(mime) }
            .build()

        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true
    }

    /**
     * Best-effort MIME-type guess from URI path. Pure function so it's
     * safe to call from any thread.
     */
    private fun guessMimeType(uri: Uri): String? {
        val path = (uri.lastPathSegment ?: uri.toString()).lowercase()
        return when {
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".m4v") -> "video/x-m4v"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".3gp") -> "video/3gpp"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            else -> null
        }
    }

    private fun attachErrorListener(p: ExoPlayer) {
        // Detach any previous listener (shouldn't happen, but cheap defence).
        errorListener?.let { p.removeListener(it) }

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
                _error.value = error.localizedMessage ?: error.errorCodeName
            }
        }
        errorListener = listener
        p.addListener(listener)
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
        val p = obtainPlayer()
        p.playbackParameters = p.playbackParameters.withSpeed(clamped)
    }

    fun releasePlayer() {
        errorListener?.let { player?.removeListener(it) }
        errorListener = null
        player?.release()
        player = null
    }

    /** Read-only view of the live player (or null until created). */
    val current: Player? get() = player

    private companion object {
        const val TAG = "PlayerViewModel"
    }
}
