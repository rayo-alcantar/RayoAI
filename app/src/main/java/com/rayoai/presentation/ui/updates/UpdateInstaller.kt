package com.rayoai.presentation.ui.updates

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.rayoai.BuildConfig
import com.rayoai.data.local.UpdatePreferences
import com.rayoai.domain.model.UpdateInfo
import com.rayoai.domain.model.AppVersion

object UpdateInstaller {
    const val UPDATE_NOTIFICATION_CHANNEL_ID = "update_channel"
    const val UPDATE_NOTIFICATION_ID = 4001

    fun downloadUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        updatePreferences: UpdatePreferences
    ): Long {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        updatePreferences.getPendingUpdate()?.let { pending ->
            downloadManager.remove(pending.downloadId)
        }

        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("RayoAI")
            .setDescription("Descargando actualizacion ${updateInfo.version}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "${updateInfo.version}.apk"
            )

        val downloadId = downloadManager.enqueue(request)
        updatePreferences.savePendingUpdate(downloadId, updateInfo.version)
        return downloadId
    }

    fun buildInstallIntent(context: Context, apkUri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun maybeCleanupAfterUpdate(context: Context, updatePreferences: UpdatePreferences) {
        val pending = updatePreferences.getPendingUpdate() ?: return
        val installed = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return
        val target = AppVersion.parse(pending.version) ?: return
        if (installed < target) {
            return
        }
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        downloadManager.remove(pending.downloadId)
        updatePreferences.clearPendingUpdate()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(UPDATE_NOTIFICATION_ID)
    }

}
