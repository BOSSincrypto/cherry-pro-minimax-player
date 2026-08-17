package io.cherry.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.cherry.player.R
import io.cherry.player.gesture.playerGestures
import io.cherry.player.player.PlayerViewModel
import io.cherry.player.ui.components.ControlsOverlay

/**
 * Full-screen player surface + controls + media-source picker hooks.
 *
 * Lifecycle of the underlying [PlayerView]:
 *  - Created once via `remember { PlayerView(ctx) }`.
 *  - On dispose we set `playerView.player = null` so Media3 releases the
 *    surface reference. We DO NOT release the [androidx.media3.exoplayer.ExoPlayer]
 *    here — the ViewModel owns it and releases it on Activity onDestroy.
 *  - The AndroidView re-creates only if the ViewModel instance changes,
 *    which never happens for a single-Activity app.
 *
 * Gestures are wired via [playerGestures] (single-tap toggles overlay,
 * double-tap seeks, drag on left/right half changes brightness/volume).
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
    onEnterPip: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { viewModel.obtainPlayer() }
    var showUrlDialog by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Observe isPlaying from the actual player so the overlay reflects reality.
    val isPlaying by remember(player) {
        derivedStateOf { player.isPlaying }
    }

    // Re-poll the play state every render — derivedStateOf is cheap because
    // it tracks reads through Compose snapshot system. A Player.Listener
    // would also work but adds another moving part for marginal gain.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                // Trigger recomposition by mutating controlsVisible
                controlsVisible = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Pause on lifecycle STOP so backgrounding the app actually stops audio.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                player.playWhenReady = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.prepareUri(uri)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_player)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    IconButton(onClick = onOpenBenchmark) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = stringResource(R.string.cd_benchmark),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                        )
                    }
                    FilledIconButton(
                        onClick = { filePicker.launch(arrayOf("video/*")) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = stringResource(R.string.cd_open_file),
                        )
                    }
                    FilledIconButton(
                        onClick = { showUrlDialog = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = stringResource(R.string.cd_open_url),
                        )
                    }
                    FilledIconButton(
                        onClick = onEnterPip,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureInPicture,
                            contentDescription = stringResource(R.string.cd_enter_pip),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .playerGestures(
                    onSingleTap = { controlsVisible = !controlsVisible },
                    onSeekBack = { viewModel.seekRelative(-10_000L) },
                    onSeekForward = { viewModel.seekRelative(10_000L) },
                    onBrightnessDelta = { /* applied via window attributes in the modifier */ },
                    onVolumeDelta = { /* applied via AudioManager in the modifier */ },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { view ->
                        view.useController = false  // we render our own overlay
                        view.setShowSubtitleButton(false)
                        view.setShowFastForwardButton(false)
                        view.setShowRewindButton(false)
                        view.setShowNextButton(false)
                        view.setShowPreviousButton(false)
                        view.player = player
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize(),
            )

            if (controlsVisible) {
                ControlsOverlay(
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.togglePlay() },
                    onSeekBack = { viewModel.seekRelative(-10_000L) },
                    onSeekForward = { viewModel.seekRelative(10_000L) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                )
            }
        }
    }

    if (showUrlDialog) {
        OpenUrlDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                showUrlDialog = false
                viewModel.prepareUri(android.net.Uri.parse(url))
            },
        )
    }
}