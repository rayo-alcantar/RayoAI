package com.rayoai.domain.usecase

import com.rayoai.domain.repository.VisionRepository
import javax.inject.Inject

class UpdateChatFavoriteStateUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    suspend operator fun invoke(id: Long, isFavorite: Boolean) {
        visionRepository.updateChatFavoriteState(id, isFavorite)
    }
}
