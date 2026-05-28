package com.rayoai.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.rayoai.R
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.usecase.SaveCaptureUseCase
import com.rayoai.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerAccessibilityButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        accessibilityButtonCallback?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        accessibilityButtonCallback = null
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
        while (volumeSequence.size > REQUIRED_VOLUME_SEQUENCE.size) {
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

        val matched = volumeSequence.size == REQUIRED_VOLUME_SEQUENCE.size &&
            volumeSequence.toList() == REQUIRED_VOLUME_SEQUENCE
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
            captureAndDescribeScreen()
        }
    }

    private fun captureAndDescribeScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            isProcessing.set(false)
            showToast(getString(R.string.accessibility_capture_requires_android_11))
            return
        }

        showToast(getString(R.string.accessibility_capture_starting))
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    serviceScope.launch {
                        val bitmap = screenshot.toBitmapCopy()
                        if (bitmap == null) {
                            isProcessing.set(false)
                            showToast(getString(R.string.accessibility_capture_failed))
                            return@launch
                        }
                        describeScreenshot(bitmap)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    isProcessing.set(false)
                    showToast(screenshotErrorMessage(errorCode))
                }
            }
        )
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
                                openMainActivity(captureId)
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
        private val REQUIRED_VOLUME_SEQUENCE = listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN
        )
    }
}
