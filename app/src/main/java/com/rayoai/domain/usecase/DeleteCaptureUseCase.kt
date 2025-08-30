package com.rayoai.domain.usecase

import com.rayoai.domain.model.Capture
import com.rayoai.domain.repository.VisionRepository
import javax.inject.Inject

class DeleteCaptureUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    suspend operator fun invoke(capture: Capture) {
        visionRepository.deleteCapture(capture)
    }
}