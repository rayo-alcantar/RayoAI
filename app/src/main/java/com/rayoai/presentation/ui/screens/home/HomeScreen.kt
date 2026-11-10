package com.rayoai.presentation.ui.screens.home

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rayoai.BuildConfig
import com.rayoai.R
import com.rayoai.core.VirtualBackgroundConfig
import com.rayoai.core.VirtualBackgroundMode
import com.rayoai.presentation.ui.components.CameraView
import com.rayoai.presentation.ui.components.ChatBubble
import com.rayoai.presentation.ui.navigation.Screen
import android.media.MediaPlayer
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.delay
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import android.content.ActivityNotFoundException
import com.google.android.play.core.review.ReviewManagerFactory
import com.rayoai.presentation.ui.LocalTextToSpeech
import com.rayoai.presentation.ui.findActivity
import com.rayoai.presentation.ui.openPlayStoreListing

private const val MAX_GALLERY_BATCH_IMAGES = 50

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController,
    imageUri: Uri? = null,
    captureId: Long? = null,
    sharedImageUris: List<Uri> = emptyList(),
    onSharedImagesConsumed: () -> Unit = {},
    onImageConsumed: () -> Unit = {},
    onOpenMultiImagePassage: (List<Uri>) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(imageUri) {
        imageUri?.let {
            viewModel.describeImageFromUri(
                uri = it,
                errorResId = R.string.error_loading_shared_image
            )
            onImageConsumed()
        }
    }

    LaunchedEffect(sharedImageUris) {
        if (sharedImageUris.size != 2) return@LaunchedEffect
        val firstImageUri = sharedImageUris.first()
        viewModel.describeImageFromUri(
            uri = firstImageUri,
            pairedUris = sharedImageUris,
            errorResId = R.string.error_loading_shared_image
        )
        onSharedImagesConsumed()
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val writeStoragePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(MAX_GALLERY_BATCH_IMAGES)
    ) { uris: List<Uri> ->
        if (uris.size >= 3) {
            onOpenMultiImagePassage(uris)
        } else if (uris.size == 2) {
            viewModel.describeImageFromUri(
                uri = uris.first(),
                pairedUris = uris,
                errorResId = R.string.error_loading_gallery_image
            )
        } else {
            uris.firstOrNull()?.let {
                viewModel.describeImageFromUri(
                    uri = it,
                    errorResId = R.string.error_loading_gallery_image
                )
            }
        }
    }

    val multipleGalleryLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(uiState.maxImagesInChat),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesSelected(uris)
        }
    }

    var tempUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempUri?.let { viewModel.onImagesSelected(listOf(it)) }
        }
    }


    LaunchedEffect(Unit) {
        viewModel.playCaptureSound.collect {
            val mediaPlayer = MediaPlayer.create(context, R.raw.send)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
        }
    }

    val sharedTts = LocalTextToSpeech.current
    var fallbackTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsInitialized by remember(sharedTts) { mutableStateOf(sharedTts != null) }
    DisposableEffect(context, sharedTts) {
        if (sharedTts != null) {
            ttsInitialized = true
            onDispose { sharedTts.stop() }
        } else {
            var localEngine: TextToSpeech? = null
            val googleEngine = "com.google.android.tts"
            val isGoogleTtsAvailable = try {
                context.packageManager.getPackageInfo(googleEngine, 0) != null
            } catch (_: Exception) {
                false
            }
            val targetEngine = if (isGoogleTtsAvailable) googleEngine else null

            localEngine = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val preferredLocale = Locale.getDefault().takeIf { it.language.isNotBlank() } ?: Locale("es", "ES")
                    val result = localEngine?.setLanguage(preferredLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        localEngine?.setLanguage(Locale("es", "ES"))
                    }
                    ttsInitialized = true
                } else {
                    Log.e("HomeScreen", "TTS initialization failed with status: $status")
                    viewModel.setError(context.getString(R.string.tts_init_error))
                }
            }, targetEngine)
            fallbackTts = localEngine
            onDispose {
                localEngine?.stop()
                localEngine?.shutdown()
                fallbackTts = null
            }
        }
    }
    val textToSpeech = sharedTts ?: fallbackTts

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.screenState is HomeScreenState.ImageCaptured) {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.app_name)) },
                    navigationIcon = {
                        BackHandler {
                            viewModel.resetHomeScreenState()
                        }
                        IconButton(onClick = { viewModel.resetHomeScreenState() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {}
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (uiState.showRatingBanner) {
                RatingBanner(
                    onRateNow = { viewModel.onRateNowClicked() },
                    onRateLater = { viewModel.onRateLaterClicked() }
                )
            }
            if (BuildConfig.DONATIONS_ENABLED && uiState.showDonationBanner) {
                DonationBanner(
                    onDonateNow = {
                        viewModel.onDonationNowClicked()
                        navController.navigate(Screen.About.createRoute(showDonationDialog = true))
                    },
                    onDonateLater = { viewModel.onDonationLaterClicked() }
                )
            }
            when (uiState.screenState) {
                HomeScreenState.Initial -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .padding(top = 12.dp, bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledIconButton(
                                    onClick = { viewModel.toggleCamera() },
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Cameraswitch,
                                        contentDescription = if (uiState.currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                            stringResource(R.string.switch_to_front_camera)
                                        else
                                            stringResource(R.string.switch_to_back_camera)
                                    )
                                }

                                val cameraModePhoto = stringResource(R.string.camera_mode_photo)
                                val cameraModePortraitSoftware = stringResource(R.string.camera_mode_portrait_software)
                                val cameraModePortraitHardware = stringResource(R.string.camera_mode_portrait_hardware)
                                val cameraModePhotoDescWithHw = stringResource(R.string.camera_mode_photo_desc_with_hw)
                                val cameraModePhotoDescNoHw = stringResource(R.string.camera_mode_photo_desc_no_hw)
                                val cameraModePortraitSoftwareDescWithHw = stringResource(R.string.camera_mode_portrait_software_desc_with_hw)
                                val cameraModePortraitSoftwareDescNoHw = stringResource(R.string.camera_mode_portrait_software_desc_no_hw)
                                val cameraModePortraitHardwareDesc = stringResource(R.string.camera_mode_portrait_hardware_desc)

                                val portraitButtonDesc = when (uiState.portraitMode) {
                                    PortraitMode.OFF -> if (uiState.isHardwarePortraitSupported) cameraModePhotoDescWithHw else cameraModePhotoDescNoHw
                                    PortraitMode.SOFTWARE -> if (uiState.isHardwarePortraitSupported) cameraModePortraitSoftwareDescWithHw else cameraModePortraitSoftwareDescNoHw
                                    PortraitMode.HARDWARE -> cameraModePortraitHardwareDesc
                                }

                                val portraitStateDesc = when (uiState.portraitMode) {
                                    PortraitMode.OFF -> cameraModePhoto
                                    PortraitMode.SOFTWARE -> cameraModePortraitSoftware
                                    PortraitMode.HARDWARE -> cameraModePortraitHardware
                                }

                                FilledIconButton(
                                    onClick = { viewModel.togglePortraitMode() },
                                    enabled = !uiState.isLoading && !uiState.isCapturing,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .semantics {
                                            contentDescription = portraitButtonDesc
                                            stateDescription = portraitStateDesc
                                        },
                                    colors = when (uiState.portraitMode) {
                                        PortraitMode.OFF -> IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        PortraitMode.SOFTWARE -> IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                        PortraitMode.HARDWARE -> IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = when (uiState.portraitMode) {
                                            PortraitMode.OFF -> Icons.Default.PhotoCamera
                                            PortraitMode.SOFTWARE -> Icons.Default.Portrait
                                            PortraitMode.HARDWARE -> Icons.Default.AutoAwesome
                                        },
                                        contentDescription = null
                                    )
                                }
                            }

                            Image(
                                painter = painterResource(id = R.drawable.logo_rayoai),
                                contentDescription = stringResource(id = R.string.app_name),
                                modifier = Modifier.size(52.dp)
                            )

                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var showContextMenu by remember { mutableStateOf(false) }
                                val moreOptionsLabel = stringResource(R.string.more_photo_options)

                                Box {
                                    FilledIconButton(
                                        onClick = { showContextMenu = true },
                                        enabled = !uiState.isLoading && !uiState.isCapturing,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics {
                                                contentDescription = moreOptionsLabel
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = null
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showContextMenu,
                                        onDismissRequest = { showContextMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_add_background)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Wallpaper,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showContextMenu = false
                                                navController.navigate(Screen.SelectBackground.route)
                                            }
                                        )
                                    }
                                }

                                FilledIconButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = stringResource(R.string.load_from_gallery)
                                    )
                                }
                            }
                        }

                        if (uiState.virtualBackgroundConfig.mode != VirtualBackgroundMode.NONE) {
                            val activeBgName = when (uiState.virtualBackgroundConfig.mode) {
                                VirtualBackgroundMode.WHITE -> stringResource(R.string.bg_option_white_name)
                                VirtualBackgroundMode.ROYAL_BLUE -> stringResource(R.string.bg_option_royal_blue_name)
                                VirtualBackgroundMode.CUSTOM -> stringResource(R.string.bg_option_custom)
                                VirtualBackgroundMode.NONE -> ""
                            }
                            val badgeText = stringResource(R.string.bg_active_badge, activeBgName)
                            val badgeDesc = stringResource(R.string.bg_active_badge_desc, activeBgName)
                            val removeDesc = stringResource(R.string.bg_remove_desc)

                            InputChip(
                                selected = true,
                                onClick = { navController.navigate(Screen.SelectBackground.route) },
                                label = {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (uiState.virtualBackgroundConfig.mode) {
                                                    VirtualBackgroundMode.WHITE -> Color.White
                                                    VirtualBackgroundMode.ROYAL_BLUE -> Color(0xFF102A71)
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.clearVirtualBackground() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = removeDesc,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .semantics {
                                        contentDescription = badgeDesc
                                    }
                            )
                        }

                        if (cameraPermissionState.status.isGranted) {
                            CameraView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                onImageCaptured = { bitmap ->
                                    viewModel.describeImage(bitmap)
                                },
                                onError = { errorMessage ->
                                    viewModel.onImageCaptureFailed(errorMessage)
                                },
                                isCapturing = uiState.isCapturing,
                                cameraSelector = uiState.currentCameraSelector,
                                flashMode = uiState.flashMode,
                                portraitMode = uiState.portraitMode,
                                virtualBackgroundConfig = uiState.virtualBackgroundConfig,
                                onVirtualBackgroundResult = { personDetected ->
                                    if (!personDetected && uiState.virtualBackgroundConfig.mode != VirtualBackgroundMode.NONE) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.bg_no_person_detected),
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                },
                                onHardwarePortraitAvailabilityChanged = { isSupported ->
                                    viewModel.setHardwarePortraitSupported(isSupported)
                                },
                                isFocusAssistEnabled = uiState.focusAssistMode != FocusAssistMode.OFF &&
                                    !uiState.isLoading &&
                                    !uiState.isCapturing,
                                onFocusAssistAnnouncement = { view.announceForAccessibility(it) },
                                onFocusAssistCentered = {
                                    if (
                                        uiState.focusAssistMode == FocusAssistMode.AUTO_CAPTURE &&
                                        !uiState.isLoading &&
                                        !uiState.isCapturing
                                    ) {
                                        viewModel.triggerImageCapture()
                                    }
                                }
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.camera_permission_required),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var isCountingDown by remember { mutableStateOf(false) }
                            var countdownValue by remember { mutableStateOf(0) }
                            var captureFlashTrigger by remember { mutableStateOf(0) }
                            val captureFlashAlpha = remember { Animatable(0f) }

                            LaunchedEffect(captureFlashTrigger) {
                                if (captureFlashTrigger > 0) {
                                    captureFlashAlpha.snapTo(0.95f)
                                    captureFlashAlpha.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 420)
                                    )
                                }
                            }

                            if (isCountingDown) {
                                val countdownText = stringResource(R.string.capturing_in, countdownValue)
                                LaunchedEffect(countdownValue) {
                                    if (countdownValue > 0) {
                                        textToSpeech?.speak(
                                            countdownValue.toString(),
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            null
                                        )
                                    }
                                }
                                Text(
                                    text = countdownText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .semantics {
                                            liveRegion = LiveRegionMode.Assertive
                                            this.contentDescription = countdownText
                                        }
                                )
                            }

                            val flashAutoDesc = stringResource(R.string.flash_mode_auto)
                            val flashOnDesc = stringResource(R.string.flash_mode_on)
                            val flashOffDesc = stringResource(R.string.flash_mode_off)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Botón de Temporizador (Izquierda)
                                val timerDesc = if (uiState.timerSeconds > 0) {
                                    stringResource(R.string.timer_seconds, uiState.timerSeconds)
                                } else {
                                    stringResource(R.string.timer_off)
                                }
                                val isAutoFocusCaptureMode = uiState.focusAssistMode == FocusAssistMode.AUTO_CAPTURE
                                val timerStateDesc = if (isAutoFocusCaptureMode) {
                                    stringResource(R.string.timer_disabled_focus_auto_capture)
                                } else {
                                    timerDesc
                                }
                                val prePromptDesc = stringResource(
                                    id = if (uiState.showPrePromptInput) R.string.hide_pre_prompt_input else R.string.write_pre_prompt
                                )
                                
                                Column(
                                    modifier = Modifier.width(72.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledIconButton(
                                        onClick = { viewModel.toggleTimer() },
                                        enabled = !isAutoFocusCaptureMode,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics {
                                                contentDescription = timerStateDesc
                                                stateDescription = timerStateDesc
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Timer, contentDescription = null)
                                            if (uiState.timerSeconds > 0) {
                                                Text(
                                                    text = "${uiState.timerSeconds}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            CircleShape
                                                        )
                                                        .padding(horizontal = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    FilledIconButton(
                                        onClick = { viewModel.togglePrePromptInput() },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics {
                                                contentDescription = prePromptDesc
                                            }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                }

                                // Botón Tomar Foto - centrado
                                Button(
                                    onClick = {
                                        if (cameraPermissionState.status.isGranted) {
                                            if (uiState.isTimerEnabled && uiState.timerSeconds > 0) {
                                                scope.launch {
                                                    isCountingDown = true
                                                    for (i in uiState.timerSeconds downTo 1) {
                                                        countdownValue = i
                                                        delay(1000)
                                                    }
                                                    isCountingDown = false
                                                    textToSpeech?.speak("foto", TextToSpeech.QUEUE_FLUSH, null, null)
                                                    captureFlashTrigger += 1
                                                    viewModel.triggerImageCapture()
                                                }
                                            } else {
                                                captureFlashTrigger += 1
                                                viewModel.triggerImageCapture()
                                            }
                                        } else {
                                            cameraPermissionState.launchPermissionRequest()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(92.dp)
                                        .semantics {
                                            contentDescription = context.getString(R.string.take_photo)
                                            if (uiState.isLoading) {
                                                stateDescription = context.getString(R.string.loading_description)
                                            }
                                        },
                                    enabled = !uiState.isLoading && !isCountingDown,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (captureFlashAlpha.value > 0f) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer {
                                                        alpha = captureFlashAlpha.value
                                                        scaleX = 1f + (1f - captureFlashAlpha.value) * 0.8f
                                                        scaleY = 1f + (1f - captureFlashAlpha.value) * 0.8f
                                                    },
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            ) {}
                                            Icon(
                                                Icons.Default.FlashOn,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .graphicsLayer {
                                                        alpha = captureFlashAlpha.value
                                                        scaleX = 0.7f + (1f - captureFlashAlpha.value) * 0.8f
                                                        scaleY = 0.7f + (1f - captureFlashAlpha.value) * 0.8f
                                                    },
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                        Icon(
                                            Icons.Default.PhotoCamera,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                // Botón de Flash - a la derecha
                                val focusAssistOffDesc = stringResource(R.string.focus_assist_off)
                                val focusAssistOnDesc = stringResource(R.string.focus_assist_on)
                                val focusAssistAutoCaptureDesc = stringResource(R.string.focus_assist_auto_capture)
                                val focusAssistDesc = when (uiState.focusAssistMode) {
                                    FocusAssistMode.ON -> focusAssistOnDesc
                                    FocusAssistMode.AUTO_CAPTURE -> focusAssistAutoCaptureDesc
                                    FocusAssistMode.OFF -> focusAssistOffDesc
                                }
                                val currentFlashDesc = when (uiState.flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> flashOnDesc
                                    ImageCapture.FLASH_MODE_OFF -> flashOffDesc
                                    else -> flashAutoDesc
                                }
                                Column(
                                    modifier = Modifier.width(72.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledIconButton(
                                        onClick = { viewModel.toggleFocusAssist() },
                                        enabled = !uiState.isLoading && !isCountingDown,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics {
                                                contentDescription = focusAssistDesc
                                                stateDescription = focusAssistDesc
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CenterFocusStrong,
                                                contentDescription = null
                                            )
                                            if (uiState.focusAssistMode == FocusAssistMode.AUTO_CAPTURE) {
                                                Text(
                                                    text = stringResource(R.string.focus_assist_auto_capture_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            CircleShape
                                                        )
                                                        .padding(horizontal = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    FilledIconButton(
                                        onClick = { viewModel.toggleFlashMode() },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics { contentDescription = currentFlashDesc }
                                    ) {
                                        Icon(
                                            imageVector = when (uiState.flashMode) {
                                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                                ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                                                else -> Icons.Default.FlashAuto
                                            },
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                            if (uiState.showPrePromptInput) {
                                OutlinedTextField(
                                    value = uiState.prePromptText,
                                    onValueChange = { viewModel.onPrePromptTextChanged(it) },
                                    label = { Text(stringResource(R.string.pre_prompt_hint)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp, max = 112.dp),
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
                is HomeScreenState.ImageCaptured -> {
                    ChatSection(
                        capturedImage = uiState.currentImageBitmap,
                        imageUri = uiState.currentImageUri,
                        chatMessages = uiState.chatMessages,
                        isAiTyping = uiState.isAiTyping,
                        isLoading = uiState.isLoading,
                        onSaveImage = { bitmap -> viewModel.saveImageToGallery(bitmap) },
                        onShareImage = { uri ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "Image", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_image)).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(chooser)
                        },
                        onCopyImage = { uri ->
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newUri(context.contentResolver, "Image", uri)
                            clipboardManager.setPrimaryClip(clipData)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.image_copied_to_clipboard),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        onSendMessage = { message -> viewModel.sendChatMessage(message) },
                        isTranscribingAudio = uiState.isTranscribingAudio,
                        onVoiceMessageReady = { recording -> viewModel.transcribeVoiceAndSend(context, recording) },
                        textToSpeech = textToSpeech,
                        ttsInitialized = ttsInitialized,
                        writeStoragePermissionState = writeStoragePermissionState,
                        onError = { message -> viewModel.setError(message) },
                        selectedImageUris = uiState.selectedImageUris,
                        onAddImageRequest = { viewModel.onAddImageRequest() },
                        onRemoveSelectedImage = { uri -> viewModel.removeSelectedImage(uri) }
                    )
                }
            }
        }
    }

    if (uiState.showApiUsageWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.onApiUsageWarningDismissed() },
            title = { Text(stringResource(R.string.api_usage_warning_title)) },
            text = { Text(stringResource(R.string.api_usage_warning_message)) },
            confirmButton = {
                Button(onClick = { viewModel.onApiUsageWarningDismissed() }) {
                    Text(stringResource(R.string.ok_understood))
                }
            }
        )
    }

    if (uiState.showAddImageDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onAddImageDialogDismissed() },
            title = { Text(stringResource(R.string.add_image_dialog_title)) },
            text = { Text(stringResource(R.string.select_images_limit_dynamic, uiState.maxImagesInChat)) },
            confirmButton = {
                Button(onClick = {
                    multipleGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    viewModel.onAddImageDialogDismissed()
                }) {
                    Text(stringResource(R.string.select_from_gallery))
                }
            },
            dismissButton = {
                Button(onClick = { 
                    val tmpUri = viewModel.getTmpFileUri()
                    tempUri = tmpUri
                    cameraLauncher.launch(tmpUri)
                    viewModel.onAddImageDialogDismissed()
                 }) {
                    Text(stringResource(R.string.take_photo))
                }
            }
        )
    }

    uiState.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.error_dialog_title)) },
            text = { Text(errorMessage) },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(errorMessage))
                        }
                    ) {
                        Text(stringResource(R.string.error_dialog_copy))
                    }
                    Button(
                        onClick = { viewModel.retryLastAction() }
                    ) {
                        Text(stringResource(R.string.retry_action))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRetry() }) {
                    Text(stringResource(R.string.error_dialog_close))
                }
            }
        )
    }
}

