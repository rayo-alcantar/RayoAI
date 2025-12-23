package com.rayoai.domain.usecase.video

import com.rayoai.data.local.videodb.VideoDao
import javax.inject.Inject

/**
 * Caso de uso para eliminar un video de la base de datos.
 */
class DeleteVideoDocumentUseCase @Inject constructor(
    private val videoDao: VideoDao
) {
    suspend operator fun invoke(id: Long) {
        videoDao.deleteById(id)
    }
}
