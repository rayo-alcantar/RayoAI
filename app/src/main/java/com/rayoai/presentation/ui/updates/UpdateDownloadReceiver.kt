package com.rayoai.presentation.ui.updates

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rayoai.R
import com.rayoai.data.local.UpdatePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UpdateDownloadReceiver : BroadcastReceiver() {

    @Inject
    lateinit var updatePreferences: UpdatePreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val pending = updatePreferences.getPendingUpdate() ?: return
        if (pending.downloadId != downloadId) return

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        if (!isDownloadSuccessful(downloadManager, downloadId)) return

        val apkUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        val installIntent = UpdateInstaller.buildInstallIntent(context, apkUri)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        ensureUpdateChannel(notificationManager, context)
        val notification = NotificationCompat.Builder(context, UpdateInstaller.UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                context.getString(R.string.update_install_ready_title, pending.version)
            )
            .setContentText(context.getString(R.string.update_install_ready_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(UpdateInstaller.UPDATE_NOTIFICATION_ID, notification)
        maybeStartInstaller(context, installIntent)
    }

    private fun isDownloadSuccessful(downloadManager: DownloadManager, downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return false
            return cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    private fun ensureUpdateChannel(manager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            UpdateInstaller.UPDATE_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.update_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun maybeStartInstaller(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) return
        }
        runCatching {
            context.startActivity(intent)
        }
    }
}
