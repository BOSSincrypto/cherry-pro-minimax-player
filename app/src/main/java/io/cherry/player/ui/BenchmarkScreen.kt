package io.cherry.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.cherry.player.R
import io.cherry.player.benchmark.BenchmarkReport
import io.cherry.player.benchmark.BenchmarkRunner
import io.cherry.player.player.PlayerViewModel

/**
 * Performance benchmark screen — measures dropped frames + seek latency
 * for the currently loaded media. Real measurements, no fake counters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BenchmarkScreen(
    viewModel: PlayerViewModel,
    runner: BenchmarkRunner,
    onBack: () -> Unit,
) {
    val state by runner.state.collectAsState()
    val player = viewModel.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_benchmark)) },
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
            Button(
                onClick = {
                    if (player != null) {
                        runner.start(player)
                    }
                },
                enabled = player != null && !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.running) stringResource(R.string.benchmark_running)
                    else stringResource(R.string.benchmark_run)
                )
            }

            if (state.running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.report?.let { report: BenchmarkReport ->
                BenchmarkResultBlock(report)
            }

            if (player == null) {
                Text(
                    text = "Open a video first to run the benchmark.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BenchmarkResultBlock(report: BenchmarkReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                R.string.benchmark_summary,
                report.droppedFrames,
                report.totalFrames,
                if (report.totalFrames > 0) (report.droppedFrames * 100.0 / report.totalFrames) else 0.0,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(
                R.string.benchmark_seek_summary,
                report.seekMinMs,
                report.seekAvgMs,
                report.seekMaxMs,
                report.seekP95Ms,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}