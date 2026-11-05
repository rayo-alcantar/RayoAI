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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
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
import com.rayoai.domain.model.proactive.ProactivePresetModel
import com.rayoai.domain.model.proactive.StandardProactiveTarget
import com.rayoai.presentation.ui.LocalTextToSpeech
import com.rayoai.presentation.voice.VoiceRecorder
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ProactiveModeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ProactiveModeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val textToSpeech = LocalTextToSpeech.current

    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isTranscribingAudio by viewModel.isTranscribingAudio.collectAsState()
    val selectedQuickTarget by viewModel.selectedQuickTarget.collectAsState()
    val customCondition by viewModel.customCondition.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val temporaryVoiceCondition by viewModel.temporaryVoiceCondition.collectAsState()
    val intervalSeconds by viewModel.intervalSeconds.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val latestDetection by viewModel.latestDetection.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showPresetsManagerDialog by remember { mutableStateOf(false) }
    var showPresetEditorDialog by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<ProactivePresetModel?>(null) }
    var isCreatingNewPreset by remember { mutableStateOf(false) }

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
    val presetsManageBtnDesc = stringResource(R.string.proactive_preset_open_manager_desc)
    val presetsManageLabel = stringResource(R.string.proactive_preset_open_manager)

    val torchDesc = if (isTorchOn) stringResource(R.string.live_mode_torch_off)
    else stringResource(R.string.live_mode_torch_on)

    val quickTargetPromptCondition = stringResource(selectedQuickTarget.promptConditionRes)

    // Título y resumen del objetivo actual
    val currentTargetDisplay = when {
        !temporaryVoiceCondition.isNullOrBlank() -> temporaryVoiceCondition!!
        activePreset != null -> activePreset!!.getDisplayName(context)
        selectedQuickTarget == ProactivePreset.CUSTOM && customCondition.isNotBlank() -> customCondition
        else -> stringResource(selectedQuickTarget.titleRes)
    }

    val searchingTargetsSummary = when {
        !temporaryVoiceCondition.isNullOrBlank() -> temporaryVoiceCondition!!
        activePreset != null -> {
            val names = activePreset!!.getActiveTargetNames(context)
            if (names.isNotEmpty()) names.joinToString(", ") else stringResource(R.string.proactive_target_obstacles)
        }
        selectedQuickTarget == ProactivePreset.CUSTOM && customCondition.isNotBlank() -> customCondition
        else -> stringResource(selectedQuickTarget.titleRes)
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
                    // Botón para abrir la ventana de Presets
                    IconButton(
                        onClick = { showPresetsManagerDialog = true },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = presetsManageBtnDesc
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmarks,
                            contentDescription = null,
                            tint = if (activePreset != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

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
                            viewModel.evaluateFrame(bitmap, quickTargetPromptCondition)
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

                // Panel Inferior: Modo Directo Predeterminado y Entrada a Presets
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                        stringResource(R.string.proactive_searching_label, searchingTargetsSummary)
                                    } else {
                                        monitoringIdleText
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cardContentColor,
                                    fontWeight = if (isDetected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Configuración del objetivo cuando no está vigilando
                        if (!isMonitoring) {
                            val presetLoaded = activePreset
                            if (presetLoaded != null) {
                                // MODO PRESET ACTIVO: Banner claro con opción para volver al modo simple o cambiar preset
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Bookmarks,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.proactive_preset_active_badge, presetLoaded.getDisplayName(context)),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        val targetNames = presetLoaded.getActiveTargetNames(context)
                                        Text(
                                            text = if (targetNames.isNotEmpty()) targetNames.joinToString(", ")
                                            else stringResource(R.string.proactive_target_obstacles),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            maxLines = 2
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.unloadPreset() },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.proactive_preset_unload_btn), style = MaterialTheme.typography.labelMedium)
                                            }

                                            Button(
                                                onClick = { showPresetsManagerDialog = true },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Text(stringResource(R.string.proactive_presets_manage_btn), style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // MODO PREDETERMINADO: Búsqueda directa (categorías rápidas + escribir + botón a presets)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Chips de categorías simples estándar
                                    ProactivePreset.entries.forEach { target ->
                                        val isSelected = selectedQuickTarget == target
                                        val title = stringResource(target.titleRes)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setQuickTarget(target) },
                                            label = { Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }

                                    // Botón para acceder a la ventana de Presets
                                    FilterChip(
                                        selected = false,
                                        onClick = { showPresetsManagerDialog = true },
                                        label = { Text(presetsManageLabel, fontWeight = FontWeight.Bold) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Bookmarks,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }

                                // Campo de texto si seleccionó Personalizado
                                AnimatedVisibility(visible = selectedQuickTarget == ProactivePreset.CUSTOM) {
                                    OutlinedTextField(
                                        value = customCondition,
                                        onValueChange = { viewModel.setCustomCondition(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(customHintText) },
                                        singleLine = true
                                    )
                                }
                            }

                            // Aviso de objetivo dictado por voz si existe
                            if (!temporaryVoiceCondition.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Voz: $temporaryVoiceCondition",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearTemporaryVoiceCondition() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.proactive_clear_voice_target),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
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

    // Ventana / Diálogo independiente para Gestionar Presets
    if (showPresetsManagerDialog) {
        ProactivePresetsManageDialog(
            presets = presets,
            activePresetId = activePreset?.id,
            onSelectPreset = { preset ->
                viewModel.loadPreset(preset)
                showPresetsManagerDialog = false
            },
            onEditPreset = { preset ->
                presetToEdit = preset
                isCreatingNewPreset = false
                showPresetEditorDialog = true
            },
            onCreateNewPreset = {
                presetToEdit = null
                isCreatingNewPreset = true
                showPresetEditorDialog = true
            },
            onDismiss = { showPresetsManagerDialog = false }
        )
    }

    // Ventana / Diálogo para Crear o Editar un Preset
    if (showPresetEditorDialog) {
        ProactivePresetEditorDialog(
            preset = presetToEdit,
            isCreatingNew = isCreatingNewPreset,
            onSavePreset = { updatedPreset ->
                viewModel.savePreset(updatedPreset)
                showPresetEditorDialog = false
                presetToEdit = null
            },
            onDeletePreset = if (presetToEdit != null && !presetToEdit!!.isDefault) {
                { presetId ->
                    viewModel.deletePreset(presetId)
                    showPresetEditorDialog = false
                    presetToEdit = null
                }
            } else null,
            onDismiss = {
                showPresetEditorDialog = false
                presetToEdit = null
            }
        )
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
 * Ventana independiente para gestionar, ver, activar y editar los Presets de vigilancia.
 */
@Composable
private fun ProactivePresetsManageDialog(
    presets: List<ProactivePresetModel>,
    activePresetId: String?,
    onSelectPreset: (ProactivePresetModel) -> Unit,
    onEditPreset: (ProactivePresetModel) -> Unit,
    onCreateNewPreset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.proactive_presets_manage_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.proactive_presets_manage_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presets.forEach { preset ->
                    val isCurrent = preset.id == activePresetId
                    val displayName = preset.getDisplayName(context)
                    val targetNames = preset.getActiveTargetNames(context)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )

                                if (isCurrent) {
                                    Text(
                                        text = stringResource(R.string.proactive_mode_monitoring_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = if (targetNames.isNotEmpty()) targetNames.joinToString(", ")
                                else stringResource(R.string.proactive_target_obstacles),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onSelectPreset(preset) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.proactive_preset_activate_btn), style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = { onEditPreset(preset) },
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.proactive_edit_preset_btn), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Botón grande para crear un nuevo preset
                Button(
                    onClick = onCreateNewPreset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.proactive_new_preset_btn),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

/**
 * Diálogo accesible para crear o editar presets del Modo Centinela con casillas y objetos propios.
 */
@Composable
private fun ProactivePresetEditorDialog(
    preset: ProactivePresetModel?,
    isCreatingNew: Boolean,
    onSavePreset: (ProactivePresetModel) -> Unit,
    onDeletePreset: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(preset) {
        mutableStateOf(preset?.name ?: "")
    }
    var selectedStandardKeys by remember(preset) {
        mutableStateOf(
            preset?.standardTargetKeys ?: setOf(
                StandardProactiveTarget.VEHICLES.key,
                StandardProactiveTarget.TRAFFIC_LIGHTS.key,
                StandardProactiveTarget.PEOPLE.key
            )
        )
    }
    var customTargets by remember(preset) {
        mutableStateOf(preset?.customTargets ?: emptyList())
    }
    var newCustomInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val dialogTitle = if (isCreatingNew || preset == null) {
        stringResource(R.string.proactive_dialog_new_title)
    } else {
        stringResource(R.string.proactive_dialog_edit_title, preset.getDisplayName(context))
    }

    val emptyNameError = stringResource(R.string.proactive_preset_empty_name_error)
    val emptyTargetsError = stringResource(R.string.proactive_preset_empty_targets_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Campo de nombre del preset
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.proactive_preset_name_label)) },
                    placeholder = { Text(stringResource(R.string.proactive_preset_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Sección de Casillas Estándar
                Text(
                    text = stringResource(R.string.proactive_preset_standard_targets_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                StandardProactiveTarget.entries.forEach { target ->
                    val isChecked = selectedStandardKeys.contains(target.key)
                    val title = stringResource(target.titleRes)
                    val desc = stringResource(target.descriptionRes)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = isChecked,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    selectedStandardKeys = if (checked) {
                                        selectedStandardKeys + target.key
                                    } else {
                                        selectedStandardKeys - target.key
                                    }
                                    validationError = null
                                }
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null // Manejado por toggleable del Row
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Sección de Objetos Adicionales escritos por el usuario
                Text(
                    text = stringResource(R.string.proactive_preset_custom_targets_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCustomInput,
                        onValueChange = { newCustomInput = it },
                        placeholder = { Text(stringResource(R.string.proactive_preset_custom_target_placeholder)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = newCustomInput.trim()
                            if (trimmed.isNotBlank() && !customTargets.contains(trimmed)) {
                                customTargets = customTargets + trimmed
                                newCustomInput = ""
                                validationError = null
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.proactive_preset_add_target_btn))
                    }
                }

                // Lista de chips de objetos adicionales agregados
                if (customTargets.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customTargets.forEach { item ->
                            val removeDesc = stringResource(R.string.proactive_remove_custom_target, item)
                            InputChip(
                                selected = false,
                                onClick = { customTargets = customTargets - item },
                                label = { Text(item) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = removeDesc,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        validationError = emptyNameError
                        return@Button
                    }
                    if (selectedStandardKeys.isEmpty() && customTargets.isEmpty()) {
                        validationError = emptyTargetsError
                        return@Button
                    }

                    val idToSave = if (isCreatingNew || preset == null) {
                        "preset_" + UUID.randomUUID().toString().take(8)
                    } else {
                        preset.id
                    }

                    val isDefault = preset?.isDefault ?: false
                    val defaultNameRes = if (!isCreatingNew) preset?.defaultNameRes else null

                    val updatedPreset = ProactivePresetModel(
                        id = idToSave,
                        name = name.trim(),
                        defaultNameRes = defaultNameRes,
                        isDefault = isDefault,
                        standardTargetKeys = selectedStandardKeys,
                        customTargets = customTargets
                    )
                    onSavePreset(updatedPreset)
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.proactive_preset_save_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDeletePreset != null && preset != null && !preset.isDefault) {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.proactive_preset_delete_btn))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )

    // Confirmación para eliminar preset
    if (showDeleteConfirmDialog && preset != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.proactive_preset_delete_confirm_title)) },
            text = { Text(stringResource(R.string.proactive_preset_delete_confirm_desc, preset.getDisplayName(context))) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePreset?.invoke(preset.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.proactive_preset_delete_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
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
