package com.rayoai.presentation.ui.components

import androidx.camera.core.ImageCapture

internal object FocusAssistLightingPolicy {
    private const val TORCH_ON_LUX = 20f
    private const val TORCH_OFF_LUX = 45f
    private const val LOW_FRAME_LUMA = 48f

    fun shouldUseTorch(
        focusAssistEnabled: Boolean,
        flashMode: Int,
        autoTorchEnabled: Boolean
    ): Boolean {
        if (!focusAssistEnabled) return false
        return when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> true
            ImageCapture.FLASH_MODE_AUTO -> autoTorchEnabled
            else -> false
        }
    }

    fun updateAutoTorchFromLux(previous: Boolean, lux: Float): Boolean = when {
        !lux.isFinite() -> previous
        previous && lux >= TORCH_OFF_LUX -> false
        !previous && lux <= TORCH_ON_LUX -> true
        else -> previous
    }

    fun isFrameTooDark(averageLuma: Float): Boolean =
        averageLuma.isFinite() && averageLuma < LOW_FRAME_LUMA
}
