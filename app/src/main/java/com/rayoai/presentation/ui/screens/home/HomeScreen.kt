package com.rayoai.presentation.ui.screens.home

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
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

    // Procesar la imagen compartida si se recibe una URI.
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
    val readStoragePermissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    val writeStoragePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            } catch (e: Exception) {
                viewModel.setError(context.getString(R.string.error_loading_gallery_image))
                null
            }
            bitmap?.let { img -> viewModel.processGalleryImage(img) }
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
                    viewModel.setError(context.getString(R.string.tts_lang_not_supported))
                } else {
                    ttsInitialized = true
                }
            } else {
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
                    // No hay acciones en la barra superior de la pantalla de inicio.
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
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
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || readStoragePermissionState.status.isGranted) {
                                        galleryLauncher.launch("image/*")
                                    } else {
                                        readStoragePermissionState.launchPermissionRequest()
                                    }
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

                            if (uiState.isCountingDown) {
                                var countdownValue by remember { mutableStateOf(uiState.timerSeconds) }
                                LaunchedEffect(uiState.isCountingDown) {
                                    if (uiState.isCountingDown) {
                                        textToSpeech?.let { tts ->
                                            viewModel.countdownTrigger.collect { value ->
                                                countdownValue = value
                                                tts.speak(value.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
                                            }
                                        }
                                    } else if (ttsInitialized && countdownValue == 0) {
                                        textToSpeech?.speak("foto", TextToSpeech.QUEUE_FLUSH, null, null)
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.capturing_in, countdownValue),
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }

                            Button(
                                onClick = {
                                    if (cameraPermissionState.status.isGranted) {
                                        viewModel.triggerImageCapture()
                                    } else {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = !uiState.isLoading
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
                        onError = { message -> viewModel.setError(message) }
                    )
                }
            }
        }
    }
}
