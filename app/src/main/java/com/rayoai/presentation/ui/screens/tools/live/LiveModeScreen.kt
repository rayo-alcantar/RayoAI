@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)

package com.rayoai.presentation.ui.screens.tools.live

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.rayoai.R
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.live.LiveSessionState
import com.rayoai.domain.model.live.VisualSource
import java.util.concurrent.Executors

@Composable
fun LiveModeScreen(
    viewModel: LiveModeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    val sessionState by viewModel.sessionState.collectAsState()
    val visualSource by viewModel.visualSource.collectAsState()
    val isVideoPaused by viewModel.isVideoPaused.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isTalking by viewModel.isTalking.collectAsState()
    val latestTranscript by viewModel.latestTranscript.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val liveModel by viewModel.liveModel.collectAsState()
    val liveVoice by viewModel.liveVoice.collectAsState()
    val liveThinkingLevel by viewModel.liveThinkingLevel.collectAsState()
    val liveVadSensitivity by viewModel.liveVadSensitivity.collectAsState()
    val livePushToTalk by viewModel.livePushToTalk.collectAsState()
    val liveCaptureIntervalSeconds by viewModel.liveCaptureIntervalSeconds.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Textos accesibles resueltos en alcance Composable
    val screenTitle = stringResource(R.string.live_mode_title)
    val stateStopped = stringResource(R.string.live_mode_state_stopped)
    val stateStarting = stringResource(R.string.live_mode_state_starting)
    val stateRunning = stringResource(R.string.live_mode_state_running)
    val stateReconnecting = stringResource(R.string.live_mode_state_reconnecting)
    val statePaused = stringResource(R.string.live_mode_state_paused)

    val currentStatusText = when (sessionState) {
        LiveSessionState.STOPPED -> stateStopped
        LiveSessionState.STARTING -> stateStarting
        LiveSessionState.RUNNING -> if (isVideoPaused) statePaused else stateRunning
        LiveSessionState.RECONNECTING -> stateReconnecting
        LiveSessionState.PAUSED -> statePaused
        LiveSessionState.STOPPING -> stateStopped
    }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    )

    // Gestor de captura de pantalla (MediaProjection)
    val screenCaptureManager = remember(liveCaptureIntervalSeconds) {
        ScreenCaptureManager(
            context = context,
            onFrameCaptured = { bitmap ->
                viewModel.sendBitmapFrame(bitmap)
            },
            intervalMs = (liveCaptureIntervalSeconds * 1000L).toLong().coerceAtLeast(500L)
        )
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            screenCaptureManager.start(result.resultCode, result.data!!)
        } else {
            viewModel.setVisualSource(VisualSource.CAMERA)
        }
    }

    // Gestionar Lifecycle del Foreground Service
    LaunchedEffect(sessionState) {
        if (sessionState == LiveSessionState.RUNNING) {
            LiveSessionForegroundService.start(context)
        } else if (sessionState == LiveSessionState.STOPPED) {
            LiveSessionForegroundService.stop(context)
            screenCaptureManager.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            screenCaptureManager.stop()
        }
    }

    BackHandler {
        if (sessionState != LiveSessionState.STOPPED) {
            viewModel.stopSession()
        }
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (sessionState != LiveSessionState.STOPPED) {
                                viewModel.stopSession()
                            }
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.live_mode_config_title)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!permissionsState.allPermissionsGranted) {
            PermissionsRationaleView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                permissionsState = permissionsState
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Área Visual Central (Cámara, Pantalla o Solo Voz)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    when (visualSource) {
                        VisualSource.CAMERA -> {
                            CameraLivePreview(
                                isFrontCamera = isFrontCamera,
                                isTorchOn = isTorchOn,
                                captureIntervalSeconds = liveCaptureIntervalSeconds,
                                onFrame = { bitmap -> viewModel.sendBitmapFrame(bitmap) }
                            )

                            // Controles flotantes sobre la cámara (Flip / Linterna)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { isTorchOn = !isTorchOn },
                                    modifier = Modifier.background(
                                        Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                ) {
                                    val torchLabel = if (isTorchOn) stringResource(R.string.live_mode_torch_off)
                                    else stringResource(R.string.live_mode_torch_on)
                                    Icon(
                                        imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                        contentDescription = torchLabel,
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { isFrontCamera = !isFrontCamera },
                                    modifier = Modifier.background(
                                        Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Cameraswitch,
                                        contentDescription = stringResource(R.string.live_mode_flip_camera),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                        VisualSource.SCREEN -> {
                            ScreenLiveView()
                        }
                        VisualSource.NONE -> {
                            VoiceOnlyLiveView()
                        }
                    }

                    // Overlay de Estado y Subtítulos para accesibilidad
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(12.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "$currentStatusText. $latestTranscript"
                            }
                    ) {
                        Text(
                            text = currentStatusText,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (sessionState == LiveSessionState.RUNNING) Color(0xFF00E676) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (latestTranscript.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = latestTranscript,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                // Barra de Controles Inferior
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Selector de Fuentes Visuales (Cámara / Pantalla / Solo voz)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val cameraDesc = stringResource(R.string.live_mode_source_camera)
                            FilledTonalButton(
                                onClick = {
                                    screenCaptureManager.stop()
                                    viewModel.setVisualSource(VisualSource.CAMERA)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (visualSource == VisualSource.CAMERA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = cameraDesc
                                }
                            ) {
                                Icon(Icons.Filled.Videocam, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(cameraDesc)
                            }

                            val screenDesc = stringResource(R.string.live_mode_source_screen)
                            FilledTonalButton(
                                onClick = {
                                    viewModel.setVisualSource(VisualSource.SCREEN)
                                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (visualSource == VisualSource.SCREEN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = screenDesc
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(screenDesc)
                            }

                            val voiceDesc = stringResource(R.string.live_mode_source_none)
                            FilledTonalButton(
                                onClick = {
                                    screenCaptureManager.stop()
                                    viewModel.setVisualSource(VisualSource.NONE)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (visualSource == VisualSource.NONE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = voiceDesc
                                }
                            ) {
                                Icon(Icons.Filled.Headphones, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(voiceDesc)
                            }
                        }

                        // Fila de acciones y PTT / Micrófono
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón de Pausa de Video (si la sesión está activa y no es solo voz)
                            if (sessionState == LiveSessionState.RUNNING && visualSource != VisualSource.NONE) {
                                val pauseVideoDesc = if (isVideoPaused) stringResource(R.string.live_mode_resume_video)
                                else stringResource(R.string.live_mode_pause_video)

                                IconButton(
                                    onClick = { viewModel.toggleVideoPause() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isVideoPaused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = pauseVideoDesc
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isVideoPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = null
                                    )
                                }
                            }

                            // Botón de Micrófono o Push-To-Talk
                            if (livePushToTalk) {
                                val pttLabel = if (isTalking) stringResource(R.string.live_mode_ptt_talking)
                                else stringResource(R.string.live_mode_ptt_button)

                                Button(
                                    onClick = {},
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    viewModel.setPushToTalkState(true)
                                                    tryAwaitRelease()
                                                    viewModel.setPushToTalkState(false)
                                                }
                                            )
                                        }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = pttLabel
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTalking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(pttLabel)
                                }
                            } else {
                                val micDesc = if (isMicMuted) stringResource(R.string.live_mode_mic_muted)
                                else stringResource(R.string.live_mode_mic_unmuted)

                                IconButton(
                                    onClick = { viewModel.toggleMicMute() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isMicMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = micDesc
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                        contentDescription = null
                                    )
                                }
                            }

                            // Botón Principal: Iniciar / Finalizar Conversación
                            val isRunningOrStarting = sessionState == LiveSessionState.RUNNING || sessionState == LiveSessionState.STARTING
                            val mainActionLabel = if (isRunningOrStarting) stringResource(R.string.live_mode_stop)
                            else stringResource(R.string.live_mode_start)

                            Button(
                                onClick = {
                                    if (isRunningOrStarting) {
                                        viewModel.stopSession()
                                    } else {
                                        viewModel.startSession(
                                            onMissingApiKey = { showApiKeyDialog = true }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(52.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = mainActionLabel
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunningOrStarting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRunningOrStarting) Icons.Filled.CallEnd else Icons.Filled.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(mainActionLabel)
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de error
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

    // Diálogo si falta API Key
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(stringResource(R.string.live_mode_api_key_required)) },
            confirmButton = {
                Button(
                    onClick = {
                        showApiKeyDialog = false
                        onNavigateToSettings()
                    }
                ) {
                    Text(stringResource(R.string.live_mode_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Diálogo de Ajustes del Modo Live
    if (showConfigDialog) {
        LiveSettingsDialog(
            currentModel = liveModel,
            currentVoice = liveVoice,
            currentThinking = liveThinkingLevel,
            currentVad = liveVadSensitivity,
            currentPtt = livePushToTalk,
            currentInterval = liveCaptureIntervalSeconds,
            onDismiss = { showConfigDialog = false },
            onSaveModel = { viewModel.saveLiveModel(it) },
            onSaveVoice = { viewModel.saveLiveVoice(it) },
            onSaveThinking = { viewModel.saveLiveThinkingLevel(it) },
            onSaveVad = { viewModel.saveLiveVadSensitivity(it) },
            onSavePtt = { viewModel.saveLivePushToTalk(it) },
            onSaveInterval = { viewModel.saveLiveCaptureIntervalSeconds(it) }
        )
    }
}

@Composable
private fun PermissionsRationaleView(
    modifier: Modifier = Modifier,
    permissionsState: MultiplePermissionsState
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.live_mode_audio_permission_required),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.live_mode_camera_permission_required),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { permissionsState.launchMultiplePermissionRequest() }
        ) {
            Text(stringResource(R.string.live_mode_grant_permissions))
        }
    }
}

@Composable
private fun CameraLivePreview(
    isFrontCamera: Boolean,
    isTorchOn: Boolean,
    captureIntervalSeconds: Float,
    onFrame: (android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastCapturedTime by remember { mutableLongStateOf(0L) }
    val intervalMs = (captureIntervalSeconds * 1000L).toLong().coerceAtLeast(400L)

    val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        }
    }

    LaunchedEffect(cameraSelector) {
        cameraController.cameraSelector = cameraSelector
    }

    LaunchedEffect(isTorchOn) {
        try {
            cameraController.enableTorch(isTorchOn)
        } catch (_: Exception) {}
    }

    DisposableEffect(cameraController, intervalMs) {
        val executor = Executors.newSingleThreadExecutor()
        cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastCapturedTime >= intervalMs) {
                lastCapturedTime = now
                try {
                    val bitmap = imageProxy.toBitmap()
                    onFrame(bitmap)
                } catch (_: Exception) {}
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

@Composable
private fun ScreenLiveView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF00E676)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.live_mode_screen_active),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.live_mode_screen_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VoiceOnlyLiveView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Headphones,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF00B8FF)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.live_mode_source_none),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.live_mode_voice_only_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LiveSettingsDialog(
    currentModel: String,
    currentVoice: String,
    currentThinking: String,
    currentVad: String,
    currentPtt: Boolean,
    currentInterval: Float,
    onDismiss: () -> Unit,
    onSaveModel: (String) -> Unit,
    onSaveVoice: (String) -> Unit,
    onSaveThinking: (String) -> Unit,
    onSaveVad: (String) -> Unit,
    onSavePtt: (Boolean) -> Unit,
    onSaveInterval: (Float) -> Unit
) {
    var expandedModel by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }
    var expandedThinking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_mode_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Modelo Live
                ExposedDropdownMenuBox(
                    expanded = expandedModel,
                    onExpandedChange = { expandedModel = it }
                ) {
                    OutlinedTextField(
                        value = currentModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.live_mode_model_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedModel,
                        onDismissRequest = { expandedModel = false }
                    ) {
                        GeminiModelConfig.liveSelectableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    onSaveModel(model)
                                    expandedModel = false
                                }
                            )
                        }
                    }
                }

                // Voz de Gemini
                ExposedDropdownMenuBox(
                    expanded = expandedVoice,
                    onExpandedChange = { expandedVoice = it }
                ) {
                    OutlinedTextField(
                        value = currentVoice,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.live_mode_voice_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVoice) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedVoice,
                        onDismissRequest = { expandedVoice = false }
                    ) {
                        GeminiModelConfig.liveVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voice) },
                                onClick = {
                                    onSaveVoice(voice)
                                    expandedVoice = false
                                }
                            )
                        }
                    }
                }

                // Nivel de Razonamiento
                ExposedDropdownMenuBox(
                    expanded = expandedThinking,
                    onExpandedChange = { expandedThinking = it }
                ) {
                    OutlinedTextField(
                        value = currentThinking.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.live_mode_thinking_level_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedThinking) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedThinking,
                        onDismissRequest = { expandedThinking = false }
                    ) {
                        GeminiModelConfig.liveThinkingLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onSaveThinking(level)
                                    expandedThinking = false
                                }
                            )
                        }
                    }
                }

                // Modo de Micrófono
                Text(
                    text = stringResource(R.string.live_mode_mic_mode_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSavePtt(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!currentPtt) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text(stringResource(R.string.live_mode_mic_continuous), style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedButton(
                        onClick = { onSavePtt(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (currentPtt) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text(stringResource(R.string.live_mode_mic_ptt), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!currentPtt) {
                    Text(
                        text = stringResource(R.string.live_mode_thinking_level_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveVad("high") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (currentVad == "high") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Text("Sensibilidad alta", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { onSaveVad("low") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (currentVad == "low") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Text("Sensibilidad baja", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Intervalo de Captura
                Text(
                    text = stringResource(R.string.live_mode_interval_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveInterval(1.0f) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (currentInterval == 1.0f) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text("1.0s")
                    }
                    OutlinedButton(
                        onClick = { onSaveInterval(1.5f) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (currentInterval == 1.5f) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text("1.5s")
                    }
                    OutlinedButton(
                        onClick = { onSaveInterval(2.0f) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (currentInterval == 2.0f) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text("2.0s")
                    }
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
