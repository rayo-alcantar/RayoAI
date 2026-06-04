package com.rayoai.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaPlayer
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.rayoai.R
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.usecase.SaveCaptureUseCase
import com.rayoai.presentation.ui.AccessibilityCaptureModeActivity
import com.rayoai.presentation.ui.AccessibilityCaptureResultActivity
import com.rayoai.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class RayoAccessibilityService : AccessibilityService() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var describeImageUseCase: DescribeImageUseCase
    @Inject lateinit var saveCaptureUseCase: SaveCaptureUseCase
    @Inject lateinit var imageStorageManager: ImageStorageManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val isProcessing = AtomicBoolean(false)
    private val volumeSequence = ArrayDeque<Int>()
    private val volumeSequenceTimes = ArrayDeque<Long>()
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastAccessibilityFocusBounds: Rect? = null
    private var pendingFocusedElementBounds: Rect? = null
    private var captureModeReceiverRegistered = false
    private val captureModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AccessibilityCaptureModeActivity.ACTION_CAPTURE_MODE_SELECTED) return
            val mode = intent.getStringExtra(AccessibilityCaptureModeActivity.EXTRA_MODE)
            serviceScope.launch {
                handleCaptureMode(mode)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerAccessibilityButton()
        registerCaptureModeReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            lastAccessibilityFocusBounds = event.source?.extractVisibleBounds()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        accessibilityButtonCallback?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        accessibilityButtonCallback = null
        unregisterCaptureModeReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            clearVolumeSequence()
            return false
        }

        val matched = recordVolumeKey(event.keyCode, event.eventTime)
        if (matched) {
            requestQuickCapture()
            return true
        }
        return false
    }

    private fun registerAccessibilityButton() {
        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                requestQuickCapture()
            }
        }
        accessibilityButtonController.registerAccessibilityButtonCallback(callback)
        accessibilityButtonCallback = callback
    }

    private fun recordVolumeKey(keyCode: Int, eventTime: Long): Boolean {
        volumeSequence.addLast(keyCode)
        volumeSequenceTimes.addLast(eventTime)
        while (volumeSequence.size > REQUIRED_VOLUME_SEQUENCE_SIZE) {
            volumeSequence.removeFirst()
            volumeSequenceTimes.removeFirst()
        }

        val firstTime = volumeSequenceTimes.firstOrNull() ?: return false
        if (eventTime - firstTime > VOLUME_SEQUENCE_WINDOW_MS) {
            clearVolumeSequence()
            volumeSequence.addLast(keyCode)
            volumeSequenceTimes.addLast(eventTime)
            return false
        }

        val matched = volumeSequence.size == REQUIRED_VOLUME_SEQUENCE_SIZE &&
            volumeSequence.count { it == KeyEvent.KEYCODE_VOLUME_UP } == REQUIRED_VOLUME_KEY_REPEATS &&
            volumeSequence.count { it == KeyEvent.KEYCODE_VOLUME_DOWN } == REQUIRED_VOLUME_KEY_REPEATS
        if (matched) clearVolumeSequence()
        return matched
    }

    private fun clearVolumeSequence() {
        volumeSequence.clear()
        volumeSequenceTimes.clear()
    }

    private fun requestQuickCapture() {
        serviceScope.launch {
            val enabled = userPreferencesRepository.accessibilityQuickCaptureEnabled.first()
            if (!enabled) {
                showToast(getString(R.string.accessibility_quick_capture_disabled))
                return@launch
            }
            if (!isProcessing.compareAndSet(false, true)) {
                showToast(getString(R.string.accessibility_capture_already_running))
                return@launch
            }
            pendingFocusedElementBounds = findAccessibilityFocusBounds()
            openCaptureModeChooser()
        }
    }

    private suspend fun handleCaptureMode(mode: String?) {
        when (mode) {
            AccessibilityCaptureModeActivity.MODE_FULL_SCREEN -> {
                pendingFocusedElementBounds = null
                withContext(Dispatchers.Main) {
                    captureAndDescribeScreen(null)
                }
            }
            AccessibilityCaptureModeActivity.MODE_FOCUSED_ELEMENT -> {
                val bounds = pendingFocusedElementBounds ?: findAccessibilityFocusBounds()
                if (bounds == null) {
                    pendingFocusedElementBounds = null
                    isProcessing.set(false)
                    showToast(getString(R.string.accessibility_capture_no_focused_element))
                    return
                }
                pendingFocusedElementBounds = bounds
                withContext(Dispatchers.Main) {
                    captureAndDescribeScreen(bounds)
                }
            }
            else -> {
                pendingFocusedElementBounds = null
                isProcessing.set(false)
            }
        }
    }

    private fun captureAndDescribeScreen(focusedBounds: Rect?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            isProcessing.set(false)
            showToast(getString(R.string.accessibility_capture_requires_android_11))
            return
        }

        serviceScope.launch captureLaunch@{
            delay(CAPTURE_CHOOSER_DISMISS_DELAY_MS)
            showToast(getString(R.string.accessibility_capture_starting))
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        serviceScope.launch screenshotLaunch@{
                            val bitmap = screenshot.toBitmapCopy()
                            if (bitmap == null) {
                                isProcessing.set(false)
                                showToast(getString(R.string.accessibility_capture_failed))
                                return@screenshotLaunch
                            }
                            val imageForDescription = if (focusedBounds != null) {
                                val cropped = cropBitmapToBounds(bitmap, focusedBounds)
                                if (cropped == null) {
                                    isProcessing.set(false)
                                    showToast(getString(R.string.accessibility_capture_invalid_focused_element))
                                    return@screenshotLaunch
                                }
                                cropped
                            } else {
                                bitmap
                            }
                            describeScreenshot(imageForDescription)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        isProcessing.set(false)
                        showToast(screenshotErrorMessage(errorCode))
                    }
                }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toBitmapCopy(): Bitmap? {
        val buffer = hardwareBuffer
        return try {
            Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
    }

    private suspend fun describeScreenshot(bitmap: Bitmap) {
        try {
            showToast(getString(R.string.accessibility_capture_analyzing))
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                isProcessing.set(false)
                showToast(getString(R.string.accessibility_capture_missing_api_key))
                openMainActivity(null)
                return
            }

            val imageUri = imageStorageManager.saveBitmapAndGetUri(bitmap)
            if (imageUri == null) {
                isProcessing.set(false)
                showToast(getString(R.string.accessibility_capture_save_failed))
                return
            }
            playCaptureSound()

            val userMessage = ChatMessage(getString(R.string.accessibility_capture_chat_user_message), true)
            val prompt = getString(R.string.accessibility_capture_prompt)
            val model = userPreferencesRepository.defaultModel.first().ifBlank { GeminiModelConfig.DEFAULT_MODEL }
            val languageCode = Locale.getDefault().language

            withContext(Dispatchers.IO) {
                describeImageUseCase(
                    apiKey = apiKey,
                    image = bitmap,
                    userPrePrompt = prompt,
                    history = emptyList(),
                    languageCode = languageCode,
                    model = model
                ).collect { result ->
                    when (result) {
                        ResultWrapper.Loading -> Unit
                        is ResultWrapper.Success -> {
                            val messages = listOf(userMessage, ChatMessage(result.data, false))
                            val captureId = saveCaptureUseCase(listOf(imageUri.toString()), messages)
                            withContext(Dispatchers.Main) {
                                showToast(getString(R.string.accessibility_capture_ready))
                                openCaptureResult(captureId, result.data)
                            }
                        }
                        is ResultWrapper.Error -> {
                            withContext(Dispatchers.Main) {
                                showToast(getString(R.string.accessibility_capture_error, result.message))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            showToast(getString(R.string.accessibility_capture_unexpected_error))
        } finally {
            isProcessing.set(false)
        }
    }

    private fun openMainActivity(captureId: Long?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            captureId?.let { putExtra(MainActivity.EXTRA_CAPTURE_ID, it) }
        }
        startActivity(intent)
    }

    private fun openCaptureResult(captureId: Long, description: String) {
        val intent = Intent(this, AccessibilityCaptureResultActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AccessibilityCaptureResultActivity.EXTRA_CAPTURE_ID, captureId)
            putExtra(AccessibilityCaptureResultActivity.EXTRA_DESCRIPTION, description)
        }
        startActivity(intent)
    }

    private fun openCaptureModeChooser() {
        val intent = Intent(this, AccessibilityCaptureModeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun findAccessibilityFocusBounds(): Rect? {
        return try {
            findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.extractVisibleBounds()
                ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.extractVisibleBounds()
                ?: lastAccessibilityFocusBounds
        } catch (_: Exception) {
            lastAccessibilityFocusBounds
        }
    }

    private fun AccessibilityNodeInfo.extractVisibleBounds(): Rect? {
        val bounds = Rect()
        return try {
            getBoundsInScreen(bounds)
            bounds.takeIf { it.width() > 0 && it.height() > 0 }
        } finally {
            recycle()
        }
    }

    private fun cropBitmapToBounds(bitmap: Bitmap, bounds: Rect): Bitmap? {
        val safeBounds = Rect(bounds)
        val imageBounds = Rect(0, 0, bitmap.width, bitmap.height)
        if (!safeBounds.intersect(imageBounds)) return null
        if (safeBounds.width() <= 0 || safeBounds.height() <= 0) return null
        return Bitmap.createBitmap(
            bitmap,
            safeBounds.left,
            safeBounds.top,
            safeBounds.width(),
            safeBounds.height()
        )
    }

    private fun registerCaptureModeReceiver() {
        if (captureModeReceiverRegistered) return
        val filter = IntentFilter(AccessibilityCaptureModeActivity.ACTION_CAPTURE_MODE_SELECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                this,
                captureModeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        captureModeReceiverRegistered = true
    }

    private fun unregisterCaptureModeReceiver() {
        if (!captureModeReceiverRegistered) return
        unregisterReceiver(captureModeReceiver)
        captureModeReceiverRegistered = false
    }

    private fun screenshotErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> getString(R.string.accessibility_capture_too_soon)
            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> getString(R.string.accessibility_capture_invalid_display)
            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> getString(R.string.accessibility_capture_no_access)
            ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> getString(R.string.accessibility_capture_secure_window)
            else -> getString(R.string.accessibility_capture_failed)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun playCaptureSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.send)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (_: Exception) {
            // Audio feedback is helpful, but capture processing should not fail if playback fails.
        }
    }

    companion object {
        private const val VOLUME_SEQUENCE_WINDOW_MS = 2200L
        private const val CAPTURE_CHOOSER_DISMISS_DELAY_MS = 350L
        private const val REQUIRED_VOLUME_SEQUENCE_SIZE = 4
        private const val REQUIRED_VOLUME_KEY_REPEATS = 2
    }
}