@Composable
fun RatingBanner(
    onRateNow: () -> Unit,
    onRateLater: () -> Unit
) {
    val context = LocalContext.current
    var isRequestingReview by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.rating_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRateLater,
                    enabled = !isRequestingReview,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(text = stringResource(id = R.string.rating_banner_later))
                }
                Button(
                    enabled = !isRequestingReview,
                    onClick = {
                        isRequestingReview = true
                        val activity = context.findActivity()
                        if (activity == null) {
                            openPlayStoreListing(context)
                            onRateNow()
                            isRequestingReview = false
                            return@Button
                        }

                        val manager = ReviewManagerFactory.create(context)
                        val request = manager.requestReviewFlow()
                        request.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val reviewInfo = task.result
                                val flow = manager.launchReviewFlow(activity, reviewInfo)
                                flow.addOnCompleteListener { _ ->
                                    onRateNow()
                                    isRequestingReview = false
                                }
                            } else {
                                openPlayStoreListing(context)
                                onRateNow()
                                isRequestingReview = false
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(id = R.string.rating_banner_rate))
                }
            }
        }
    }
}

@Composable
fun DonationBanner(
    onDonateNow: () -> Unit,
    onDonateLater: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.donation_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDonateLater,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(text = stringResource(id = R.string.donation_banner_later))
                }
                Button(
                    onClick = onDonateNow
                ) {
                    Text(text = stringResource(id = R.string.donation_banner_donate))
                }
            }
        }
    }
}
