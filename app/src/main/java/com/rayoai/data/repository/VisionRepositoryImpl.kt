package com.rayoai.data.repository

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementación concreta de [VisionRepository] que interactúa con la API de Google Gemini.
 * Utiliza el modelo 'gemini-2.0-flash' para generar contenido (descripciones de imágenes o respuestas de chat).
 */
class VisionRepositoryImpl @Inject constructor() : VisionRepository {

    /**
     * Genera contenido (descripción de imagen o respuesta de chat) utilizando el modelo Gemini.
     * @param apiKey La clave de API para autenticarse con el servicio Gemini.
     * @param prompt El texto de la pregunta o la instrucción inicial para el modelo.
     * @param image (Opcional) La imagen [Bitmap] a describir. Si es nula, se asume un chat de solo texto.
     * @param history El historial de mensajes [ChatMessage] para mantener el contexto en conversaciones multipaso.
     * @return Un [Flow] que emite el estado de la operación ([ResultWrapper.Loading], [ResultWrapper.Success], [ResultWrapper.Error]).
     */
    override fun generateContent(
        apiKey: String,
        prompt: String,
        image: Bitmap?,
        history: List<ChatMessage>
    ): Flow<ResultWrapper<String>> = flow {
        emit(ResultWrapper.Loading) // Emitir estado de carga al inicio de la operación.

        val contents = mutableListOf<Content>()

        // Añadir el historial de chat previo para mantener el contexto.
        // Se mapea el ChatMessage de dominio a Content del SDK de Gemini.
        history.forEach { chatMessage ->
            val participant = if (chatMessage.isFromUser) "user" else "model"
            contents.add(content(participant) { text(chatMessage.content) })
        }

        // Añadir el contenido actual (imagen y/o texto).
        val currentContent = content {
            image?.let { image(it) } // Si hay imagen, añadirla al contenido.
            text(prompt) // Añadir el prompt de texto.
        }
        contents.add(currentContent)

        try {
            // Intento 1: gemini-2.0-flash
            val generativeModelFlash = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey
            )
            val responseFlash = generativeModelFlash.generateContent(*contents.toTypedArray())
            val responseTextFlash = responseFlash.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.let { part ->
                    when (part) {
                        is com.google.ai.client.generativeai.type.TextPart -> part.text
                        else -> null
                    }
                }
            responseTextFlash?.let {
                emit(ResultWrapper.Success(it))
                return@flow // Salir si tiene éxito
            }
        } catch (e: Exception) {
            // Log del error del primer intento, pero no emitir aún.
            // Puedes añadir un Log.e aquí si lo deseas para depuración.
            // Log.e("VisionRepositoryImpl", "Error with gemini-2.0-flash: ${e.localizedMessage}")
        }

        try {
            // Intento 2: gemini-1.5-flash (fallback)
            val generativeModel1_5Flash = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            val response1_5Flash = generativeModel1_5Flash.generateContent(*contents.toTypedArray())
            val responseText1_5Flash = response1_5Flash.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.let { part ->
                    when (part) {
                        is com.google.ai.client.generativeai.type.TextPart -> part.text
                        else -> null
                    }
                }
            responseText1_5Flash?.let {
                emit(ResultWrapper.Success(it))
                return@flow // Salir si tiene éxito
            } ?: emit(ResultWrapper.Error("Empty response from both APIs."))
        } catch (e: Exception) {
            // Si ambos fallan, emitir el error del segundo intento.
            emit(ResultWrapper.Error(e.localizedMessage ?: "An unknown error occurred after multiple attempts"))
        }
    }
}