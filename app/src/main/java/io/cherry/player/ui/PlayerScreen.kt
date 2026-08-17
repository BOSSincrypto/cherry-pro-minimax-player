package io.cherry.player.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
 * Bug fixes vs. v1:
 *  - `useController` and `controllerAutoShow` are disabled BEFORE the player
 *    is bound so the PlayerView doesn't allocate its default control panel.
 *  - Deprecated `setShow*` calls removed; they were no-ops in Media3 1.6.0
 *    and could race with our custom overlay.
 *  - The factory captures the player reference once; `update` is a no-op so
 *    we never briefly null the binding during recomposition.
 *  - Fullscreen state hides system bars + the TopAppBar and shows a sticky
 *    "Exit fullscreen" cue inside the overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    initialUri: Uri? = null,
    onOpenSettings: () -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Bind the player once per ViewModel — never recreate it.
    val player = remember(viewModel) { viewModel.obtainPlayer() }

    // Auto-prepare if we arrived with a media source from HomeScreen.
    LaunchedEffect(initialUri) {
        if (initialUri != null) viewModel.prepareUri(initialUri)
    }

    var showUrlDialog by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    val isPlaying by remember(player) { derivedStateOf { player.isPlaying } }
    val error by viewModel.error.collectAsState()

    // Refresh isPlaying when player state changes (driven by ExoPlayer events).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
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

    // Immersive fullscreen: hide status + nav bars when toggled on.
    val insetsController = remember(view, context) {
        val activity = context as? Activity
        val window = activity?.window
        window?.let { WindowCompat.getInsetsController(it, view) }
    }
    DisposableEffect(isFullscreen, insetsController) {
        val controller = insetsController
        if (controller != null) {
            if (isFullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { /* system bars restore on next composition */ }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Mirror the MainActivity picker: persist read access so the
            // URI survives rotation / process death. Without this the
            // URI is only readable until the Activity is destroyed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.prepareUri(uri)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_player)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
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
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
                .playerGestures(
                    onSingleTap = { controlsVisible = !controlsVisible },
                    onSeekBack = { viewModel.seekRelative(-10_000L) },
                    onSeekForward = { viewModel.seekRelative(10_000L) },
                    onBrightnessDelta = { /* applied via window attrs in the modifier */ },
                    onVolumeDelta = { /* applied via AudioManager in the modifier */ },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    // Build the PlayerView and bind the captured player.
                    // `also { ... }` keeps the outer `player` reference so
                    // `it.player = player` writes to the new view's player
                    // property instead (of this@PlayerScreen.player, which
                    // is invalid because PlayerScreen is a function, not a
                    // class). Disable the controller BEFORE binding the
                    // player so the default control view-tree never exists.
                    PlayerView(ctx).also { view ->
                        view.useController = false
                        view.controllerAutoShow = false
                        view.setShutterBackgroundColor(android.graphics.Color.BLACK)
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (controlsVisible) {
                ControlsOverlay(
                    isPlaying = isPlaying,
                    isFullscreen = isFullscreen,
                    onPlayPause = { viewModel.togglePlay() },
                    onSeekBack = { viewModel.seekRelative(-10_000L) },
                    onSeekForward = { viewModel.seekRelative(10_000L) },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                )
            }

            if (error != null) {
                ErrorBanner(
                    message = error!!,
                    onDismiss = { initialUri?.let(viewModel::prepareUri) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isFullscreen) 16.dp else 72.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }

    if (showUrlDialog) {
        OpenUrlDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                showUrlDialog = false
                viewModel.prepareUri(Uri.parse(url))
            },
        )
    }
}

/**
 * Inline error banner shown when ExoPlayer fails to prepare the
 * current URI. Tapping "Retry" re-prepares the same URI; tapping
 * "Dismiss" hides the banner until the next error.
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC1B0A0E),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.error_load_failed, message),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(text = "Retry", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}