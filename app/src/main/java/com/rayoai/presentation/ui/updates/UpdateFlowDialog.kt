package com.rayoai.presentation.ui.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rayoai.R
import androidx.activity.ComponentActivity

@Composable
fun UpdateFlowDialog() {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: UpdateCheckViewModel = hiltViewModel(activity)
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val updateInfo = uiState.updateAvailable
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

    DisposableEffect(context, uiState.isDownloading) {
        if (!uiState.isDownloading) {
            onDispose { }
        } else {
            val appContext = context.applicationContext
            val foregroundContext = context
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    maybeStartCompletedDownloadInstall(foregroundContext, downloadId, canInstall)
                    viewModel.onDownloadComplete(context, downloadId)
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose {
                runCatching { appContext.unregisterReceiver(receiver) }
            }
        }
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(text = stringResource(R.string.update_error_title)) },
            text = { Text(text = error.details) },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(error.details))
                        viewModel.clearError()
                    }
                ) {
                    Text(text = stringResource(R.string.update_error_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(text = stringResource(R.string.update_error_close))
                }
            }
        )
        return
    }

    updateInfo ?: return

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

private fun maybeStartCompletedDownloadInstall(
    context: android.content.Context,
    downloadId: Long,
    canInstall: Boolean
): Boolean {
    if (!canInstall) return false
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    val query = DownloadManager.Query().setFilterById(downloadId)
    val successful = downloadManager.query(query).use { cursor ->
        if (!cursor.moveToFirst()) return false
        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (statusIndex < 0) return false
        cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
    }
    if (!successful) return false
    val apkUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return false
    val installIntent = UpdateInstaller.buildInstallIntent(context, apkUri)
    return runCatching { context.startActivity(installIntent) }.isSuccess
}
