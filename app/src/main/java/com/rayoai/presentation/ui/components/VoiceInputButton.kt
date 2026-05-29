package com.rayoai.presentation.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.rayoai.domain.model.AudioRecording
import com.rayoai.presentation.voice.VoiceRecorder
import kotlinx.coroutines.launch

@Composable
fun VoiceInputButton(
    enabled: Boolean,
    isTranscribingAudio: Boolean,
    onRecordingReady: (AudioRecording) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                recorder.start()
                isRecording = true
                vibrate(context)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo iniciar la grabacion.")
            }
        } else {
            onError("Permiso de microfono denegado.")
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    val label = when {
        isRecording -> "Parar grabación"
        isTranscribingAudio -> "Transcribiendo audio"
        else -> "Grabar audio"
    }

    IconButton(
        onClick = {
            if (isRecording) {
                scope.launch {
                    try {
                        val recording = recorder.stop()
                        isRecording = false
                        vibrate(context)
                        onRecordingReady(recording)
                    } catch (e: Exception) {
                        isRecording = false
                        onError(e.message ?: "No se pudo detener la grabacion.")
                    }
                }
            } else {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    try {
                        recorder.start()
                        isRecording = true
                        vibrate(context)
                    } catch (e: Exception) {
                        onError(e.message ?: "No se pudo iniciar la grabacion.")
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        enabled = enabled && !isTranscribingAudio,
        modifier = modifier.semantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Assertive
        }
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = null
        )
    }
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(80)
    }
}
