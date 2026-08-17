package io.cherry.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cherry.player.R
import io.cherry.player.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Settings screen — speed slider, battery toggle, reset, about.
 * Pure stateless Composable that reads/writes through [SettingsRepository].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
) {
    val settings by repository.settings.collectAsState(initial = null)
    val current = settings ?: io.cherry.player.data.CherrySettings(
        playbackSpeed = SettingsRepository.DEFAULT_SPEED,
        lowPriorityOnBattery = false,
    )
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Speed row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_speed_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_speed_value, current.playbackSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = current.playbackSpeed,
                    onValueChange = { value ->
                        scope.launch { repository.setPlaybackSpeed(value) }
                    },
                    valueRange = SettingsRepository.MIN_SPEED..SettingsRepository.MAX_SPEED,
                    steps = ((SettingsRepository.MAX_SPEED - SettingsRepository.MIN_SPEED) /
                        SettingsRepository.SPEED_STEP).toInt() - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = {
                        scope.launch { repository.resetSpeedToDefault() }
                    }) {
                        Text(stringResource(R.string.settings_reset))
                    }
                }
            }

            HorizontalDivider()

            // Battery toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_battery),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = current.lowPriorityOnBattery,
                    onCheckedChange = { value ->
                        scope.launch { repository.setLowPriorityOnBattery(value) }
                    },
                )
            }

            HorizontalDivider()

            // About
            Column {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}