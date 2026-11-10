package com.rayoai.presentation.ui.screens.tools.live

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.rayoai.R
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LiveFloatingControlsState @Inject constructor() {
    val enabled = MutableStateFlow(false)
    val liveScreenVisible = MutableStateFlow(false)
}

internal class LiveFloatingControls(
    private val context: Context,
    private val onToggleMic: () -> Unit,
    private val onToggleVideo: () -> Unit,
    private val onStop: () -> Unit,
    private val onHide: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val buttonContext = ContextThemeWrapper(context, android.R.style.Theme_Material)
    private var panel: LinearLayout? = null
    private var micButton: Button? = null
    private var videoButton: Button? = null

    fun update(visible: Boolean, micMuted: Boolean, videoPaused: Boolean, hasVideo: Boolean) {
        if (!visible || !Settings.canDrawOverlays(context)) {
            hide()
            return
        }
        if (panel == null) show()

        micButton?.apply {
            setText(micActionLabel(micMuted))
            ViewCompat.setStateDescription(
                this, context.getString(if (micMuted) R.string.live_mode_mic_muted else R.string.live_mode_mic_unmuted)
            )
        }
        videoButton?.apply {
            isVisible = hasVideo
            setText(videoActionLabel(videoPaused))
        }
    }

    private fun show() {
        val view = LinearLayout(buttonContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 28, 32))
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(6).toFloat()
            ViewCompat.setAccessibilityPaneTitle(this, context.getString(R.string.live_mode_floating_controls))
        }
        val mic = addButton(view, R.string.live_mode_mute_mic, onToggleMic)
        val video = addButton(view, R.string.live_mode_pause_video, onToggleVideo)
        addButton(view, R.string.live_mode_stop, onStop)
        addButton(view, R.string.live_mode_hide_floating_controls, onHide)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(12)
            y = dp(24)
        }
        try {
            windowManager.addView(view, params)
            panel = view
            micButton = mic
            videoButton = video
        } catch (e: RuntimeException) {
            Log.w("LiveFloatingControls", "Could not show floating controls", e)
        }
    }

    fun hide() {
        val view = panel ?: return
        panel = null
        micButton = null
        videoButton = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // The system may already have removed the window after permission was revoked.
        }
    }

    private fun addButton(parent: LinearLayout, label: Int, action: () -> Unit): Button =
        Button(buttonContext, null, android.R.attr.borderlessButtonStyle).apply {
            setText(label)
            setAllCaps(false)
            minimumHeight = dp(48)
            setTextColor(Color.WHITE)
            setOnClickListener { action() }
            parent.addView(
                this,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
        }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}

internal fun micActionLabel(muted: Boolean): Int =
    if (muted) R.string.live_mode_unmute_mic else R.string.live_mode_mute_mic

internal fun videoActionLabel(paused: Boolean): Int =
    if (paused) R.string.live_mode_resume_video else R.string.live_mode_pause_video
