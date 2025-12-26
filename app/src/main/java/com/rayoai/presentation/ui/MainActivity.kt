package com.rayoai.presentation.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
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

    /**
     * Helper function para obtener Parcelable de manera compatible con todas las versiones de Android.
     * En Android 13+ (API 33), getParcelableExtra(String) está deprecado y puede causar crashes.
     */
    private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting parcelable extra: $key", e)
            null
        }
    }

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

        // Obtener URIs de manera segura usando la función helper compatible
        val isPdf = intent?.getBooleanExtra("EXTRA_IS_PDF", false) == true
        val isVideo = intent?.getBooleanExtra("EXTRA_IS_VIDEO", false) == true
        
        val imageUri: Uri? = if (isPdf || isVideo) null 
            else intent?.getParcelableExtraCompat(Intent.EXTRA_STREAM)
        
        val pdfUri: Uri? = if (isPdf) 
            intent?.getParcelableExtraCompat(Intent.EXTRA_STREAM) else null
        
        val videoUri: Uri? = if (isVideo) 
            intent?.getParcelableExtraCompat(Intent.EXTRA_STREAM) else null

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
                        AppNavigation(
                            imageUri = imageUri, 
                            startDestination = startDestination, 
                            pdfUri = pdfUri,
                            videoUri = videoUri
                        )
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

