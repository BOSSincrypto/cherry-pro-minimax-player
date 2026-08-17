package io.cherry.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import io.cherry.player.benchmark.BenchmarkRunner
import io.cherry.player.data.SettingsRepository
import io.cherry.player.player.PlayerViewModel
import io.cherry.player.ui.BenchmarkScreen
import io.cherry.player.ui.HomeScreen
import io.cherry.player.ui.PlayerScreen
import io.cherry.player.ui.SettingsScreen
import io.cherry.player.ui.theme.CherryPlayerTheme

/**
 * Single Activity hosting the Compose UI.
 *
 * v2 navigation:
 *  - Home (default) → Player / Settings / Benchmark
 *  - Player → Back to Home
 *  - Settings / Benchmark → Back to Home (or Player if you arrived from there)
 *
 * PiP behaviour:
 *  - `onUserLeaveHint` triggers when the user presses Home while a video
 *    is playing → we call `enterPictureInPictureMode(...)`.
 *  - `onPictureInPictureModeChanged` toggles `isInPip` so the player
 *    UI can hide the controls overlay.
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

    internal fun triggerPip() = enterPipIfPlaying()

    override fun onDestroy() {
        if (isFinishing) viewModel.releasePlayer()
        super.onDestroy()
    }
}

/**
 * Lightweight route state: the current screen + (when on Player) the
 * media source the player should prepare.
 */
private sealed class NavRoute {
    object Home : NavRoute()
    object Settings : NavRoute()
    object Benchmark : NavRoute()
    data class Player(val uri: Uri) : NavRoute()
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AppNavigation(activity: MainActivity, viewModel: PlayerViewModel) {
    var route by rememberSaveable(stateSaver = NavRouteSaver) {
        mutableStateOf<NavRoute>(NavRoute.Home)
    }
    val context = LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
    val benchmarkRunner = remember { BenchmarkRunner() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) route = NavRoute.Player(uri)
    }

    when (val r = route) {
        NavRoute.Home -> HomeScreen(
            onPickFile = { filePicker.launch(arrayOf("video/*")) },
            onPickUrl = { url -> route = NavRoute.Player(Uri.parse(url)) },
            onOpenSettings = { route = NavRoute.Settings },
            onOpenBenchmark = { route = NavRoute.Benchmark },
        )
        is NavRoute.Player -> PlayerScreen(
            viewModel = viewModel,
            initialUri = r.uri,
            onBack = { route = NavRoute.Home },
            onOpenSettings = { route = NavRoute.Settings },
            onOpenBenchmark = { route = NavRoute.Benchmark },
            onEnterPip = { activity.triggerPip() },
        )
        NavRoute.Settings -> SettingsScreen(
            repository = settingsRepo,
            onBack = { route = NavRoute.Home },
        )
        NavRoute.Benchmark -> BenchmarkScreen(
            viewModel = viewModel,
            runner = benchmarkRunner,
            onBack = { route = NavRoute.Home },
        )
    }
}

/**
 * rememberSaveable Saver for [NavRoute]. We serialise as a String
 * "type|uri" — Home / Settings / Benchmark have no payload, Player carries
 * the Uri. The Uri is round-tripped via toString()/parse() since Uri is
 * not natively Bundle-friendly as a state-saver value.
 */
private val NavRouteSaver = androidx.compose.runtime.saveable.Saver<NavRoute, String>(
    save = { route ->
        when (route) {
            NavRoute.Home -> "home|"
            NavRoute.Settings -> "settings|"
            NavRoute.Benchmark -> "benchmark|"
            is NavRoute.Player -> "player|${route.uri}"
        }
    },
    restore = { value ->
        val parts = value.split("|", limit = 2)
        when (parts[0]) {
            "home" -> NavRoute.Home
            "settings" -> NavRoute.Settings
            "benchmark" -> NavRoute.Benchmark
            "player" -> NavRoute.Player(Uri.parse(parts[1]))
            else -> NavRoute.Home
        }
    },
)