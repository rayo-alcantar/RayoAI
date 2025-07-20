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
     * Invoca el guardado de una captura.
     * @param imageUri La URI de la imagen capturada o seleccionada.
     * @param chatHistory El historial de chat asociado a la imagen.
     */
    suspend operator fun invoke(imageUri: String, chatHistory: List<ChatMessage>) {
        // Crea una nueva entidad de captura con la información proporcionada y la marca de tiempo actual.
        val capture = CaptureEntity(
            imageUri = imageUri,
            chatHistory = chatHistory,
            timestamp = System.currentTimeMillis()
        )
        // Inserta la captura en la base de datos a través del DAO.
        captureDao.insertCapture(capture)
    }
}