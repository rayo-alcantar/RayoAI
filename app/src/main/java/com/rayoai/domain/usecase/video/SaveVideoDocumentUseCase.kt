package com.rayoai.domain.usecase.video

import com.rayoai.data.local.videodb.VideoDao
import com.rayoai.data.local.videodb.VideoDocumentEntity
import javax.inject.Inject

/**
 * Caso de uso para guardar un video analizado en la base de datos.
 */
class SaveVideoDocumentUseCase @Inject constructor(
    private val videoDao: VideoDao
) {
    suspend operator fun invoke(
        name: String,
        uri: String,
        content: String,
        timestamp: Long,
        durationSeconds: Int,
        sizeBytes: Long
    ): Long {
        val entity = VideoDocumentEntity(
            name = name,
            uri = uri,
            content = content,
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            sizeBytes = sizeBytes
        )
        return videoDao.insert(entity)
    }
}
