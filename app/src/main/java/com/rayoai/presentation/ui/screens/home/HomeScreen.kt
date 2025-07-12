package com.rayoai.presentation.ui.screens.home

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.* // Importar todos los composables de runtime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Recolecta el estado de la UI del ViewModel.
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Estado para mostrar SnackBar (mensajes temporales en la parte inferior de la pantalla).
    val snackbarHostState = remember { SnackbarHostState() }
    // Efecto lanzado cuando el estado de error de la UI cambia.
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(message = "Error: $it")
            viewModel.clearError() // Limpiar el error después de mostrarlo para evitar repeticiones.
        }
    }

    // Estado del permiso de lectura de almacenamiento externo para la galería.
    val readStoragePermissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    // Launcher para seleccionar contenido de la galería.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Convertir la URI seleccionada a Bitmap.
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Para Android P (API 28) y superiores, usar ImageDecoder.
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    // Para versiones anteriores, usar MediaStore.
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            } catch (e: Exception) {
                // Manejar errores al cargar la imagen de la galería.
                viewModel.setError("Error al cargar la imagen de la galería.")
                null
            }
            // Si el bitmap se obtiene correctamente, procesarlo con el ViewModel.
            bitmap?.let { img -> viewModel.processGalleryImage(img) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Host para mostrar SnackBar.
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
                    // Botón para abrir la galería.
                    IconButton(onClick = {
                        // Verificar la versión de Android y el estado del permiso.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || readStoragePermissionState.status.isGranted) {
                            galleryLauncher.launch("image/*") // Lanzar el selector de galería.
                        }
                        else {
                            readStoragePermissionState.launchPermissionRequest() // Solicitar permiso si es necesario.
                        }
                    }) {
                        Icon(Icons.Default.Image, contentDescription = "Seleccionar de Galería")
                    }
                    // Botón para navegar a la pantalla de ajustes.
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Aplicar padding de la barra superior.
                .fillMaxSize()
        ) {
            // 1. Vista de Cámara y Controles
            CameraView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onImageCaptured = { bitmap ->
                    viewModel.describeImage(bitmap) // Llamar al ViewModel para describir la imagen.
                },
                onError = { errorMessage ->
                    viewModel.setError(errorMessage) // Propagar errores de la cámara al ViewModel.
                }
            )

            // 2. Zona de Chat
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Mostrar indicador de carga si el ViewModel está procesando.
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Analizando imagen. Por favor, espere."
                        }
                    )
                }

                // Lista de mensajes de chat.
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
            var chatInput by remember { mutableStateOf("") } // Estado del texto de entrada.
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
                    modifier = Modifier.weight(1f), // Ocupa el espacio restante.
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Botón de enviar mensaje.
                IconButton(onClick = {
                    viewModel.sendChatMessage(chatInput)
                    chatInput = "" // Limpiar el campo de entrada.
                }, enabled = chatInput.isNotBlank()) { // Habilitado solo si hay texto.
                    Icon(Icons.Default.Send, contentDescription = "Enviar mensaje")
                }
            }
        }
    }
}