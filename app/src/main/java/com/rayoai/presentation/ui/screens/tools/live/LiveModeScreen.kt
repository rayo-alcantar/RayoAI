@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)

package com.rayoai.presentation.ui.screens.tools.live

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
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
    val liveAudioOutput by viewModel.liveAudioOutput.collectAsState()
    val liveDontAskScreenShareConfirm by viewModel.liveDontAskScreenShareConfirm.collectAsState()
    val liveRecentSharedApps by viewModel.liveRecentSharedApps.collectAsState()

    var showConfigSheet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    var showAppPickerSheet by remember { mutableStateOf(false) }
    var appToConfirmShare by remember { mutableStateOf<AppInfo?>(null) }
    var pendingAppPackage by remember { mutableStateOf<String?>(null) }
    var pendingAppName by remember { mutableStateOf<String?>(null) }
    var currentActiveAppName by remember { mutableStateOf<String?>(null) }

    // Textos accesibles resueltos en alcance Composable
    val screenTitle = stringResource(R.string.live_mode_title)
    val stateStopped = stringResource(R.string.live_mode_state_stopped)
    val stateStarting = stringResource(R.string.live_mode_state_starting)
    val stateRunning = stringResource(R.string.live_mode_state_running)
    val stateReconnecting = stringResource(R.string.live_mode_state_reconnecting)
    val statePaused = stringResource(R.string.live_mode_state_paused)
    val tapAnywhereToInterrupt = stringResource(R.string.live_mode_tap_anywhere_to_interrupt)

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

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val targetPkg = pendingAppPackage
            val targetName = pendingAppName
            currentActiveAppName = targetName

            if (targetPkg != null) {
                viewModel.setVisualSource(VisualSource.APP)
                viewModel.addRecentSharedApp(targetPkg)
            } else {
                viewModel.setVisualSource(VisualSource.SCREEN)
            }

            LiveSessionForegroundService.startScreenCapture(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!,
                appName = targetName
            )

            if (sessionState == LiveSessionState.STOPPED) {
                viewModel.startSession(
                    onMissingApiKey = { showApiKeyDialog = true }
                )
            }

            if (targetPkg != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIntent != null) {
                    try {
                        context.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.w("LiveModeScreen", "No se pudo abrir la app seleccionada: ${e.message}")
                    }
                }
            } else {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(homeIntent)
                } catch (_: Exception) {}
            }
        } else {
            pendingAppPackage = null
            pendingAppName = null
            currentActiveAppName = null
            viewModel.setVisualSource(VisualSource.CAMERA)
        }
    }

    val launchScreenCapture: (String?, String?) -> Unit = { pkg, name ->
        pendingAppPackage = pkg
        pendingAppName = name
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
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

    // Apagar la linterna automáticamente si se cambia de fuente, se pausa el video o se pasa a cámara frontal
    LaunchedEffect(visualSource, isVideoPaused, isFrontCamera) {
        if (visualSource != VisualSource.CAMERA || isVideoPaused || isFrontCamera) {
            isTorchOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            feedbackHelper.release()
            LiveSessionForegroundService.stopScreenCapture(context)
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
                val isRunning = sessionState == LiveSessionState.RUNNING
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF121212))
                        .clickable(enabled = isRunning) {
                            feedbackHelper.onPttRelease()
                            viewModel.interruptCurrentResponse()
                        }
                        .semantics {
                            if (isRunning) {
                                role = Role.Button
                                contentDescription = tapAnywhereToInterrupt
                            }
                        }
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
                                // Linterna: solo si es cámara trasera (la frontal no tiene flash)
                                if (!isFrontCamera) {
                                    val torchDesc = if (isTorchOn) stringResource(R.string.live_mode_torch_off_desc)
                                    else stringResource(R.string.live_mode_torch_on_desc)

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
                                }

                                val flipDesc = if (isFrontCamera) stringResource(R.string.live_mode_camera_front_desc)
                                else stringResource(R.string.live_mode_camera_back_desc)

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
                        VisualSource.APP -> {
                            ScreenLiveView(
                                appName = currentActiveAppName,
                                onOpenApp = {
                                    pendingAppPackage?.let { pkg ->
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        if (launchIntent != null) {
                                            try {
                                                context.startActivity(launchIntent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            )
                        }
                        VisualSource.NONE -> {
                            VoiceOnlyLiveView()
                        }
                    }

                    // Franja de Estado y Subtítulos Accesibles (con toque para interrumpir)
                    val interruptHint = stringResource(R.string.live_mode_tap_to_interrupt)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Color.Black.copy(alpha = 0.75f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = isRunning) {
                                viewModel.interruptCurrentResponse()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                if (isRunning) {
                                    role = Role.Button
                                    contentDescription = if (latestTranscript.isNotBlank()) {
                                        "$currentStatusText. $latestTranscript. $interruptHint"
                                    } else {
                                        "$currentStatusText. $interruptHint"
                                    }
                                } else {
                                    contentDescription = "$currentStatusText. $latestTranscript"
                                }
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isCamera = visualSource == VisualSource.CAMERA
                            val isScreen = visualSource == VisualSource.SCREEN
                            val isApp = visualSource == VisualSource.APP
                            val isNone = visualSource == VisualSource.NONE

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_camera),
                                icon = Icons.Filled.Videocam,
                                isSelected = isCamera,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    LiveSessionForegroundService.stopScreenCapture(context)
                                    pendingAppPackage = null
                                    pendingAppName = null
                                    currentActiveAppName = null
                                    viewModel.setVisualSource(VisualSource.CAMERA)
                                }
                            )

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_screen),
                                icon = Icons.AutoMirrored.Filled.ScreenShare,
                                isSelected = isScreen,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    LiveSessionForegroundService.stopScreenCapture(context)
                                    launchScreenCapture(null, null)
                                }
                            )

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_app),
                                icon = Icons.Filled.Apps,
                                isSelected = isApp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    showAppPickerSheet = true
                                }
                            )

                            ModeSelectChip(
                                label = stringResource(R.string.live_mode_source_none),
                                icon = Icons.Filled.Headphones,
                                isSelected = isNone,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    LiveSessionForegroundService.stopScreenCapture(context)
                                    pendingAppPackage = null
                                    pendingAppName = null
                                    currentActiveAppName = null
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
                                    // Control de video: solo en modo CÁMARA (pausar/reanudar)
                                    // En modo Pantalla/App: botón para detener la captura de pantalla
                                    if (visualSource == VisualSource.CAMERA) {
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
                                    } else if (visualSource == VisualSource.SCREEN || visualSource == VisualSource.APP) {
                                        val stopShareDesc = stringResource(R.string.live_mode_stop_screen_share)
                                        FilledTonalIconButton(
                                            onClick = {
                                                LiveSessionForegroundService.stopScreenCapture(context)
                                                pendingAppPackage = null
                                                pendingAppName = null
                                                currentActiveAppName = null
                                                viewModel.setVisualSource(VisualSource.CAMERA)
                                            },
                                            modifier = Modifier
                                                .size(52.dp)
                                                .semantics {
                                                    role = Role.Button
                                                    contentDescription = stopShareDesc
                                                },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    // Conmutador Rápido de Salida de Audio (Altavoz / Auricular / Silenciado)
                                    val nextOutput = when (liveAudioOutput) {
                                        "speaker" -> "earpiece"
                                        "earpiece" -> "muted"
                                        else -> "speaker"
                                    }
                                    val outputIcon = when (liveAudioOutput) {
                                        "earpiece" -> Icons.Filled.Headphones
                                        "muted" -> Icons.AutoMirrored.Filled.VolumeOff
                                        else -> Icons.AutoMirrored.Filled.VolumeUp
                                    }
                                    val outputActionHint = when (liveAudioOutput) {
                                        "earpiece" -> stringResource(R.string.live_mode_audio_output_action_muted)
                                        "muted" -> stringResource(R.string.live_mode_audio_output_action_speaker)
                                        else -> stringResource(R.string.live_mode_audio_output_action_earpiece)
                                    }
                                    val outputCurrentDesc = when (liveAudioOutput) {
                                        "earpiece" -> stringResource(R.string.live_mode_audio_output_earpiece)
                                        "muted" -> stringResource(R.string.live_mode_audio_output_muted)
                                        else -> stringResource(R.string.live_mode_audio_output_speaker)
                                    }

                                    FilledTonalIconButton(
                                        onClick = { viewModel.setAudioOutput(nextOutput) },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics {
                                                role = Role.Button
                                                stateDescription = outputCurrentDesc
                                                contentDescription = outputActionHint
                                            },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (liveAudioOutput == "muted") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = outputIcon,
                                            contentDescription = null,
                                            tint = if (liveAudioOutput == "muted") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                            .weight(if (livePushToTalk) 0.8f else 1.1f)
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
            currentAudioOutput = liveAudioOutput,
            onDismiss = { showConfigSheet = false },
            onSaveModel = { viewModel.saveLiveModel(it) },
            onSaveVoice = { viewModel.saveLiveVoice(it) },
            onSaveThinking = { viewModel.saveLiveThinkingLevel(it) },
            onSaveVad = { viewModel.saveLiveVadSensitivity(it) },
            onSavePtt = { viewModel.saveLivePushToTalk(it) },
            onSaveInterval = { viewModel.saveLiveCaptureIntervalSeconds(it) },
            onSaveAudioOutput = { viewModel.setAudioOutput(it) }
        )
    }

    // Selector de Aplicación para Compartir
    if (showAppPickerSheet) {
        AppSharePickerSheet(
            recentPackages = liveRecentSharedApps,
            onDismiss = { showAppPickerSheet = false },
            onSelectApp = { app ->
                showAppPickerSheet = false
                if (liveDontAskScreenShareConfirm) {
                    launchScreenCapture(app.packageName, app.name)
                } else {
                    appToConfirmShare = app
                }
            }
        )
    }

    // Confirmación de Compartir Aplicación
    if (appToConfirmShare != null) {
        val app = appToConfirmShare!!
        var dontAskChecked by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { appToConfirmShare = null },
            title = {
                Text(stringResource(R.string.live_mode_confirm_app_share_title))
            },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.live_mode_confirm_app_share_message,
                            app.name
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dontAskChecked = !dontAskChecked }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = dontAskChecked,
                            onCheckedChange = { dontAskChecked = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_mode_dont_ask_again),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = app
                        appToConfirmShare = null
                        if (dontAskChecked) {
                            viewModel.saveLiveDontAskScreenShareConfirm(true)
                        }
                        launchScreenCapture(target.packageName, target.name)
                    }
                ) {
                    Text(stringResource(R.string.live_mode_confirm_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { appToConfirmShare = null }) {
                    Text(stringResource(R.string.live_mode_cancel))
                }
            }
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
        border = BorderStroke(1.dp, borderColor)
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
private fun CyclingSettingButton(
    title: String,
    currentValue: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tapToChangeText = stringResource(R.string.live_mode_tap_to_change)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "$title: $currentValue. $tapToChangeText"
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
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
private fun ScreenLiveView(
    appName: String? = null,
    onOpenApp: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (!appName.isNullOrBlank()) Icons.Filled.Apps else Icons.AutoMirrored.Filled.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF00E676)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (!appName.isNullOrBlank()) {
                stringResource(R.string.live_mode_screen_notification_title_app, appName)
            } else {
                stringResource(R.string.live_mode_screen_active)
            },
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
        if (onOpenApp != null && !appName.isNullOrBlank()) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.live_mode_open_app_button, appName),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
    currentAudioOutput: String,
    onDismiss: () -> Unit,
    onSaveModel: (String) -> Unit,
    onSaveVoice: (String) -> Unit,
    onSaveThinking: (String) -> Unit,
    onSaveVad: (String) -> Unit,
    onSavePtt: (Boolean) -> Unit,
    onSaveInterval: (Float) -> Unit,
    onSaveAudioOutput: (String) -> Unit
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

            // Sección 2: Micrófono y Detección (Botones conmutadores de un solo toque)
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

                    // Botón conmutador: Modo de micrófono (Manos libres <-> Pulsar para hablar)
                    val currentMicModeText = if (!currentPtt) {
                        stringResource(R.string.live_mode_mic_continuous)
                    } else {
                        stringResource(R.string.live_mode_mic_ptt)
                    }
                    CyclingSettingButton(
                        title = stringResource(R.string.live_mode_mic_mode_label),
                        currentValue = currentMicModeText,
                        icon = if (!currentPtt) Icons.Filled.Mic else Icons.Filled.RecordVoiceOver,
                        onClick = { onSavePtt(!currentPtt) }
                    )

                    // Botón conmutador: Sensibilidad de detección de voz (Alta <-> Baja)
                    if (!currentPtt) {
                        val currentVadText = if (currentVad == "high") {
                            stringResource(R.string.live_mode_vad_high)
                        } else {
                            stringResource(R.string.live_mode_vad_low)
                        }
                        CyclingSettingButton(
                            title = stringResource(R.string.live_mode_vad_label),
                            currentValue = currentVadText,
                            icon = if (currentVad == "high") Icons.Filled.Mic else Icons.Filled.MicOff,
                            onClick = { onSaveVad(if (currentVad == "high") "low" else "high") }
                        )
                    }

                    // Botón conmutador: Salida de Audio (Altavoz <-> Auricular <-> Silenciado)
                    val currentOutputText = when (currentAudioOutput) {
                        "earpiece" -> stringResource(R.string.live_mode_audio_output_earpiece)
                        "muted" -> stringResource(R.string.live_mode_audio_output_muted)
                        else -> stringResource(R.string.live_mode_audio_output_speaker)
                    }
                    val nextOutputMode = when (currentAudioOutput) {
                        "speaker" -> "earpiece"
                        "earpiece" -> "muted"
                        else -> "speaker"
                    }
                    val outputIcon = when (currentAudioOutput) {
                        "earpiece" -> Icons.Filled.Headphones
                        "muted" -> Icons.AutoMirrored.Filled.VolumeOff
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    CyclingSettingButton(
                        title = stringResource(R.string.live_mode_audio_output_label),
                        currentValue = currentOutputText,
                        icon = outputIcon,
                        onClick = { onSaveAudioOutput(nextOutputMode) }
                    )
                }
            }

            // Sección 3: Frecuencia de Captura Visual (Botón conmutador cíclico)
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

                    val currentIntervalText = when (currentInterval) {
                        1.0f -> stringResource(R.string.live_mode_interval_1s)
                        1.5f -> stringResource(R.string.live_mode_interval_1_5s)
                        else -> stringResource(R.string.live_mode_interval_2s)
                    }
                    val nextInterval = when (currentInterval) {
                        1.0f -> 1.5f
                        1.5f -> 2.0f
                        else -> 1.0f
                    }
                    CyclingSettingButton(
                        title = stringResource(R.string.live_mode_interval_label),
                        currentValue = currentIntervalText,
                        icon = Icons.Filled.Timer,
                        onClick = { onSaveInterval(nextInterval) }
                    )
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

