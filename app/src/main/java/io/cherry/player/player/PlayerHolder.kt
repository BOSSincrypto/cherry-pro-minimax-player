package io.cherry.player.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.cherry.player.data.SettingsRepository

/**
 * Builds a single ExoPlayer instance tuned for low-latency local + stream
 * playback. Pure factory — no Android lifecycle ownership. The
 * ViewModel that calls this is responsible for `player.release()`.
 *
 * LoadControl numbers come from iterative buffer tuning for phones:
 *  - 1.5 s of buffered audio is enough to start playback quickly.
 *  - 3 s after a rebuffer absorbs typical network jitter on cellular.
 *  - 20 s ahead avoids needless rebuffering on sustained networks.
 *  - 2 s behind lets us drop old data aggressively during seeks.
 */
@UnstableApi
object PlayerHolder {

    private const val BUFFER_FOR_PLAYBACK_MS = 1_500
    private const val BUFFER_FOR_REBUFFER_MS = 3_000
    private const val MIN_BUFFER_MS = 20_000
    private const val MAX_BUFFER_MS = 50_000
    private const val KEEP_BEHIND_MS = 2_000

    fun build(
        context: Context,
        initialSpeed: Float = SettingsRepository.DEFAULT_SPEED,
    ): ExoPlayer {
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(KEEP_BEHIND_MS, /* retainBehindWhenReplacing = */ true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(8_000)
                    .setReadTimeoutMs(8_000)
                    .setAllowCrossProtocolRedirects(true)
                    .setUserAgent("CherryPlayer/1.0"),
            )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { it.playbackParameters = it.playbackParameters.withSpeed(initialSpeed) }
    }
}