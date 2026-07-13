package com.rayoai.domain.usecase.video

import com.rayoai.data.local.videodb.VideoChatDao
import com.rayoai.data.local.videodb.VideoChatMessageEntity
import javax.inject.Inject

class SaveVideoChatMessageUseCase @Inject constructor(
    private val videoChatDao: VideoChatDao
) {
    suspend operator fun invoke(videoDocumentId: Long, content: String, isFromUser: Boolean): Long =
        videoChatDao.insert(
            VideoChatMessageEntity(
                videoDocumentId = videoDocumentId,
                content = content,
                isFromUser = isFromUser,
                timestamp = System.currentTimeMillis()
            )
        )
}
