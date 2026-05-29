package com.rayoai.presentation.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.rayoai.domain.model.AudioRecording
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class AndroidSpeechFileTranscriber(
    private val context: Context
) {
    suspend fun transcribe(recording: AudioRecording): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Result.failure(IllegalStateException("El fallback de voz requiere Android 13 o superior."))
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return Result.failure(IllegalStateException("No hay servicio de reconocimiento de voz disponible."))
        }

        return suspendCancellableCoroutine { continuation ->
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val pfd = ParcelFileDescriptor.open(recording.rawPcmFile, ParcelFileDescriptor.MODE_READ_ONLY)

            fun closeResources() {
                try {
                    pfd.close()
                } catch (_: Exception) {
                }
                speechRecognizer.destroy()
            }

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException("Reconocimiento de voz fallido: $error")))
                    }
                    closeResources()
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (continuation.isActive) {
                        if (text.isNullOrBlank()) {
                            continuation.resume(Result.failure(IllegalStateException("El reconocimiento de voz no devolvio texto.")))
                        } else {
                            continuation.resume(Result.success(text))
                        }
                    }
                    closeResources()
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, recording.channelCount)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, recording.sampleRate)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }

            continuation.invokeOnCancellation {
                try {
                    speechRecognizer.cancel()
                } catch (_: Exception) {
                }
                closeResources()
            }
            speechRecognizer.startListening(intent)
        }
    }
}
