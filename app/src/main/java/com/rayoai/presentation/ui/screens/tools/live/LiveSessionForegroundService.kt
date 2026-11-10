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
import com.rayoai.domain.model.live.LiveSessionState
import com.rayoai.domain.model.live.VisualSource
import com.rayoai.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveSessionForegroundService : Service() {

    @Inject
    lateinit var liveManager: GeminiLiveManager

    @Inject
    lateinit var floatingState: LiveFloatingControlsState

    private val controlsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var floatingControls: LiveFloatingControls? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var isScreenCaptureActive = false
    private var activeCapturedAppName: String? = null

    companion object {
        const val CHANNEL_ID = "rayoai_live_session_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP = "com.rayoai.live.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "com.rayoai.live.ACTION_TOGGLE_PAUSE"
        private const val ACTION_TOGGLE_MIC = "com.rayoai.live.ACTION_TOGGLE_MIC"
        const val ACTION_START_SCREEN_CAPTURE = "com.rayoai.live.ACTION_START_SCREEN_CAPTURE"
        const val ACTION_STOP_SCREEN_CAPTURE = "com.rayoai.live.ACTION_STOP_SCREEN_CAPTURE"

        const val EXTRA_RESULT_CODE = "com.rayoai.live.EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.rayoai.live.EXTRA_RESULT_DATA"
        const val EXTRA_APP_NAME = "com.rayoai.live.EXTRA_APP_NAME"

        fun start(context: Context) {
            val intent = Intent(context, LiveSessionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startScreenCapture(context: Context, resultCode: Int, data: Intent, appName: String? = null) {
            val intent = Intent(context, LiveSessionForegroundService::class.java).apply {
                action = ACTION_START_SCREEN_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                if (appName != null) {
                    putExtra(EXTRA_APP_NAME, appName)
                }
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
        val action = intent?.action
        if ((action == ACTION_TOGGLE_PAUSE || action == ACTION_TOGGLE_MIC) &&
            liveManager.sessionState.value == LiveSessionState.STOPPED
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                liveManager.toggleVideoPause()
            }
            ACTION_TOGGLE_MIC -> {
                liveManager.toggleMicMute()
            }
            ACTION_START_SCREEN_CAPTURE -> {
                isScreenCaptureActive = true
                intent?.getStringExtra(EXTRA_APP_NAME)?.let {
                    activeCapturedAppName = it
                }
            }
            ACTION_STOP_SCREEN_CAPTURE -> {
                isScreenCaptureActive = false
                activeCapturedAppName = null
                screenCaptureManager?.stop()
                screenCaptureManager = null
                liveManager.setVisualSource(VisualSource.NONE)
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

        // Si se detuvo la captura pero el servicio sigue corriendo (solo audio), actualizar notificación
        if (intent?.action == ACTION_STOP_SCREEN_CAPTURE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
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

        observeFloatingControls()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controlsScope.cancel()
        floatingControls?.hide()
        floatingControls = null
        floatingState.enabled.value = false
        isScreenCaptureActive = false
        activeCapturedAppName = null
        screenCaptureManager?.stop()
        screenCaptureManager = null
        liveManager.stopSession()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun observeFloatingControls() {
        if (floatingControls != null) return
        val controls = LiveFloatingControls(
            context = this,
            onToggleMic = liveManager::toggleMicMute,
            onToggleVideo = liveManager::toggleVideoPause,
            onStop = { stopSelf() },
            onHide = { floatingState.enabled.value = false }
        )
        floatingControls = controls

        // Close the service even when the Live screen is not collecting its state.
        // Screen sharing can start the service just before the Live connection begins.
        controlsScope.launch {
            liveManager.sessionState
                .dropWhile { it == LiveSessionState.STOPPED }
                .first { it == LiveSessionState.STOPPED }
            stopSelf()
        }

        val showOverlay = combine(
            liveManager.sessionState, floatingState.enabled, floatingState.liveScreenVisible
        ) { sessionState, enabled, screenVisible ->
            enabled && !screenVisible && sessionState == LiveSessionState.RUNNING
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        controlsScope.launch {
            combine(
                showOverlay, liveManager.isMicMuted, liveManager.isVideoPaused, liveManager.visualSource
            ) { show, micMuted, videoPaused, source ->
                controls.update(show, micMuted, videoPaused, source != VisualSource.NONE)
            }.collect {
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
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

        val title = if (isScreenCaptureActive) {
            val appName = activeCapturedAppName
            if (!appName.isNullOrBlank()) {
                getString(R.string.live_mode_screen_notification_title_app, appName)
            } else {
                getString(R.string.live_mode_screen_notification_title)
            }
        } else {
            getString(R.string.live_mode_title)
        }

        val contentText = if (isScreenCaptureActive) {
            getString(R.string.live_mode_screen_notification_desc)
        } else {
            getString(R.string.live_mode_state_running)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        builder.addAction(
            android.R.drawable.ic_btn_speak_now,
            getString(micActionLabel(liveManager.isMicMuted.value)),
            controlPendingIntent(ACTION_TOGGLE_MIC, 3)
        )

        if (isScreenCaptureActive || liveManager.visualSource.value != VisualSource.NONE) {
            builder.addAction(
                if (liveManager.isVideoPaused.value) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                getString(videoActionLabel(liveManager.isVideoPaused.value)),
                controlPendingIntent(ACTION_TOGGLE_PAUSE, 4)
            )
        }

        builder.addAction(
            android.R.drawable.ic_lock_power_off,
            getString(R.string.live_mode_stop),
            stopPendingIntent
        )

        return builder.build()
    }

    private fun controlPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LiveSessionForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
