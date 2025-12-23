package com.rayoai.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs para la API REST v1 beta de Google Gemini.
 * Estos modelos representan la estructura JSON exacta esperada por la API.
 */

// ============ REQUEST MODELS ============

/**
 * Petición raíz para la API de Gemini.
 */
data class GeminiRequest(
    @SerializedName("system_instruction") val systemInstruction: SystemInstruction? = null,
    @SerializedName("contents") val contents: List<ContentDto>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfigDto? = null
)

/**
 * Instrucciones del sistema que definen el comportamiento del modelo.
 * Esto es crítico para separar las instrucciones del sistema del contenido del usuario.
 */
data class SystemInstruction(
    @SerializedName("parts") val parts: List<PartDto>
)

/**
 * Representa un mensaje en la conversación.
 * @param role Puede ser "user" o "model"
 */
data class ContentDto(
    @SerializedName("role") val role: String,
    @SerializedName("parts") val parts: List<PartDto>
)

/**
 * Una parte del contenido, puede ser texto o datos inline (como imágenes).
 */
data class PartDto(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inline_data") val inlineData: InlineDataDto? = null
)

/**
 * Datos inline para imágenes codificadas en Base64.
 */
data class InlineDataDto(
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("data") val data: String // Base64 encoded
)

/**
 * Configuración de generación opcional.
 */
data class GenerationConfigDto(
    @SerializedName("temperature") val temperature: Float? = null,
    @SerializedName("topK") val topK: Int? = null,
    @SerializedName("topP") val topP: Float? = null,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = null
)

// ============ RESPONSE MODELS ============

/**
 * Respuesta de la API de Gemini.
 */
data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<CandidateDto>?,
    @SerializedName("promptFeedback") val promptFeedback: PromptFeedbackDto? = null
)

/**
 * Un candidato de respuesta del modelo.
 */
data class CandidateDto(
    @SerializedName("content") val content: ContentDto?,
    @SerializedName("finishReason") val finishReason: String?,
    @SerializedName("safetyRatings") val safetyRatings: List<SafetyRatingDto>? = null
)

/**
 * Evaluación de seguridad del contenido.
 */
data class SafetyRatingDto(
    @SerializedName("category") val category: String,
    @SerializedName("probability") val probability: String
)

/**
 * Feedback sobre el prompt enviado.
 */
data class PromptFeedbackDto(
    @SerializedName("blockReason") val blockReason: String? = null,
    @SerializedName("safetyRatings") val safetyRatings: List<SafetyRatingDto>? = null
)
