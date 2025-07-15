package com.rayoai.presentation.ui

import android.content.Intent
import android.net.Uri
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
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.presentation.ui.navigation.AppNavigation
import com.rayoai.presentation.ui.navigation.Screen
import com.rayoai.presentation.ui.theme.RayoAITheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("es", "ES"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "El idioma español no está soportado o faltan datos.")
                }
            } else {
                Log.e("TTS", "Fallo en la inicialización de TTS.")
            }
        }

        val imageUri: Uri? = intent?.getParcelableExtra(Intent.EXTRA_STREAM)

        setContent {
            val tts = remember { textToSpeech }
            CompositionLocalProvider(LocalTextToSpeech provides tts) {
                RayoAITheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val isFirstRun = runBlocking { userPreferencesRepository.isFirstRun.first() }
                        val apiKey = runBlocking { userPreferencesRepository.apiKey.first() }
                        val startDestination = if (isFirstRun || apiKey.isNullOrEmpty()) {
                            Screen.Welcome.route
                        } else {
                            Screen.Home.route
                        }
                        AppNavigation(imageUri = imageUri, startDestination = startDestination)
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                }
            }
        }
    }
}