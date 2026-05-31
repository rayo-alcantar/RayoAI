package com.rayoai.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import com.rayoai.R
import com.rayoai.presentation.ui.theme.RayoAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccessibilityCaptureResultActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)

        val captureId = intent.getLongExtra(EXTRA_CAPTURE_ID, -1L).takeIf { it > 0L }
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()

        setContent {
            RayoAITheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccessibilityCaptureResultDialog(
                        description = description,
                        canOpenCapture = captureId != null,
                        onDismiss = { finish() },
                        onOpenCapture = {
                            if (captureId != null) {
                                openMainActivity(captureId)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun openMainActivity(captureId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_CAPTURE_ID, captureId)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_CAPTURE_ID = "com.rayoai.extra.ACCESSIBILITY_CAPTURE_ID"
        const val EXTRA_DESCRIPTION = "com.rayoai.extra.ACCESSIBILITY_DESCRIPTION"
    }
}

@Composable
private fun AccessibilityCaptureResultDialog(
    description: String,
    canOpenCapture: Boolean,
    onDismiss: () -> Unit,
    onOpenCapture: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val title = stringResource(R.string.accessibility_capture_result_title)
    val closeLabel = stringResource(R.string.accessibility_capture_result_close)
    val openLabel = stringResource(R.string.accessibility_capture_result_open)
    val fallbackDescription = stringResource(R.string.accessibility_capture_result_empty)
    val announcedDescription = description.ifBlank { fallbackDescription }

    LaunchedEffect(announcedDescription) {
        view.post {
            view.announceForAccessibility(announcedDescription)
            val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
            if (accessibilityManager?.isEnabled == true) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                    packageName = context.packageName
                    className = AccessibilityCaptureResultActivity::class.java.name
                    text.add(announcedDescription)
                }
                accessibilityManager.sendAccessibilityEvent(event)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = announcedDescription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(closeLabel)
                    }
                    Button(
                        onClick = onOpenCapture,
                        enabled = canOpenCapture,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(openLabel)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
