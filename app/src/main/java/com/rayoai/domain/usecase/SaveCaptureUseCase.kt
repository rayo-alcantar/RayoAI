package com.rayoai.domain.usecase

import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.model.CaptureEntity
import javax.inject.Inject

/**
 * Caso de uso para guardar una captura (imagen y su descripción) en la base de datos local.
 * Encapsula la lógica para persistir los resultados de las descripciones de imágenes.
 */
class SaveCaptureUseCase @Inject constructor(
    private val captureDao: CaptureDao
) {
    /**
     * Invoca el guardado de una captura.
     * @param imageUri La URI de la imagen capturada o seleccionada.
     * @param description La descripción de texto generada para la imagen.
     */
    suspend operator fun invoke(imageUri: String, description: String) {
        // Crea una nueva entidad de captura con la información proporcionada y la marca de tiempo actual.
        val capture = CaptureEntity(
            imageUri = imageUri,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        // Inserta la captura en la base de datos a través del DAO.
        captureDao.insertCapture(capture)
    }
}