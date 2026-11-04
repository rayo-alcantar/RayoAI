@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)

package com.rayoai.presentation.ui.screens.tools.live

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

    var showConfigSheet by remember { mutableStateOf(false) }
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

    // Gestor de retroalimentación auditiva y háptica
    val feedbackHelper = remember { LiveFeedbackHelper(context) }

    // Gestionar Lifecycle del Foreground Service y Feedback
    LaunchedEffect(sessionState) {
        when (sessionState) {
            LiveSessionState.STARTING -> feedbackHelper.onStarting()
            LiveSessionState.RUNNING -> {
                feedbackHelper.onConnected()
                LiveSessionForegroundService.start(context)
            }
            LiveSessionState.STOPPED -> {
                feedbackHelper.onDisconnected()
                LiveSessionForegroundService.stop(context)
                screenCaptureManager.stop()
            }
            LiveSessionState.RECONNECTING -> feedbackHelper.onStarting()
            else -> Unit
        }
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            feedbackHelper.onError()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            feedbackHelper.release()
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(screenTitle, fontWeight = FontWeight.Bold)
                        if (sessionState == LiveSessionState.RUNNING) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                        }
                    }
                },
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
                    IconButton(
                        onClick = { showConfigSheet = true },
                        modifier = Modifier.semantics {
                            contentDescription = screenTitle
                        }
                    ) {
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
                // Escenario Central Visual (Cámara, Pantalla o Solo Voz)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF121212))
                ) {
                    when (visualSource) {
                        VisualSource.CAMERA -> {
                            CameraLivePreview(
                                isFrontCamera = isFrontCamera,
                                isTorchOn = isTorchOn,
                                captureIntervalSeconds = liveCaptureIntervalSeconds,
                                onFrame = { bitmap -> viewModel.sendBitmapFrame(bitmap) }
                            )

                            // Botones flotantes de control de cámara (Linterna y Flip)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val torchDesc = if (isTorchOn) stringResource(R.string.live_mode_torch_off)
                                else stringResource(R.string.live_mode_torch_on)

                                IconButton(
                                    onClick = { isTorchOn = !isTorchOn },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = torchDesc
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                        contentDescription = null,
                                        tint = if (isTorchOn) Color(0xFFFFD600) else Color.White
                                    )
                                }

                                val flipDesc = stringResource(R.string.live_mode_flip_camera)
                                IconButton(
                                    onClick = { isFrontCamera = !isFrontCamera },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = flipDesc
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Cameraswitch,
                                        contentDescription = null,
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

                    // Franja de Estado y Subtítulos Accesibles
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .background(
                                Color.Black.copy(alpha = 0.75f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "$currentStatusText. $latestTranscript"
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (sessionState) {
                                LiveSessionState.RUNNING -> Color(0xFF00E676)
                                LiveSessionState.STARTING, LiveSessionState.RECONNECTING -> Color(0xFFFFB300)
                                else -> Color(0xFF9E9E9E)
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentStatusText,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (latestTranscript.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = latestTranscript,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFEEEEEE)
                            )
                        }
                    }
                }

                // Panel de Controles Inferior Rediseñado
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Selector de Modo Visual
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isCamera = visualSource == VisualSource.CAMERA
                            val isScreen = visualSource == VisualSource.SCREEN
                            val isNone = visualSource == VisualSource.NONE

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_camera),
                                icon = Icons.Filled.Videocam,
                                isSelected = isCamera,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    screenCaptureManager.stop()
                                    viewModel.setVisualSource(VisualSource.CAMERA)
                                }
                            )

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_screen),
                                icon = Icons.AutoMirrored.Filled.ScreenShare,
                                isSelected = isScreen,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.setVisualSource(VisualSource.SCREEN)
                                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                                }
                            )

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_none),
                                icon = Icons.Filled.Headphones,
                                isSelected = isNone,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    screenCaptureManager.stop()
                                    viewModel.setVisualSource(VisualSource.NONE)
                                }
                            )
                        }

                        // Fila de Acción Principal / En Llamada
                        when (sessionState) {
                            LiveSessionState.STOPPED -> {
                                val startLabel = stringResource(R.string.live_mode_start)
                                Button(
                                    onClick = {
                                        viewModel.startSession(
                                            onMissingApiKey = { showApiKeyDialog = true }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = startLabel
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(startLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }

                            LiveSessionState.STARTING, LiveSessionState.RECONNECTING -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = currentStatusText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.stopSession() },
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(stringResource(android.R.string.cancel))
                                    }
                                }
                            }

                            LiveSessionState.RUNNING, LiveSessionState.PAUSED, LiveSessionState.STOPPING -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pausar / Reanudar Video si la fuente no es solo audio
                                    if (visualSource != VisualSource.NONE) {
                                        val pauseDesc = if (isVideoPaused) stringResource(R.string.live_mode_resume_video)
                                        else stringResource(R.string.live_mode_pause_video)

                                        FilledTonalIconButton(
                                            onClick = { viewModel.toggleVideoPause() },
                                            modifier = Modifier
                                                .size(52.dp)
                                                .semantics {
                                                    role = Role.Button
                                                    contentDescription = pauseDesc
                                                },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = if (isVideoPaused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isVideoPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                                contentDescription = null
                                            )
                                        }
                                    }

                                    // Controles de Micrófono o Push-To-Talk
                                    if (livePushToTalk) {
                                        val pttLabel = if (isTalking) stringResource(R.string.live_mode_ptt_talking)
                                        else stringResource(R.string.live_mode_ptt_button)

                                        Button(
                                            onClick = {},
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(54.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onPress = {
                                                            feedbackHelper.onPttPress()
                                                            viewModel.setPushToTalkState(true)
                                                            tryAwaitRelease()
                                                            feedbackHelper.onPttRelease()
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
                                            ),
                                            shape = RoundedCornerShape(27.dp)
                                        ) {
                                            Icon(Icons.Filled.Mic, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(pttLabel, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        val micDesc = if (isMicMuted) stringResource(R.string.live_mode_mic_muted)
                                        else stringResource(R.string.live_mode_mic_unmuted)

                                        FilledTonalIconButton(
                                            onClick = { viewModel.toggleMicMute() },
                                            modifier = Modifier
                                                .size(52.dp)
                                                .semantics {
                                                    role = Role.Button
                                                    contentDescription = micDesc
                                                },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = if (isMicMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                                contentDescription = null,
                                                tint = if (isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Botón Finalizar Conversación
                                    val stopLabel = stringResource(R.string.live_mode_stop)
                                    Button(
                                        onClick = { viewModel.stopSession() },
                                        modifier = Modifier
                                            .weight(if (livePushToTalk) 0.8f else 1.2f)
                                            .height(54.dp)
                                            .semantics {
                                                role = Role.Button
                                                contentDescription = stopLabel
                                            },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        shape = RoundedCornerShape(27.dp)
                                    ) {
                                        Icon(Icons.Filled.CallEnd, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stopLabel, fontWeight = FontWeight.Bold)
                                    }
                                }
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

    // Hoja Inferior (ModalBottomSheet) de Ajustes del Modo Live
    if (showConfigSheet) {
        LiveSettingsSheet(
            currentModel = liveModel,
            currentVoice = liveVoice,
            currentThinking = liveThinkingLevel,
            currentVad = liveVadSensitivity,
            currentPtt = livePushToTalk,
            currentInterval = liveCaptureIntervalSeconds,
            onDismiss = { showConfigSheet = false },
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
private fun ModeSelectChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = label
            },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        }
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
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
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
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
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
private fun LiveSettingsSheet(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var expandedModel by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }
    var expandedThinking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera de Ajustes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.live_mode_config_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.live_mode_settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
                }
            }

            // Sección 1: Inteligencia y Voz
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_live_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Modelo
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

                    // Voz
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

                    // Thinking
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
                }
            }

            // Sección 2: Micrófono y Detección
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_mode_mic_mode_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeSelectChip(
                            label = stringResource(R.string.live_mode_mic_continuous),
                            icon = Icons.Filled.Mic,
                            isSelected = !currentPtt,
                            modifier = Modifier.weight(1f),
                            onClick = { onSavePtt(false) }
                        )

                        ModeSelectChip(
                            label = stringResource(R.string.live_mode_mic_ptt),
                            icon = Icons.Filled.RecordVoiceOver,
                            isSelected = currentPtt,
                            modifier = Modifier.weight(1f),
                            onClick = { onSavePtt(true) }
                        )
                    }

                    if (!currentPtt) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.live_mode_vad_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_vad_high),
                                icon = Icons.Filled.Mic,
                                isSelected = currentVad == "high",
                                modifier = Modifier.weight(1f),
                                onClick = { onSaveVad("high") }
                            )

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_vad_low),
                                icon = Icons.Filled.MicOff,
                                isSelected = currentVad == "low",
                                modifier = Modifier.weight(1f),
                                onClick = { onSaveVad("low") }
                            )
                        }
                    }
                }
            }

            // Sección 3: Frecuencia de Captura Visual
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_mode_interval_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeSelectChip(
                            label = "1.0s",
                            icon = Icons.Filled.Timer,
                            isSelected = currentInterval == 1.0f,
                            modifier = Modifier.weight(1f),
                            onClick = { onSaveInterval(1.0f) }
                        )

                        ModeSelectChip(
                            label = "1.5s",
                            icon = Icons.Filled.Timer,
                            isSelected = currentInterval == 1.5f,
                            modifier = Modifier.weight(1f),
                            onClick = { onSaveInterval(1.5f) }
                        )

                        ModeSelectChip(
                            label = "2.0s",
                            icon = Icons.Filled.Timer,
                            isSelected = currentInterval == 2.0f,
                            modifier = Modifier.weight(1f),
                            onClick = { onSaveInterval(2.0f) }
                        )
                    }
                }
            }

            // Botón de Confirmación Listo
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.Filled.Done, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
