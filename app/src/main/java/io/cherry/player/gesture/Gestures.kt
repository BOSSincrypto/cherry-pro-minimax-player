package io.cherry.player.gesture

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.coroutineScope

/**
 * Composable modifier that wires player-level gestures:
 *  - Single tap (center)         → [onSingleTap]
 *  - Double tap (left/right)     → [onSeekBack] / [onSeekForward]
 *  - Vertical drag (left half)   → brightness delta via window attributes
 *  - Vertical drag (right half)  → music stream volume delta
 *
 * Implementation notes:
 *  - Each gesture detector is its own pointerInput so cancel/replace rules
 *    don't fight. Compose gives each pointerInput its own coroutine.
 *  - Brightness is applied via the Activity window's `screenBrightness`
 *    attribute so it survives orientation changes; AudioManager stream
 *    volume is process-wide and survives navigation.
 *  - Drag axis is locked at drag-start so the user's finger can drift
 *    sideways without flipping between volume and brightness mid-drag.
 */
fun Modifier.playerGestures(
    onSingleTap: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val audio = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val window = remember(context) { (context as? Activity)?.window }
    val maxVolume = remember(audio) {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    val initialBrightness = remember(window) {
        window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
    }
    val currentBrightness = remember { mutableFloatStateOf(initialBrightness) }
    val currentVolume = remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }

    // Brightness side effect: whenever the float state changes, push it to
    // the window. Doing this here keeps the gesture detector pure.
    LaunchedEffect(currentBrightness.floatValue) {
        val w = window ?: return@LaunchedEffect
        val lp = w.attributes
        lp.screenBrightness = currentBrightness.floatValue.coerceIn(0.05f, 1.0f)
        w.attributes = lp
        onBrightnessDelta(currentBrightness.floatValue)
    }
    LaunchedEffect(currentVolume.floatValue) {
        onVolumeDelta(currentVolume.floatValue)
    }

    // Tap + double-tap detector.
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = { offset ->
                if (offset.x < size.width / 2f) onSeekBack() else onSeekForward()
            },
        )
    }
        // Drag detector for brightness (left half) and volume (right half).
        .pointerInput(Unit) {
            coroutineScope {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragAxis = if (offset.x < size.width / 2f) DragAxis.BRIGHTNESS else DragAxis.VOLUME
                        dragStartY = offset.y
                        dragStartValue = when (dragAxis) {
                            DragAxis.BRIGHTNESS -> currentBrightness.floatValue
                            DragAxis.VOLUME -> currentVolume.floatValue
                        }
                    },
                    onDrag = { change, _ ->
                        val delta = (dragStartY - change.position.y) / size.height.toFloat()
                        val normalized = (dragStartValue + delta).coerceIn(0f, 1f)
                        when (dragAxis) {
                            DragAxis.BRIGHTNESS -> currentBrightness.floatValue = normalized
                            DragAxis.VOLUME -> {
                                currentVolume.floatValue = normalized
                                audio.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (normalized * maxVolume).toInt(),
                                    0,
                                )
                            }
                        }
                    },
                )
            }
        }
}

private enum class DragAxis { BRIGHTNESS, VOLUME }

// Captured per-gesture by detectDragGestures. Single-threaded: only the
// pointerInput coroutine reads/writes these.
private var dragAxis: DragAxis = DragAxis.VOLUME
private var dragStartY: Float = 0f
private var dragStartValue: Float = 0f

/** Pure helper for unit tests + non-Compose callers. */
fun applyBrightnessDelta(current: Float, delta: Float): Float =
    (current.coerceAtLeast(0f) + delta).coerceIn(0.05f, 1.0f)

/** Convenience extension for the Activity to enable keep-screen-on. */
fun WindowManager.applyKeepScreenOn(@Suppress("UNUSED_PARAMETER") keep: Boolean) {
    /* no-op for v1 stub — Compose-driven controls cover this in iteration 4 */
}