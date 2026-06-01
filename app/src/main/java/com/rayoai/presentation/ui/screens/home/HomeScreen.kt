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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.rayoai.R
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
import com.rayoai.presentation.ui.findActivity
import com.rayoai.presentation.ui.openPlayStoreListing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController,
    imageUri: Uri? = null,
    captureId: Long? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(imageUri) {
        imageUri?.let {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            } catch (e: SecurityException) {
                Log.e("HomeScreen", "SecurityException loading shared image - permission may have expired", e)
                viewModel.setError(context.getString(R.string.error_security_shared_image))
                null
            } catch (e: java.io.FileNotFoundException) {
                Log.e("HomeScreen", "FileNotFoundException loading shared image - file may have been deleted", e)
                viewModel.setError(context.getString(R.string.error_loading_shared_image))
                null
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error loading shared image", e)
                viewModel.setError(context.getString(R.string.error_loading_shared_image))
                null
            }
            bitmap?.let { img -> viewModel.describeImage(img) }
        }
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val writeStoragePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
) { uri: Uri? ->
    uri?.let {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            }
            viewModel.processGalleryImage(bitmap)
        } catch (e: Exception) {
            Log.e("HomeScreen", "Error loading gallery image", e)
            viewModel.setError(context.getString(R.string.error_loading_gallery_image))
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

    var textToSpeech: TextToSpeech? = null
    var ttsInitialized by remember { mutableStateOf(false) }
    var focusAssistAnnouncement by remember { mutableStateOf("") }

    LaunchedEffect(focusAssistAnnouncement) {
        if (focusAssistAnnouncement.isNotBlank()) {
            view.announceForAccessibility(focusAssistAnnouncement)
        }
    }

    DisposableEffect(context) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("es", "ES"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("HomeScreen", "TTS language not supported or missing data.")
                    viewModel.setError(context.getString(R.string.tts_lang_not_supported))
                } else {
                    ttsInitialized = true
                }
            } else {
                Log.e("HomeScreen", "TTS initialization failed with status: $status")
                viewModel.setError(context.getString(R.string.tts_init_error))
            }
        }

        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
    }

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
            if (uiState.showDonationBanner) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .padding(top = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
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

                            Image(
                                painter = painterResource(id = R.drawable.logo_rayoai),
                                contentDescription = stringResource(id = R.string.app_name),
                                modifier = Modifier.size(52.dp)
                            )

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

                        if (cameraPermissionState.status.isGranted) {
                            CameraView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                onImageCaptured = { bitmap ->
                                    viewModel.describeImage(bitmap)
                                },
                                onError = { errorMessage ->
                                    viewModel.setError(errorMessage)
                                    viewModel.setCapturing(false) // nuevo
                                },
                                isCapturing = uiState.isCapturing,
                                cameraSelector = uiState.currentCameraSelector,
                                flashMode = uiState.flashMode,
                                isFocusAssistEnabled = uiState.isFocusAssistEnabled &&
                                    !uiState.isLoading &&
                                    !uiState.isCapturing,
                                onFocusAssistAnnouncement = { focusAssistAnnouncement = it }
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
                                        modifier = Modifier
                                            .size(52.dp)
                                            .semantics {
                                                contentDescription = timerDesc
                                                stateDescription = timerDesc
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
                                val focusAssistDesc = if (uiState.isFocusAssistEnabled) {
                                    stringResource(R.string.focus_assist_on)
                                } else {
                                    stringResource(R.string.focus_assist_off)
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
                                        Icon(
                                            imageVector = Icons.Default.CenterFocusStrong,
                                            contentDescription = null
                                        )
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
                            val shareIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, uri)
                                type = "image/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_image)))
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
