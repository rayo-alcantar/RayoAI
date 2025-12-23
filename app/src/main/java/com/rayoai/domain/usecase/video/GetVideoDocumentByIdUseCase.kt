package com.rayoai.domain.usecase.video

import com.rayoai.data.local.videodb.VideoDao
import com.rayoai.data.local.videodb.VideoDocumentEntity
import com.rayoai.domain.model.VideoDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Caso de uso para obtener un video específico por ID.
 */
class GetVideoDocumentByIdUseCase @Inject constructor(
    private val videoDao: VideoDao
) {
    operator fun invoke(id: Long): Flow<VideoDocument?> {
        return videoDao.getVideoById(id).map { entity ->
            entity?.toDomain()
        }
    }

    private fun VideoDocumentEntity.toDomain() = VideoDocument(
        id = id,
        name = name,
        uri = uri,
        content = content,
        timestamp = timestamp,
        durationSeconds = durationSeconds,
        sizeBytes = sizeBytes
    )
}
