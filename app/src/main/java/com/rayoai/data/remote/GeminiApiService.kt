package com.rayoai.data.remote

import com.rayoai.data.remote.dto.GeminiRequest
import com.rayoai.data.remote.dto.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Servicio de API Retrofit para Google Gemini REST API v1 beta.
 * 
 * Esta interfaz define los endpoints para interactuar con los modelos de Gemini.
 * Usa la API REST directamente en lugar del SDK deprecado.
 */
interface GeminiApiService {
    
    /**
     * Genera contenido usando un modelo de Gemini.
     * 
     * @param model Nombre del modelo (ej. "gemini-2.0-flash", "gemini-2.5-pro")
     * @param apiKey API key de Google AI Studio
     * @param request Cuerpo de la petición con system instructions, contenido e historial
     * @return Response con la respuesta del modelo o error HTTP
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
