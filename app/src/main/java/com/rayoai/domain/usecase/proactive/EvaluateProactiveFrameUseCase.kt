package com.rayoai.domain.usecase.proactive

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.proactive.ProactiveDetection
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import javax.inject.Inject

class EvaluateProactiveFrameUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    operator fun invoke(
        apiKey: String,
        image: Bitmap,
        condition: String,
        languageCode: String,
        model: String
    ): Flow<ResultWrapper<ProactiveDetection>> = flow {
        emit(ResultWrapper.Loading)

        val systemPrompt = """
            You are RayoAI Proactive Sentinel, an assistive vision system for a blind or visually impaired person.
            The user requested proactive surveillance for this specific condition or target:
            "$condition"

            Examine this camera frame closely.
            Determine whether the requested condition or target is detected/visible right now.
            - If the target is an object the user is looking for or left behind (keys, phone, wallet, cane, cup, etc.), locate it clearly relative to the user (e.g. 'Llaves sobre la mesa a la derecha', 'Taza al frente').
            - If the condition is met, set "detected": true and provide a concise announcement of 3 to 8 words in $languageCode optimized for natural speech synthesis (TextToSpeech).
            - If the condition is NOT met or the target is not visible, set "detected": false and "announcement": null.
            - "stateChanged": true if this represents a new detection, state change, or notable change in position/lighting.

            Respond strictly in valid JSON with this exact schema:
            {
              "detected": true or false,
              "stateChanged": true or false,
              "announcement": "Short concise phrase of 3 to 8 words in $languageCode for immediate speech announcement, or null if not detected",
              "details": "One short sentence describing what is visible",
              "confidence": "high", "medium", or "low"
            }
            Do not output markdown code blocks or text outside JSON.
        """.trimIndent()

        val prompt = "Condition to evaluate: $condition. Return JSON analysis."

        visionRepository.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemPrompt = systemPrompt,
            images = listOf(image),
            history = emptyList(),
            model = model,
            responseMimeType = "application/json"
        ).collect { result ->
            when (result) {
                is ResultWrapper.Loading -> Unit
                is ResultWrapper.Error -> emit(ResultWrapper.Error(result.message))
                is ResultWrapper.Success -> {
                    try {
                        val raw: String = result.data.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                        val json = JSONObject(raw)
                        val detected = json.optBoolean("detected", false)
                        val stateChanged = json.optBoolean("stateChanged", true)
                        val rawAnnouncement = json.optString("announcement")
                        val announcement = if (rawAnnouncement.isEmpty() || rawAnnouncement == "null") null else rawAnnouncement
                        val rawDetails = json.optString("details")
                        val details = if (rawDetails.isEmpty() || rawDetails == "null") null else rawDetails
                        val confidence = json.optString("confidence", "medium")

                        emit(
                            ResultWrapper.Success(
                                ProactiveDetection(
                                    detected = detected,
                                    stateChanged = stateChanged,
                                    announcement = announcement,
                                    details = details,
                                    confidence = confidence
                                )
                            )
                        )
                    } catch (e: Exception) {
                        emit(ResultWrapper.Error(e.message ?: "Error al procesar JSON"))
                    }
                }
            }
        }
    }
}
