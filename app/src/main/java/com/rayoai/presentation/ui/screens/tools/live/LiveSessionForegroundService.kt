package com.rayoai.presentation.ui.screens.tools.live

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rayoai.R
import com.rayoai.data.remote.live.GeminiLiveManager
import com.rayoai.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LiveSessionForegroundService : Service() {

    @Inject
    lateinit var liveManager: GeminiLiveManager

    private var screenCaptureManager: ScreenCaptureManager? = null
    private var isScreenCaptureActive = false

    companion object {
        const val CHANNEL_ID = "rayoai_live_session_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP = "com.rayoai.live.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "com.rayoai.live.ACTION_TOGGLE_PAUSE"
        const val ACTION_START_SCREEN_CAPTURE = "com.rayoai.live.ACTION_START_SCREEN_CAPTURE"
        const val ACTION_STOP_SCREEN_CAPTURE = "com.rayoai.live.ACTION_STOP_SCREEN_CAPTURE"

        const val EXTRA_RESULT_CODE = "com.rayoai.live.EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.rayoai.live.EXTRA_RESULT_DATA"

        fun start(context: Context) {
            val intent = Intent(context, LiveSessionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startScreenCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, LiveSessionForegroundService::class.java).apply {
                action = ACTION_START_SCREEN_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopScreenCapture(context: Context) {
            val intent = Intent(context, LiveSessionForegroundService::class.java).apply {
                action = ACTION_STOP_SCREEN_CAPTURE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveSessionForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                screenCaptureManager?.stop()
                screenCaptureManager = null
                liveManager.stopSession()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                liveManager.toggleVideoPause()
            }
            ACTION_START_SCREEN_CAPTURE -> {
                isScreenCaptureActive = true
            }
            ACTION_STOP_SCREEN_CAPTURE -> {
                isScreenCaptureActive = false
                screenCaptureManager?.stop()
                screenCaptureManager = null
            }
        }

        val notification = buildNotification()

        // En Android 14+, startForeground con FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        // debe invocarse ANTES de que se cree el MediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (isScreenCaptureActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            if (fgsType != 0) {
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Si la acción era iniciar la captura, la iniciamos ahora que el servicio está
        // activamente registrado en primer plano con tipo mediaProjection
        if (intent?.action == ACTION_START_SCREEN_CAPTURE) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                if (screenCaptureManager == null) {
                    screenCaptureManager = ScreenCaptureManager(
                        context = applicationContext,
                        onFrameCaptured = { bitmap ->
                            liveManager.sendBitmapFrame(bitmap)
                        }
                    )
                }
                screenCaptureManager?.start(resultCode, resultData)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager?.stop()
        screenCaptureManager = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.live_mode_notification_channel_name)
            val descriptionText = getString(R.string.live_mode_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveSessionForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.live_mode_title))
            .setContentText(getString(R.string.live_mode_state_running))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.live_mode_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
