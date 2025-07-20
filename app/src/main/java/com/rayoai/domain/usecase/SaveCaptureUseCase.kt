package com.rayoai.domain.usecase

import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.model.CaptureEntity
import javax.inject.Inject

import com.rayoai.domain.model.ChatMessage

/**
 * Caso de uso para guardar una captura (imagen y su historial de chat) en la base de datos local.
 * Encapsula la lógica para persistir los resultados de las descripciones de imágenes.
 */
class SaveCaptureUseCase @Inject constructor(
    private val captureDao: CaptureDao
) {
    /**
     * Invoca el guardado o la actualización de una captura.
     * @param imageUri La URI de la imagen capturada o seleccionada.
     * @param chatHistory El historial de chat asociado a la imagen.
     * @param captureId El ID de la captura a actualizar (opcional). Si es nulo, se crea una nueva.
     * @return El ID de la captura guardada o actualizada.
     */
    suspend operator fun invoke(imageUri: String, chatHistory: List<ChatMessage>, captureId: Long? = null): Long {
        val capture = CaptureEntity(
            id = captureId ?: 0,
            imageUri = imageUri,
            chatHistory = chatHistory,
            timestamp = System.currentTimeMillis()
        )
        return captureDao.insertCapture(capture)
    }
}