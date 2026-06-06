package com.rayoai.domain.repository

import android.content.Context
import android.net.Uri
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.VideoAnalysisResult

/**
 * Repositorio para operaciones relacionadas con videos.
 */
interface VideoRepository {

    /**
     * Sube un video a la Files API de Gemini, espera a que esté listo,
     * y luego lo analiza usando el modelo Gemini.
     *
     * @param uri URI del video a analizar
     * @param context Contexto de Android para acceder al ContentResolver
     * @param systemPrompt Prompt del sistema para guiar el análisis
     * @return ResultWrapper con la descripción generada o un error
     */
    suspend fun uploadAndAnalyzeVideo(
        uri: Uri,
        context: Context,
        systemPrompt: String
    ): ResultWrapper<String>

    suspend fun analyzeVideoFromUrl(
        url: String,
        context: Context,
        systemPrompt: String,
        onStatus: (String) -> Unit = {}
    ): ResultWrapper<VideoAnalysisResult>

    fun isSupportedVideoUrl(url: String): Boolean
}
