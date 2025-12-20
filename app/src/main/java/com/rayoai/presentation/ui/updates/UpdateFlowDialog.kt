package com.rayoai.presentation.ui.updates

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rayoai.R

@Composable
fun UpdateFlowDialog(
    viewModel: UpdateCheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateInfo = uiState.updateAvailable ?: return
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(canRequestInstallPackages(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = canRequestInstallPackages(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = { viewModel.dismissUpdate() },
        title = {
            Text(
                text = stringResource(R.string.update_available_title, updateInfo.version)
            )
        },
        text = {
            Column {
                val changelog = updateInfo.changelog.ifBlank {
                    stringResource(R.string.update_changelog_empty)
                }
                Text(text = changelog)
                if (!canInstall) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.update_install_permission_required))
                }
                if (uiState.isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.update_downloading))
                    }
                }
            }
        },
        confirmButton = {
            val confirmLabel = if (canInstall) {
                stringResource(R.string.update_download)
            } else {
                stringResource(R.string.update_enable_install)
            }
            Button(
                onClick = {
                    if (canInstall) {
                        viewModel.startDownload(context)
                    } else {
                        openUnknownSourcesSettings(context)
                    }
                },
                enabled = !uiState.isDownloading
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissUpdate() }) {
                Text(text = stringResource(R.string.update_later))
            }
        }
    )
}

private fun canRequestInstallPackages(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

private fun openUnknownSourcesSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
