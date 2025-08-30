package com.rayoai.domain.usecase

import com.rayoai.domain.repository.VisionRepository
import javax.inject.Inject

class DeleteAllCapturesUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    suspend operator fun invoke() {
        visionRepository.deleteAllCaptures()
    }
}