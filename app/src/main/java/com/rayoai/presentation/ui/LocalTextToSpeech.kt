package com.rayoai.presentation.ui

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * [CompositionLocal] para proporcionar una instancia de [TextToSpeech] a través del árbol de Composable.
 * Permite que cualquier Composable hijo acceda al motor de TTS sin pasarlo explícitamente como parámetro.
 */
val LocalTextToSpeech = staticCompositionLocalOf<TextToSpeech?> { null }
