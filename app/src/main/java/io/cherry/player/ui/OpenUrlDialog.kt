package io.cherry.player.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.cherry.player.R

/**
 * URL entry dialog. The caller passes the latest chosen URL (may be blank)
 * and a confirm callback. We return true/false via the lambdas rather than
 * holding navigation state, so the dialog can be composed in/out cleanly.
 */
@Composable
fun OpenUrlDialog(
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.open_url_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.trim() },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.open_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (looksLikeMediaUrl(value)) onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.open_url_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.open_url_cancel))
            }
        },
    )
}

/**
 * Lightweight validation: any non-blank value that starts with http(s),
 * content://, file://, or looks like an .m3u8 / .mpd URL. Avoids hard
 * parsing so we don't reject unusual but valid stream sources (RTSP,
 * RTMP via Media3).
 */
private fun looksLikeMediaUrl(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return false
    return v.startsWith("http://") ||
        v.startsWith("https://") ||
        v.startsWith("content://") ||
        v.startsWith("file://") ||
        v.endsWith(".m3u8") ||
        v.endsWith(".mpd") ||
        v.endsWith(".mp4") ||
        v.endsWith(".mov") ||
        v.endsWith(".m4v")
}