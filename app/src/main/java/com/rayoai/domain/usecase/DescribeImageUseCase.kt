package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Caso de uso para describir una imagen utilizando el modelo de visión de Gemini.
 * Este caso de uso encapsula la lógica de negocio para la descripción inicial de una imagen.
 */
class DescribeImageUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    /**
     * Invoca la descripción de una imagen.
     * @param apiKey La clave de API para autenticar la llamada al modelo Gemini.
     * @param image La imagen [Bitmap] a describir.
     * @param history El historial de chat opcional para mantener el contexto (aunque para la descripción inicial suele estar vacío).
     * @return Un [Flow] que emite el estado de la operación ([ResultWrapper.Loading], [ResultWrapper.Success], [ResultWrapper.Error]).
     */
    operator fun invoke(
        apiKey: String,
        image: Bitmap,
        history: List<ChatMessage> = emptyList()
    ): Flow<ResultWrapper<String>> {
        // Prompt inicial para la descripción de la imagen. Se busca una descripción precisa y concisa.
        val prompt = """Eres un asistente de visión artificial diseñado para ayudar a personas ciegas o con baja visión. Tu objetivo es describir imágenes de la manera más útil, concisa y accesible posible. Prioriza la información relevante para la navegación, identificación de objetos, personas, texto visible y el contexto general de la escena. Utiliza un lenguaje claro, directo y neutro. Evita adornos innecesarios. Si hay texto en la imagen, transcríbelo. Si hay personas, describe su apariencia general y acciones sin juicios. Si la imagen es irrelevante o no contiene información útil, indícalo. Siempre responde en español."""
        // Delega la llamada al repositorio de visión para interactuar con el modelo Gemini.
        return visionRepository.generateContent(apiKey, prompt, image, history)
    }
}