package com.rayoai.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rayoai.R
import com.rayoai.domain.model.VideoLinkValidator
import com.rayoai.presentation.ui.theme.RayoAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccessibilityCaptureModeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)

        setContent {
            RayoAITheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccessibilityCaptureModeDialog(
                        onFullScreen = { finishWithMode(MODE_FULL_SCREEN) },
                        onFocusedElement = { finishWithMode(MODE_FOCUSED_ELEMENT) },
                        onVideoUrl = { url -> finishWithMode(MODE_VIDEO_URL, url) },
                        onDismiss = { finishWithMode(MODE_CANCELLED) }
                    )
                }
            }
        }
    }

    private fun finishWithMode(mode: String, videoUrl: String? = null) {
        sendBroadcast(Intent(ACTION_CAPTURE_MODE_SELECTED).apply {
            setPackage(packageName)
            putExtra(EXTRA_MODE, mode)
            videoUrl?.let { putExtra(EXTRA_VIDEO_URL, it) }
        })
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_CAPTURE_MODE_SELECTED = "com.rayoai.action.ACCESSIBILITY_CAPTURE_MODE_SELECTED"
        const val EXTRA_MODE = "com.rayoai.extra.ACCESSIBILITY_CAPTURE_MODE"
        const val EXTRA_VIDEO_URL = "com.rayoai.extra.ACCESSIBILITY_VIDEO_URL"
        const val MODE_FULL_SCREEN = "full_screen"
        const val MODE_FOCUSED_ELEMENT = "focused_element"
        const val MODE_VIDEO_URL = "video_url"
        const val MODE_CANCELLED = "cancelled"
    }
}

@Composable
private fun AccessibilityCaptureModeDialog(
    onFullScreen: () -> Unit,
    onFocusedElement: () -> Unit,
    onVideoUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showVideoUrlDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.accessibility_capture_mode_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onFullScreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.accessibility_capture_mode_full_screen))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onFocusedElement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.accessibility_capture_mode_focused_element))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showVideoUrlDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.accessibility_capture_mode_video_link))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showVideoUrlDialog) {
        AccessibilityVideoUrlDialog(
            onDismiss = { showVideoUrlDialog = false },
            onSubmit = onVideoUrl
        )
    }
}

@Composable
private fun AccessibilityVideoUrlDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    val isValid = VideoLinkValidator.extractSupportedUrl(value) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_link_dialog_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.video_link_hint)) },
                supportingText = {
                    if (value.isNotBlank() && !isValid) {
                        Text(stringResource(R.string.video_link_unsupported))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(value) }, enabled = isValid) {
                Text(stringResource(R.string.video_link_describe_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
