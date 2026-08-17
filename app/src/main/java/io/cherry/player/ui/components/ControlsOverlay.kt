package io.cherry.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cherry.player.R

/**
 * Player control overlay rendered above the [PlayerView].
 *
 * v2 additions:
 *  - Fullscreen toggle button (top-right of the overlay)
 *  - Hint row that doubles as a "tap to dismiss" cue when in fullscreen
 */
@Composable
fun ControlsOverlay(
    isPlaying: Boolean,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    IconButton(onClick = onSeekBack) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = stringResource(R.string.cd_seek_back),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.cd_pause else R.string.cd_play
                            ),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onSeekForward) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = stringResource(R.string.cd_seek_forward),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit
                            else Icons.Filled.Fullscreen,
                            contentDescription = stringResource(
                                if (isFullscreen) R.string.cd_exit_fullscreen
                                else R.string.cd_enter_fullscreen
                            ),
                            tint = Color.White,
                        )
                    }
                }
            }
            if (isFullscreen) {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.hint_tap_to_exit_fullscreen),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}