package com.rayoai.domain.usecase

import com.rayoai.domain.model.Capture
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHistoryUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    operator fun invoke(showHidden: Boolean): Flow<List<Capture>> {
        return visionRepository.getHistory(showHidden)
    }
}