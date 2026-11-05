package com.rayoai.presentation.ui.screens.tools.proactive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.rayoai.R
import com.rayoai.domain.model.AudioRecording
import com.rayoai.domain.model.proactive.ProactivePreset
import com.rayoai.presentation.ui.LocalTextToSpeech
import com.rayoai.presentation.voice.VoiceRecorder
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ProactiveModeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ProactiveModeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textToSpeech = LocalTextToSpeech.current

    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isTranscribingAudio by viewModel.isTranscribingAudio.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val customCondition by viewModel.customCondition.collectAsState()
    val intervalSeconds by viewModel.intervalSeconds.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val latestDetection by viewModel.latestDetection.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Textos accesibles resueltos en alcance Composable
    val screenTitle = stringResource(R.string.proactive_mode_title)
    val backButtonDesc = stringResource(R.string.back)
    val startMonitoringText = stringResource(R.string.proactive_mode_start)
    val stopMonitoringText = stringResource(R.string.proactive_mode_stop)
    val monitoringActiveText = stringResource(R.string.proactive_mode_monitoring_active)
    val monitoringIdleText = stringResource(R.string.proactive_mode_monitoring_idle)
    val customHintText = stringResource(R.string.proactive_custom_hint)
    val silenceHint = stringResource(R.string.proactive_silence_hint)
    val cameraTouchToSilenceDesc = stringResource(R.string.proactive_camera_touch_to_silence)
    val apiKeyRequiredText = stringResource(R.string.proactive_api_key_required)
    val openSettingsText = stringResource(R.string.live_mode_open_settings)
    val flipCameraDesc = stringResource(R.string.live_mode_flip_camera)
    val intervalLabel = stringResource(R.string.proactive_interval_label)
    val changeTargetBtnText = stringResource(R.string.proactive_change_target_btn)
    val dictateTargetBtnText = stringResource(R.string.proactive_dictate_target_btn)
    val detectedPrefixText = stringResource(R.string.proactive_detected_prefix)

    val torchDesc = if (isTorchOn) stringResource(R.string.live_mode_torch_off)
    else stringResource(R.string.live_mode_torch_on)

    val presetCondition = stringResource(selectedPreset.promptConditionRes)

    val currentTargetDisplay = if (selectedPreset == ProactivePreset.CUSTOM && customCondition.isNotBlank()) {
        customCondition
    } else {
        stringResource(selectedPreset.titleRes)
    }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // Manejo de vibración y earcon sonoro discreto
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGen?.release()
            textToSpeech?.stop()
        }
    }

    // Anuncio vocal por Google TTS ÚNICAMENTE cuando se localiza el objeto
    LaunchedEffect(viewModel.alertTrigger) {
        viewModel.alertTrigger.collect { announcement ->
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            } catch (_: Exception) {}
            textToSpeech?.stop()
            textToSpeech?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "proactive_alert")
        }
    }

    // Confirmación breve por Google TTS al fijar/cambiar el objetivo en lenguaje natural
    LaunchedEffect(viewModel.speechFeedbackTrigger) {
        viewModel.speechFeedbackTrigger.collect { feedback ->
            textToSpeech?.stop()
            textToSpeech?.speak(feedback, TextToSpeech.QUEUE_FLUSH, null, "proactive_target_confirm")
        }
    }

    // Vibración háptica al localizar
    LaunchedEffect(viewModel.hapticTrigger) {
        viewModel.hapticTrigger.collect {
            vibrateDetection(vibrator)
        }
    }

    BackHandler {
        if (isMonitoring) {
            viewModel.stopMonitoring()
        }
        textToSpeech?.stop()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isMonitoring) viewModel.stopMonitoring()
                            textToSpeech?.stop()
                            onNavigateBack()
                        },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = backButtonDesc
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // Linterna (cámara trasera)
                    if (!isFrontCamera) {
                        IconButton(
                            onClick = { viewModel.toggleTorch() },
                            modifier = Modifier.semantics {
                                role = Role.Button
                                contentDescription = torchDesc
                            }
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = null,
                                tint = if (isTorchOn) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Cambiar Cámara
                    IconButton(
                        onClick = { viewModel.toggleFrontCamera() },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = flipCameraDesc
                        }
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = null)
                    }

                    // Frecuencia (1s, 2s, 3s, 5s)
                    val intervalText = "$intervalLabel: ${intervalSeconds}s"
                    TextButton(
                        onClick = { viewModel.cycleInterval() },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = intervalText
                        }
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${intervalSeconds}s", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!permissionsState.allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.live_mode_camera_permission_required),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text(stringResource(R.string.live_mode_grant_permissions))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Escenario de Cámara Central (Tocar silencia cualquier anuncio en curso)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .clickable {
                            textToSpeech?.stop()
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = cameraTouchToSilenceDesc
                        }
                ) {
                    ProactiveCameraPreview(
                        isFrontCamera = isFrontCamera,
                        isTorchOn = isTorchOn,
                        isMonitoring = isMonitoring,
                        isAnalyzing = isAnalyzing,
                        intervalSeconds = intervalSeconds,
                        onFrameToAnalyze = { bitmap ->
                            viewModel.evaluateFrame(bitmap, presetCondition)
                        }
                    )

                    // Pastilla superior con el estado y objetivo actual
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when {
                                        !isMonitoring -> Color(0xFFB0BEC5)
                                        latestDetection?.detected == true -> Color(0xFF00E676)
                                        else -> Color(0xFFFFB300)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isMonitoring) {
                                "$monitoringActiveText • $currentTargetDisplay"
                            } else {
                                monitoringIdleText
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Panel Inferior: Información Limpia y Controles Robustos
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tarjeta de Estado/Localización (SIN liveRegion para eliminar el spam en TalkBack)
                        val detection = latestDetection
                        val isDetected = detection?.detected == true

                        val cardBg = if (isDetected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        val cardContentColor = if (isDetected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    textToSpeech?.stop()
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = if (isDetected) {
                                        "$detectedPrefixText: ${detection?.announcement ?: ""}. $silenceHint"
                                    } else {
                                        "$currentTargetDisplay. $silenceHint"
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isDetected) Icons.Filled.CheckCircle else Icons.Filled.Visibility,
                                            contentDescription = null,
                                            tint = cardContentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isDetected) detectedPrefixText else currentTargetDisplay,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = cardContentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isAnalyzing && isMonitoring) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = cardContentColor
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = if (isDetected) {
                                        detection?.announcement ?: ""
                                    } else if (isMonitoring) {
                                        stringResource(R.string.proactive_searching_label, currentTargetDisplay)
                                    } else {
                                        monitoringIdleText
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cardContentColor,
                                    fontWeight = if (isDetected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Presets para selección rápida cuando no está en ejecución
                        if (!isMonitoring) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProactivePreset.entries.forEach { preset ->
                                    val isSelected = selectedPreset == preset
                                    val title = stringResource(preset.titleRes)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setPreset(preset) },
                                        label = { Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            // Campo de texto personalizado si el preset es CUSTOM
                            AnimatedVisibility(visible = selectedPreset == ProactivePreset.CUSTOM) {
                                OutlinedTextField(
                                    value = customCondition,
                                    onValueChange = { viewModel.setCustomCondition(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(customHintText) },
                                    singleLine = true
                                )
                            }
                        }

                        // Barra de Acciones: Cambiar objetivo al vuelo por voz y Iniciar / Detener
                        if (isMonitoring) {
                            // MODO ACTIVO: Dos botones grandes (Micrófono para cambiar sobre la marcha y Detener)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProactiveVoiceActionButton(
                                    isTranscribing = isTranscribingAudio,
                                    idleText = changeTargetBtnText,
                                    onRecordingReady = { recording ->
                                        viewModel.transcribeVoiceAndSetCustomCondition(
                                            context = context,
                                            recording = recording
                                        )
                                    },
                                    modifier = Modifier.weight(1.2f)
                                )

                                Button(
                                    onClick = {
                                        textToSpeech?.stop()
                                        viewModel.stopMonitoring()
                                    },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .height(56.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = stopMonitoringText
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stopMonitoringText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // MODO EN ESPERA: Botón Gigante Iniciar + Botón Dictar Objetivo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        textToSpeech?.stop()
                                        viewModel.startMonitoring(
                                            onMissingApiKey = { showApiKeyDialog = true }
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(56.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = startMonitoringText
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(startMonitoringText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }

                                ProactiveVoiceActionButton(
                                    isTranscribing = isTranscribingAudio,
                                    idleText = dictateTargetBtnText,
                                    onRecordingReady = { recording ->
                                        viewModel.transcribeVoiceAndSetCustomCondition(
                                            context = context,
                                            recording = recording
                                        )
                                    },
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo Clave API ausente
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(screenTitle) },
            text = { Text(apiKeyRequiredText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApiKeyDialog = false
                        onNavigateToSettings()
                    }
                ) {
                    Text(openSettingsText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Diálogo Error
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.unexpected_error)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

/**
 * Botón accesible de dictado por voz en lenguaje natural, utilizable tanto antes como durante la vigilancia.
 */
@Composable
private fun ProactiveVoiceActionButton(
    isTranscribing: Boolean,
    idleText: String,
    onRecordingReady: (AudioRecording) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val textToSpeech = LocalTextToSpeech.current
    var isRecording by remember { mutableStateOf(false) }

    val startRecordingLabel = stringResource(R.string.voice_record_start)
    val stopRecordingLabel = stringResource(R.string.voice_record_stop)
    val transcribingLabel = stringResource(R.string.voice_record_transcribing)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                textToSpeech?.stop()
                recorder.start()
                isRecording = true
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    val buttonText = when {
        isRecording -> stopRecordingLabel
        isTranscribing -> transcribingLabel
        else -> idleText
    }

    val buttonColor = if (isRecording) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = if (isRecording) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Button(
        onClick = {
            if (isRecording) {
                scope.launch {
                    try {
                        val recording = recorder.stop()
                        isRecording = false
                        onRecordingReady(recording)
                    } catch (_: Exception) {
                        isRecording = false
                    }
                }
            } else {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    try {
                        textToSpeech?.stop()
                        recorder.start()
                        isRecording = true
                    } catch (_: Exception) {}
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        enabled = !isTranscribing,
        modifier = modifier
            .height(56.dp)
            .semantics {
                contentDescription = buttonText
                role = Role.Button
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        if (isTranscribing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ProactiveCameraPreview(
    isFrontCamera: Boolean,
    isTorchOn: Boolean,
    isMonitoring: Boolean,
    isAnalyzing: Boolean,
    intervalSeconds: Int,
    onFrameToAnalyze: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    var lastFrameTimeMs by remember { mutableLongStateOf(0L) }
    val intervalMs = intervalSeconds * 1000L
    val canAnalyze = isMonitoring && !isAnalyzing

    LaunchedEffect(isFrontCamera) {
        cameraController.cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    LaunchedEffect(isTorchOn, isFrontCamera) {
        if (!isFrontCamera) {
            cameraController.enableTorch(isTorchOn)
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, canAnalyze, intervalMs) {
        val executor = Executors.newSingleThreadExecutor()

        cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (canAnalyze && (now - lastFrameTimeMs >= intervalMs)) {
                lastFrameTimeMs = now
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    onFrameToAnalyze(bitmap)
                }
            }
            imageProxy.close()
        }
        cameraController.bindToLifecycle(lifecycleOwner)

        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Convierte y redimensiona eficientemente el fotograma de CameraX para inferencia en tiempo real.
 * Aplica rotación y escalado a 768px en una sola transformación para reducir payload y latencia.
 */
private fun imageProxyToBitmap(image: ImageProxy, maxDim: Int = 768): Bitmap? {
    val original = try {
        image.toBitmap()
    } catch (_: Exception) {
        return null
    }

    val rotation = image.imageInfo.rotationDegrees
    val matrix = Matrix()
    if (rotation != 0) {
        matrix.postRotate(rotation.toFloat())
    }

    val largestSide = maxOf(original.width, original.height)
    if (largestSide > maxDim) {
        val scale = maxDim.toFloat() / largestSide
        matrix.postScale(scale, scale)
    }

    val processed = Bitmap.createBitmap(
        original,
        0,
        0,
        original.width,
        original.height,
        matrix,
        true
    )

    if (processed != original) {
        original.recycle()
    }
    return processed
}

private fun vibrateDetection(vibrator: Vibrator?) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 160, 100, 200), -1)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 160, 100, 200), -1)
        }
    } catch (_: Exception) {}
}