private data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSharePickerSheet(
    recentPackages: List<String>,
    onDismiss: () -> Unit,
    onSelectApp: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
            val myPkg = context.packageName
            val loaded = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == myPkg) return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                AppInfo(name = label, packageName = pkg, icon = icon)
            }.distinctBy { it.packageName }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }

            withContext(Dispatchers.Main) {
                apps = loaded
                isLoading = false
            }
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val recentApps = remember(apps, recentPackages, searchQuery) {
        if (searchQuery.isNotBlank() || recentPackages.isEmpty()) emptyList()
        else {
            val appMap = apps.associateBy { it.packageName }
            recentPackages.mapNotNull { appMap[it] }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.live_mode_share_app_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.live_mode_share_app_search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.live_mode_share_app_search_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.live_mode_share_app_loading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.live_mode_share_app_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    if (recentApps.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.live_mode_share_app_recent_section),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(recentApps, key = { "recent_${it.packageName}" }) { app ->
                            AppListItem(app = app, onClick = { onSelectApp(app) })
                        }
                        item {
                            Text(
                                text = stringResource(R.string.live_mode_share_app_all_section),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(app = app, onClick = { onSelectApp(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .semantics {
                role = Role.Button
                contentDescription = app.name
            },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                AsyncImage(
                    model = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
