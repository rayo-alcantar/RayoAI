package com.rayoai.domain.usecase.video

import com.rayoai.data.local.videodb.VideoChatDao
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetVideoChatMessagesUseCase @Inject constructor(
    private val videoChatDao: VideoChatDao
) {
    operator fun invoke(videoDocumentId: Long): Flow<List<ChatMessage>> =
        videoChatDao.getMessages(videoDocumentId).map { messages ->
            messages.map { ChatMessage(content = it.content, isFromUser = it.isFromUser) }
        }
}
