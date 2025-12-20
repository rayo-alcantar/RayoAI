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
        if (compareVersions(BuildConfig.VERSION_NAME, pending.version) < 0) {
            return
        }
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        downloadManager.remove(pending.downloadId)
        updatePreferences.clearPendingUpdate()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(UPDATE_NOTIFICATION_ID)
    }

    private fun compareVersions(first: String, second: String): Int {
        val firstParts = extractVersionParts(first)
        val secondParts = extractVersionParts(second)
        val max = maxOf(firstParts.size, secondParts.size)
        for (index in 0 until max) {
            val a = firstParts.getOrElse(index) { 0 }
            val b = secondParts.getOrElse(index) { 0 }
            if (a != b) {
                return a.compareTo(b)
            }
        }
        return 0
    }

    private fun extractVersionParts(input: String): List<Int> {
        return Regex("\\d+").findAll(input).map { it.value.toInt() }.toList()
    }
}
