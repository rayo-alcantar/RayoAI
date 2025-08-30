package com.rayoai.domain.usecase

import com.rayoai.domain.repository.VisionRepository
import javax.inject.Inject

class UpdateChatHiddenStateUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    suspend operator fun invoke(id: Long, isHidden: Boolean) {
        visionRepository.updateChatHiddenState(id, isHidden)
    }
}