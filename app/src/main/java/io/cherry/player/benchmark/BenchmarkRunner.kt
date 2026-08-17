package io.cherry.player.benchmark

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Aggregate benchmark state. Exposed as a [StateFlow] so the Compose screen
 * can `collectAsState()` and re-render only when results arrive.
 */
data class BenchmarkState(
    val running: Boolean = false,
    val report: BenchmarkReport? = null,
)

data class BenchmarkReport(
    val totalFrames: Int,
    val droppedFrames: Int,
    val seekMinMs: Long,
    val seekAvgMs: Long,
    val seekMaxMs: Long,
    val seekP95Ms: Long,
)

/**
 * Drives a real benchmark against a live [Player]:
 *  - [FrameDropCounter] listens to [Choreographer] for `vsync` events and
 *    counts how many frames took > 18 ms (anything > 16.67 ms misses a 60Hz
 *    vsync; 18 ms gives a small slack budget for jitter).
 *  - [SeekLatencyRecorder] fires N random seeks and times each one by
 *    listening for `STATE_READY` state changes and capturing the delta
 *    from the seek call to the new-state event.
 *
 * Both stop automatically after [MEASUREMENT_WINDOW_MS] and call
 * `publish()` which folds the results into a [BenchmarkReport].
 */
class BenchmarkRunner {

    private val _state = MutableStateFlow(BenchmarkState())
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()

    fun start(player: Player) {
        if (_state.value.running) return
        _state.update { it.copy(running = true, report = null) }

        // Choreographer must be created on the main (UI) thread.
        val mainHandler = Handler(Looper.getMainLooper())
        val frameCounter = FrameDropCounter(mainHandler)
        val seekRecorder = SeekLatencyRecorder(player)
        frameCounter.start()
        seekRecorder.start()

        Thread {
            try {
                Thread.sleep(MEASUREMENT_WINDOW_MS)
            } catch (_: InterruptedException) { /* exit */ }
            val frameReport = frameCounter.stop()
            val seekReport = seekRecorder.stop()
            val report = BenchmarkReport(
                totalFrames = frameReport.first,
                droppedFrames = frameReport.second,
                seekMinMs = seekReport.minMs,
                seekAvgMs = seekReport.avgMs,
                seekMaxMs = seekReport.maxMs,
                seekP95Ms = seekReport.p95Ms,
            )
            _state.update { it.copy(running = false, report = report) }
        }.start()
    }

    companion object {
        /** How long the measurement window runs (15 s). */
        const val MEASUREMENT_WINDOW_MS = 15_000L
        /** Number of random seeks to issue. */
        const val SEEK_COUNT = 25
        /** Frame budget in nanoseconds. 60 Hz = 16.67 ms; we use 18 ms as the slack. */
        const val FRAME_BUDGET_NS = 18_000_000L
    }
}

/**
 * Snapshot of seek-latency samples for the report.
 */
data class SeekStats(val samples: List<Long>) {
    val minMs: Long get() = (samples.minOrNull() ?: 0L)
    val maxMs: Long get() = (samples.maxOrNull() ?: 0L)
    val avgMs: Long get() = if (samples.isEmpty()) 0L else samples.average().roundToInt().toLong()
    val p95Ms: Long get() {
        if (samples.isEmpty()) return 0L
        val sorted = samples.sorted()
        val idx = ((sorted.size - 1) * 0.95).toInt()
        return sorted[idx]
    }
}

/**
 * Counts vsync frames via [Choreographer] and tags any frame whose
 * interval exceeded [FRAME_BUDGET_NS] as dropped. The Choreographer
 * instance is captured on the UI thread (via the provided [mainHandler])
 * and all `postFrameCallback` calls run on that thread.
 */
private class FrameDropCounter(private val mainHandler: Handler) {

    @Volatile private var totalFrames = 0
    @Volatile private var droppedFrames = 0
    @Volatile private var lastFrameNs = 0L
    @Volatile private var registered: Choreographer.FrameCallback? = null

    fun start() {
        val runner = Runnable { registerCallback() }
        mainHandler.post(runner)
    }

    private fun registerCallback() {
        val choreographer = Choreographer.getInstance()
        // Capture the callback in a local so we don't depend on SAM `this`.
        val cb = Choreographer.FrameCallback { frameTimeNanos ->
            if (lastFrameNs != 0L) {
                val delta = frameTimeNanos - lastFrameNs
                totalFrames++
                if (delta > BenchmarkRunner.FRAME_BUDGET_NS) droppedFrames++
            }
            lastFrameNs = frameTimeNanos
            choreographer.postFrameCallback(registered!!)
        }
        registered = cb
        choreographer.postFrameCallback(cb)
    }

    fun stop(): Pair<Int, Int> {
        val cb = registered
        if (cb != null) {
            val choreographer = mainHandler.run { Choreographer.getInstance() }
            mainHandler.post { choreographer.removeFrameCallback(cb) }
        }
        return Pair(totalFrames, droppedFrames)
    }
}

/**
 * Issues N random seeks and times how long each one takes to reach
 * STATE_READY. Uses the player's listener API so it works for any
 * underlying source (local file, HLS, DASH).
 */
private class SeekLatencyRecorder(private val player: Player) {

    private val pendingSeekAt = HashMap<Int, Long>()
    private val samples = mutableListOf<Long>()
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && pendingSeekAt.isNotEmpty()) {
                val now = System.nanoTime()
                val it = pendingSeekAt.entries.iterator()
                if (it.hasNext()) {
                    val (_, startedAt) = it.next()
                    it.remove()
                    val ms = (now - startedAt) / 1_000_000L
                    synchronized(samples) { samples += ms }
                }
            }
        }
    }

    fun start() {
        player.addListener(listener)
        Thread {
            val duration = player.duration.coerceAtLeast(1L)
            for (i in 0 until BenchmarkRunner.SEEK_COUNT) {
                val target = (Math.random() * duration).toLong()
                synchronized(pendingSeekAt) {
                    pendingSeekAt[i] = System.nanoTime()
                }
                player.seekTo(target)
                try {
                    Thread.sleep(SEEK_DELAY_MS)
                } catch (_: InterruptedException) { return@Thread }
            }
        }.start()
    }

    fun stop(): SeekStats {
        player.removeListener(listener)
        val copy = synchronized(samples) { samples.toList() }
        return SeekStats(copy)
    }

    companion object {
        /** Pause between seeks so each one has time to resolve. */
        const val SEEK_DELAY_MS = 250L
    }
}