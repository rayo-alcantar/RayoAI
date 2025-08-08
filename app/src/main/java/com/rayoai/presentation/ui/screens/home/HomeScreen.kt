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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
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
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(imageUri) {
        imageUri?.let {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error loading shared image", e)
                viewModel.setError(context.getString(R.string.error_loading_shared_image))
                null
            }
            bitmap?.let { img -> viewModel.describeImage(img) }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(message = "Error: $it")
            viewModel.clearError()
        }
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val readStoragePermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val writeStoragePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Take persistent read permission for the URI
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)

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
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                navigationIcon = {
                    if (uiState.screenState is HomeScreenState.ImageCaptured) {
                        BackHandler {
                            viewModel.resetHomeScreenState()
                        }
                        IconButton(onClick = { viewModel.resetHomeScreenState() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                }
            )
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
            when (uiState.screenState) {
                HomeScreenState.Initial -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (cameraPermissionState.status.isGranted) {
                            CameraView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                onImageCaptured = { bitmap ->
                                    viewModel.describeImage(bitmap)
                                    viewModel.setLoading(false)
                                },
                                onError = { errorMessage ->
                                    viewModel.setError(errorMessage)
                                    viewModel.setLoading(false)
                                },
                                isCapturing = uiState.isLoading,
                                cameraSelector = uiState.currentCameraSelector
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
                                .padding(top = 16.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.toggleCamera()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text(
                                    if (uiState.currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                        stringResource(R.string.switch_to_front_camera)
                                    else
                                        stringResource(R.string.switch_to_back_camera),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Button(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text(stringResource(R.string.load_from_gallery), style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = uiState.isTimerEnabled,
                                        onCheckedChange = { viewModel.setTimerEnabled(it) }
                                    )
                                    Text(stringResource(R.string.timer), style = MaterialTheme.typography.titleMedium)
                                }

                                if (uiState.isTimerEnabled) {
                                    var expanded by remember { mutableStateOf(false) }
                                    val timerOptions = listOf(2, 3, 5, 10, 15)
                                    val selectedOptionText = if (uiState.timerSeconds > 0) "${uiState.timerSeconds}s" else stringResource(R.string.select)

                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = selectedOptionText,
                                            onValueChange = { },
                                            readOnly = true,
                                            label = { Text(stringResource(R.string.seconds)) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            timerOptions.forEach { selectionOption ->
                                                DropdownMenuItem(
                                                    text = { Text(selectionOption.toString()) },
                                                    onClick = {
                                                        viewModel.setTimerSeconds(selectionOption)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            var isCountingDown by remember { mutableStateOf(false) }
                            var countdownValue by remember { mutableStateOf(0) }

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
                                                viewModel.triggerImageCapture()
                                            }
                                        } else {
                                            viewModel.triggerImageCapture()
                                        }
                                    } else {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .semantics {
                                        if (uiState.isLoading) {
                                            stateDescription = context.getString(R.string.loading_description)
                                        }
                                    },
                                enabled = !uiState.isLoading && !isCountingDown
                            ) {
                                Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.titleMedium)
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
