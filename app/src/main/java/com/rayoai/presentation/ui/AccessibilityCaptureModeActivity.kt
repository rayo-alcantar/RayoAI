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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rayoai.R

class AccessibilityCaptureModeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccessibilityCaptureModeDialog(
                        onFullScreen = { finishWithMode(MODE_FULL_SCREEN) },
                        onFocusedElement = { finishWithMode(MODE_FOCUSED_ELEMENT) },
                        onDismiss = { finishWithMode(MODE_CANCELLED) }
                    )
                }
            }
        }
    }

    private fun finishWithMode(mode: String) {
        sendBroadcast(Intent(ACTION_CAPTURE_MODE_SELECTED).apply {
            setPackage(packageName)
            putExtra(EXTRA_MODE, mode)
        })
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_CAPTURE_MODE_SELECTED = "com.rayoai.action.ACCESSIBILITY_CAPTURE_MODE_SELECTED"
        const val EXTRA_MODE = "com.rayoai.extra.ACCESSIBILITY_CAPTURE_MODE"
        const val MODE_FULL_SCREEN = "full_screen"
        const val MODE_FOCUSED_ELEMENT = "focused_element"
        const val MODE_CANCELLED = "cancelled"
    }
}

@Composable
private fun AccessibilityCaptureModeDialog(
    onFullScreen: () -> Unit,
    onFocusedElement: () -> Unit,
    onDismiss: () -> Unit
) {
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
