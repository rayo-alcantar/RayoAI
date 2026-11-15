package com.rayoai.presentation.ui.components

import com.rayoai.presentation.ui.screens.home.FocusAssistMode
import com.rayoai.presentation.ui.screens.home.PortraitMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFeatureCompatibilityPolicyTest {

    @Test
    fun canEnableFocusAssist_disallowedOnlyInHardwarePortrait() {
        assertFalse(CameraFeatureCompatibilityPolicy.canEnableFocusAssist(PortraitMode.HARDWARE))
        assertTrue(CameraFeatureCompatibilityPolicy.canEnableFocusAssist(PortraitMode.SOFTWARE))
        assertTrue(CameraFeatureCompatibilityPolicy.canEnableFocusAssist(PortraitMode.OFF))
    }

    @Test
    fun canEnableHardwarePortrait_allowedOnlyWhenFocusAssistIsOff() {
        assertTrue(CameraFeatureCompatibilityPolicy.canEnableHardwarePortrait(FocusAssistMode.OFF))
        assertFalse(CameraFeatureCompatibilityPolicy.canEnableHardwarePortrait(FocusAssistMode.ON))
        assertFalse(CameraFeatureCompatibilityPolicy.canEnableHardwarePortrait(FocusAssistMode.AUTO_CAPTURE))
    }

    @Test
    fun shouldEnableImageAnalysis_requiresFocusAssistAndNonHardwarePortrait() {
        assertFalse(
            CameraFeatureCompatibilityPolicy.shouldEnableImageAnalysis(
                isFocusAssistEnabled = false,
                portraitMode = PortraitMode.OFF
            )
        )
        assertFalse(
            CameraFeatureCompatibilityPolicy.shouldEnableImageAnalysis(
                isFocusAssistEnabled = true,
                portraitMode = PortraitMode.HARDWARE
            )
        )
        assertTrue(
            CameraFeatureCompatibilityPolicy.shouldEnableImageAnalysis(
                isFocusAssistEnabled = true,
                portraitMode = PortraitMode.SOFTWARE
            )
        )
        assertTrue(
            CameraFeatureCompatibilityPolicy.shouldEnableImageAnalysis(
                isFocusAssistEnabled = true,
                portraitMode = PortraitMode.OFF
            )
        )
    }

    @Test
    fun canUseHardwareBokeh_disallowedWhenFocusAssistIsActive() {
        assertFalse(
            CameraFeatureCompatibilityPolicy.canUseHardwareBokeh(
                portraitMode = PortraitMode.HARDWARE,
                isBokehAvailable = true,
                hasExtensionManager = true,
                isFocusAssistEnabled = true
            )
        )
        assertTrue(
            CameraFeatureCompatibilityPolicy.canUseHardwareBokeh(
                portraitMode = PortraitMode.HARDWARE,
                isBokehAvailable = true,
                hasExtensionManager = true,
                isFocusAssistEnabled = false
            )
        )
        assertFalse(
            CameraFeatureCompatibilityPolicy.canUseHardwareBokeh(
                portraitMode = PortraitMode.SOFTWARE,
                isBokehAvailable = true,
                hasExtensionManager = true,
                isFocusAssistEnabled = false
            )
        )
        assertFalse(
            CameraFeatureCompatibilityPolicy.canUseHardwareBokeh(
                portraitMode = PortraitMode.HARDWARE,
                isBokehAvailable = false,
                hasExtensionManager = true,
                isFocusAssistEnabled = false
            )
        )
        assertFalse(
            CameraFeatureCompatibilityPolicy.canUseHardwareBokeh(
                portraitMode = PortraitMode.HARDWARE,
                isBokehAvailable = true,
                hasExtensionManager = false,
                isFocusAssistEnabled = false
            )
        )
    }
}
