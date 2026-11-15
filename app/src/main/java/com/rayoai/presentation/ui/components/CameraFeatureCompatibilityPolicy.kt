package com.rayoai.presentation.ui.components

import com.rayoai.presentation.ui.screens.home.FocusAssistMode
import com.rayoai.presentation.ui.screens.home.PortraitMode

internal object CameraFeatureCompatibilityPolicy {

    /**
     * CameraX vendor extensions (such as BOKEH for hardware portrait) do not support
     * ImageAnalysis concurrently. Focus assist requires ImageAnalysis for ML Kit face detection.
     */
    fun canEnableFocusAssist(portraitMode: PortraitMode): Boolean =
        portraitMode != PortraitMode.HARDWARE

    fun canEnableHardwarePortrait(focusAssistMode: FocusAssistMode): Boolean =
        focusAssistMode == FocusAssistMode.OFF

    fun shouldEnableImageAnalysis(
        isFocusAssistEnabled: Boolean,
        portraitMode: PortraitMode
    ): Boolean = isFocusAssistEnabled && portraitMode != PortraitMode.HARDWARE

    fun canUseHardwareBokeh(
        portraitMode: PortraitMode,
        isBokehAvailable: Boolean,
        hasExtensionManager: Boolean,
        isFocusAssistEnabled: Boolean
    ): Boolean = portraitMode == PortraitMode.HARDWARE &&
        isBokehAvailable &&
        hasExtensionManager &&
        !isFocusAssistEnabled
}
