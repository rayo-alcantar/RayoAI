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
import androidx.compose.runtime.* // Importar todos los composables de runtime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
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
import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Composable principal para la pantalla de inicio de la aplicación.
 * Muestra la vista de la cámara, el historial de chat y el campo de entrada para el chat.
 * También maneja la navegación a la pantalla de ajustes y la selección de imágenes de la galería.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
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
                viewModel.setError("Error al cargar la imagen de la galería.")
                null
            }
            bitmap?.let { img -> viewModel.processGalleryImage(img) }
        }
    }

    

    var textToSpeech: TextToSpeech? = null
    var ttsInitialized by remember { mutableStateOf(false) }


DisposableEffect(context) {
    textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                viewModel.setError("Idioma no soportado para Text-to-Speech.")
            } else {
                ttsInitialized = true
            }
        } else {
            viewModel.setError("Error al inicializar Text-to-Speech.")
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
                        IconButton(onClick = { viewModel.resetHomeScreenState() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
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
                    // UI para la selección de cámara/galería y permisos
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        

                        // Botón para cargar de la galería
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || readStoragePermissionState.status.isGranted) {
                                    galleryLauncher.launch("image/*")
                                } else {
                                    readStoragePermissionState.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cargar de la galería")
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón para solicitar permisos (solo si no están concedidos)
                        if (!cameraPermissionState.status.isGranted || !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || readStoragePermissionState.status.isGranted)) {
                            Button(
                                onClick = {
                                    if (!cameraPermissionState.status.isGranted) {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                    if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || readStoragePermissionState.status.isGranted)) {
                                        readStoragePermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Solicitar Permisos")
                            }
                        }

                        // Vista de la cámara (oculta hasta que se tome una foto)
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
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Se requiere permiso de cámara para tomar fotografías.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
                is HomeScreenState.ImageCaptured -> {
                    // UI para la imagen capturada y el chat
                    val capturedImage = uiState.currentImageBitmap
                    val description = uiState.currentImageDescription
                    val imageUri = uiState.currentImageUri

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Imagen capturada
                        capturedImage?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Imagen capturada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Opciones de la imagen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leer descripción (TTS)
                            IconButton(
    onClick = {
        description?.let {
            textToSpeech?.speak(it, TextToSpeech.QUEUE_FLUSH, null, null)
        } ?: viewModel.setError("No hay descripción para leer.")
    },
    enabled = !description.isNullOrBlank() && ttsInitialized
) {
    Icon(Icons.Default.SpeakerPhone, contentDescription = "Leer descripción")
}

                            // Copiar descripción
                            IconButton(
                                onClick = {
                                    description?.let {
                                        ContextCompat.getSystemService(context, ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Descripción de imagen", it))
                                    } ?: viewModel.setError("No hay descripción para copiar.")
                                },
                                enabled = !description.isNullOrBlank()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar descripción")
                            }

                            // Guardar imagen
                            IconButton(
                                onClick = {
                                    capturedImage?.let {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || writeStoragePermissionState.status.isGranted) {
                                            // La imagen ya se guarda en describeImage, esto sería para un guardado explícito si se desea
                                            // viewModel.saveImageToDevice(it) // Si se implementa una función de guardado explícito
                                            scope.launch {
                                                snackbarHostState.showSnackbar(message = "La imagen ya ha sido guardada.")
                                            }
                                        } else {
                                            writeStoragePermissionState.launchPermissionRequest()
                                        }
                                    } ?: viewModel.setError("No hay imagen para guardar.")
                                },
                                enabled = capturedImage != null
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Guardar imagen")
                            }

                            // Compartir imagen
                            IconButton(
                                onClick = {
                                    imageUri?.let { uri ->
                                        val shareIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            type = "image/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir imagen"))
                                    } ?: viewModel.setError("No hay imagen para compartir.")
                                },
                                enabled = imageUri != null
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Compartir imagen")
                            }
                        }

                        // Zona de Chat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.semantics {
                                        contentDescription = "Analizando imagen. Por favor, espere."
                                    }
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.chatMessages) { message ->
                                    ChatBubble(message = message)
                                }
                            }
                        }

                        // Campo de entrada de chat
                        var chatInput by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                label = { Text("Pregunta sobre la imagen...") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSend = {
                                        if (chatInput.isNotBlank()) {
                                            viewModel.sendChatMessage(chatInput)
                                            chatInput = ""
                                        }
                                    }
                                ),
                                maxLines = 5 // Multi-línea
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                viewModel.sendChatMessage(chatInput)
                                chatInput = ""
                            }, enabled = chatInput.isNotBlank()) {
                                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar mensaje")
                            }
                        }
                    }
                }
            }
        }
    }
}