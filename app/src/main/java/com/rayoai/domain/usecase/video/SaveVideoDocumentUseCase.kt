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
        sizeBytes: Long,
        geminiFileUri: String? = null,
        geminiFileName: String? = null,
        geminiMimeType: String? = null,
        geminiFileExpiresAt: Long? = null
    ): Long {
        val entity = VideoDocumentEntity(
            name = name,
            uri = uri,
            content = content,
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            sizeBytes = sizeBytes,
            geminiFileUri = geminiFileUri,
            geminiFileName = geminiFileName,
            geminiMimeType = geminiMimeType,
            geminiFileExpiresAt = geminiFileExpiresAt
        )
        return videoDao.insert(entity)
    }
}
