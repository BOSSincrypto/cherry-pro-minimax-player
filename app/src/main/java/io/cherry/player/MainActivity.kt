package io.cherry.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import io.cherry.player.benchmark.BenchmarkRunner
import io.cherry.player.data.SettingsRepository
import io.cherry.player.player.PlayerViewModel
import io.cherry.player.ui.BenchmarkScreen
import io.cherry.player.ui.PlayerScreen
import io.cherry.player.ui.SettingsScreen
import io.cherry.player.ui.theme.CherryPlayerTheme

/**
 * Single Activity hosting the Compose UI. All screens (player, settings,
 * benchmark) are Compose destinations inside this activity.
 *
 * PiP behaviour:
 *  - `onUserLeaveHint` triggers when the user presses Home while a video
 *    is playing → we call `enterPictureInPictureMode(...)`.
 *  - `onPictureInPictureModeChanged` toggles `isInPip` so the player
 *    UI can hide the controls overlay.
 *  - `configChanges` in the manifest keeps the Activity alive across
 *    PiP / orientation transitions; ExoPlayer survives via the ViewModel.
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private var isInPip: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CherryPlayerTheme {
                AppNavigation(this, viewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPlaying()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }

    private fun enterPipIfPlaying() {
        val player = viewModel.current ?: return
        if (!player.isPlaying) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    /** Internal entry point used by Compose callbacks. */
    internal fun triggerPip() = enterPipIfPlaying()

    override fun onDestroy() {
        if (isFinishing) viewModel.releasePlayer()
        super.onDestroy()
    }
}

private enum class NavRoute { Player, Settings, Benchmark }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AppNavigation(activity: MainActivity, viewModel: PlayerViewModel) {
    var route by rememberSaveable { mutableStateOf(NavRoute.Player) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
    val benchmarkRunner = remember { BenchmarkRunner() }

    when (route) {
        NavRoute.Player -> PlayerScreen(
            viewModel = viewModel,
            onOpenSettings = { route = NavRoute.Settings },
            onOpenBenchmark = { route = NavRoute.Benchmark },
            onEnterPip = { activity.triggerPip() },
        )
        NavRoute.Settings -> SettingsScreen(
            repository = settingsRepo,
            onBack = { route = NavRoute.Player },
        )
        NavRoute.Benchmark -> BenchmarkScreen(
            viewModel = viewModel,
            runner = benchmarkRunner,
            onBack = { route = NavRoute.Player },
        )
    }
}