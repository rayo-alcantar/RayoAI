package com.rayoai.domain.usecase

import android.util.Base64
import com.rayoai.core.ResultWrapper
import com.rayoai.data.remote.GeminiApiService
import com.rayoai.data.remote.dto.ContentDto
import com.rayoai.data.remote.dto.GeminiRequest
import com.rayoai.data.remote.dto.GenerationConfigDto
import com.rayoai.data.remote.dto.InlineDataDto
import com.rayoai.data.remote.dto.PartDto
import com.rayoai.data.remote.dto.SystemInstruction
import com.rayoai.data.remote.dto.ThinkingConfigDto
import com.rayoai.domain.model.GeminiModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class TranscribeAudioUseCase @Inject constructor(
    private val geminiApiService: GeminiApiService
) {
    suspend operator fun invoke(
        apiKey: String,
        audioFile: File,
        model: String = GeminiModelConfig.DEFAULT_MODEL
    ): ResultWrapper<String> = withContext(Dispatchers.IO) {
        val prompt = """
            Transcribe el audio exactamente como fue hablado.
            Reglas:
            1. Devuelve solo la transcripcion, sin explicaciones ni encabezados.
            2. No resumas, no corrijas estilo, no reordenes ideas y no elimines muletillas.
            3. Conserva palabras repetidas, pausas verbalizadas, cambios de idioma y errores del hablante.
            4. Si una parte es inaudible, escribe [inaudible] en ese punto.
        """.trimIndent()

        val audioBytes = audioFile.readBytes()
        val request = GeminiRequest(
            systemInstruction = SystemInstruction(
                parts = listOf(PartDto(text = "Eres un transcriptor literal y conservador."))
            ),
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = listOf(
                        PartDto(text = prompt),
                        PartDto(
                            inlineData = InlineDataDto(
                                mimeType = "audio/wav",
                                data = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                            )
                        )
                    )
                )
            ),
            generationConfig = GenerationConfigDto(
                temperature = 0f,
                thinkingConfig = ThinkingConfigDto(
                    includeThoughts = false,
                    thinkingLevel = "MINIMAL"
                )
            )
        )

        val errors = mutableListOf<String>()
        for (modelName in buildModelPriority(model)) {
            try {
                val response = geminiApiService.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )
                if (!response.isSuccessful || response.body() == null) {
                    errors += "$modelName: HTTP ${response.code()} - ${response.errorBody()?.string().orEmpty()}"
                    continue
                }

                val candidate = response.body()!!.candidates?.firstOrNull()
                val text = candidate?.content?.parts
                    ?.filter { it.thought != true }
                    ?.mapNotNull { it.text }
                    ?.joinToString("")
                    ?.trim()

                if (!text.isNullOrBlank()) {
                    return@withContext ResultWrapper.Success(text)
                }
                errors += "$modelName: respuesta vacia (${candidate?.finishReason ?: "UNKNOWN"})"
            } catch (e: Exception) {
                errors += "$modelName: ${e.message ?: "error desconocido"}"
            }
        }

        ResultWrapper.Error(errors.lastOrNull() ?: "No se pudo transcribir el audio.")
    }

    private fun buildModelPriority(preferredModel: String): List<String> {
        val normalized = preferredModel.ifBlank { GeminiModelConfig.DEFAULT_MODEL }
        return (listOf(normalized) + GeminiModelConfig.fallbackOrder).distinct()
    }
}
