package com.rayoai.presentation.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rayoai.presentation.ui.navigation.AppNavigation
import com.rayoai.presentation.ui.theme.RayoAITheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Actividad principal de la aplicación RayoAI.
 * Configura el tema, la navegación y el motor de Text-to-Speech (TTS).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el motor de Text-to-Speech.
        textToSpeech = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("es", "ES")) // Configurar idioma a español.
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "El idioma español no está soportado o faltan datos.")
                }
            } else {
                Log.e("TTS", "Fallo en la inicialización de TTS.")
            }
        }

        setContent {
            // Proporcionar el TextToSpeech a través de un CompositionLocal para que sea accesible en otros Composables.
            val tts = remember { textToSpeech }
            CompositionLocalProvider(LocalTextToSpeech provides tts) {
                RayoAITheme {
                    // Superficie principal de la aplicación con el color de fondo del tema.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }

            // Limpiar el motor de TTS cuando el Composable se destruye.
            DisposableEffect(Unit) {
                onDispose {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                }
            }
        }
    }
}
