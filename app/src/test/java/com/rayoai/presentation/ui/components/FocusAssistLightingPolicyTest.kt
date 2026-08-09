package com.rayoai.presentation.ui.components

import androidx.camera.core.ImageCapture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAssistLightingPolicyTest {
    @Test
    fun flashOff_neverUsesTorch() {
        assertFalse(
            FocusAssistLightingPolicy.shouldUseTorch(
                focusAssistEnabled = true,
                flashMode = ImageCapture.FLASH_MODE_OFF,
                autoTorchEnabled = true
            )
        )
    }

    @Test
    fun flashOn_usesTorchOnlyWhileFocusAssistIsActive() {
        assertTrue(
            FocusAssistLightingPolicy.shouldUseTorch(
                focusAssistEnabled = true,
                flashMode = ImageCapture.FLASH_MODE_ON,
                autoTorchEnabled = false
            )
        )
        assertFalse(
            FocusAssistLightingPolicy.shouldUseTorch(
                focusAssistEnabled = false,
                flashMode = ImageCapture.FLASH_MODE_ON,
                autoTorchEnabled = true
            )
        )
    }

    @Test
    fun automaticFlash_usesHysteresisToAvoidFlickering() {
        assertTrue(FocusAssistLightingPolicy.updateAutoTorchFromLux(previous = false, lux = 10f))
        assertTrue(FocusAssistLightingPolicy.updateAutoTorchFromLux(previous = true, lux = 30f))
        assertFalse(FocusAssistLightingPolicy.updateAutoTorchFromLux(previous = true, lux = 60f))
    }

    @Test
    fun darkFrame_isDetectedAsFallback() {
        assertTrue(FocusAssistLightingPolicy.isFrameTooDark(20f))
        assertFalse(FocusAssistLightingPolicy.isFrameTooDark(100f))
    }
}
