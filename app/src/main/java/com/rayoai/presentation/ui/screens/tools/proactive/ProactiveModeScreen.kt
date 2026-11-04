package com.rayoai.presentation.ui.screens.tools.proactive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
import com.rayoai.domain.model.proactive.ProactivePreset
import com.rayoai.presentation.ui.LocalTextToSpeech
import com.rayoai.presentation.ui.components.VoiceInputButton
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
    val analyzingText = stringResource(R.string.proactive_mode_analyzing)
    val detectedBadgeText = stringResource(R.string.proactive_mode_detected)
    val customHintText = stringResource(R.string.proactive_custom_hint)
    val silenceHint = stringResource(R.string.proactive_silence_hint)
    val apiKeyRequiredText = stringResource(R.string.proactive_api_key_required)
    val openSettingsText = stringResource(R.string.live_mode_open_settings)
    val flipCameraDesc = stringResource(R.string.live_mode_flip_camera)
    val intervalLabel = stringResource(R.string.proactive_interval_label)

    val torchDesc = if (isTorchOn) stringResource(R.string.live_mode_torch_off)
    else stringResource(R.string.live_mode_torch_on)

    val presetCondition = stringResource(selectedPreset.promptConditionRes)

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // Manejo de vibración y earcon sonoro
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
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

    LaunchedEffect(viewModel.alertTrigger) {
        viewModel.alertTrigger.collect { announcement ->
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            } catch (_: Exception) {}
            textToSpeech?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "proactive_alert")
        }
    }

    LaunchedEffect(viewModel.hapticTrigger) {
        viewModel.hapticTrigger.collect {
            vibrateDetection(context, vibrator)
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
                    // Botón Linterna (solo cámara trasera)
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

                    // Botón Cambiar Cámara
                    IconButton(
                        onClick = { viewModel.toggleFrontCamera() },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = flipCameraDesc
                        }
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = null)
                    }

                    // Botón Selector de Intervalo (Ciclo rápido)
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
                // Escenario de Cámara Central con Análisis Periódico
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    ProactiveCameraPreview(
                        isFrontCamera = isFrontCamera,
                        isTorchOn = isTorchOn,
                        isMonitoring = isMonitoring,
                        intervalSeconds = intervalSeconds,
                        onFrameToAnalyze = { bitmap ->
                            viewModel.evaluateFrame(bitmap, presetCondition)
                        }
                    )

                    // Indicador flotante de estado en vivo sobre la cámara
                    if (isMonitoring) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (latestDetection?.detected == true) Color(0xFF00E676) else Color(0xFFFFB300),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isAnalyzing) analyzingText else "$monitoringActiveText (${intervalSeconds}s)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Panel de Control Inferior: Presets, Alerta y Botón de Inicio
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
                        // Fila de Chips de Objetivos Proactivos
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

                        // Campo personalizado si el preset es CUSTOM
                        AnimatedVisibility(visible = selectedPreset == ProactivePreset.CUSTOM) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customCondition,
                                    onValueChange = { viewModel.setCustomCondition(it) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(customHintText) },
                                    singleLine = true
                                )
                                Spacer(Modifier.width(8.dp))
                                VoiceInputButton(
                                    enabled = true,
                                    isTranscribingAudio = false,
                                    onRecordingReady = { recording ->
                                        // Texto de voz integrado
                                    },
                                    onError = {}
                                )
                            }
                        }

                        // Tarjeta de Alerta y Detección Accesible (con toque para silenciar anuncio)
                        val detection = latestDetection
                        val isDetected = detection?.detected == true
                        val cardBg = if (isDetected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val cardContentColor = if (isDetected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                        val statusCardText = when {
                            !isMonitoring -> monitoringIdleText
                            isDetected && !detection?.announcement.isNullOrBlank() -> "${detection?.announcement}. ${detection?.details ?: ""}"
                            isAnalyzing -> analyzingText
                            else -> stringResource(selectedPreset.titleRes) + ": " + stringResource(R.string.proactive_mode_no_detection)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    textToSpeech?.stop()
                                }
                                .semantics {
                                    liveRegion = LiveRegionMode.Assertive
                                    role = Role.Button
                                    contentDescription = "$statusCardText. $silenceHint"
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
                                            imageVector = Icons.Filled.Visibility,
                                            contentDescription = null,
                                            tint = cardContentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isDetected) detectedBadgeText else stringResource(selectedPreset.titleRes),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = cardContentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isAnalyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = cardContentColor
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = statusCardText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cardContentColor,
                                    fontWeight = if (isDetected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Botón Gigante Principal de Iniciar / Detener Vigilancia
                        val actionButtonText = if (isMonitoring) stopMonitoringText else startMonitoringText
                        Button(
                            onClick = {
                                if (isMonitoring) {
                                    textToSpeech?.stop()
                                }
                                viewModel.toggleMonitoring(
                                    onMissingApiKey = { showApiKeyDialog = true }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = actionButtonText
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isMonitoring) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = actionButtonText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
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

@SuppressLint("MissingPermission")
@Composable
private fun ProactiveCameraPreview(
    isFrontCamera: Boolean,
    isTorchOn: Boolean,
    isMonitoring: Boolean,
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

    DisposableEffect(cameraController, lifecycleOwner, isMonitoring, intervalMs) {
        val executor = Executors.newSingleThreadExecutor()

        cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (isMonitoring && (now - lastFrameTimeMs >= intervalMs)) {
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

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes.getOrNull(0) ?: return null
    val buffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val bitmap = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
    return bitmap
}

private fun vibrateDetection(context: Context, vibrator: Vibrator?) {
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
