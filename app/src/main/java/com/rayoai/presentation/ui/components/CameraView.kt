package com.rayoai.presentation.ui.components

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.LaunchedEffect
import android.widget.LinearLayout
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Composable que muestra una vista previa de la cámara y permite capturar imágenes.
 * Gestiona los permisos de la cámara y la integración con CameraX.
 * @param modifier Modificador para aplicar al Composable.
 * @param onImageCaptured Callback que se invoca cuando se captura una imagen exitosamente, proporcionando el [Bitmap] de la imagen.
 * @param onError Callback que se invoca si ocurre un error durante la operación de la cámara, proporcionando un mensaje de error.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    isCapturing: Boolean,
    cameraSelector: CameraSelector
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Controlador de la cámara que gestiona el ciclo de vida y las operaciones de la cámara.
    val cameraController = remember { LifecycleCameraController(context) }
    LaunchedEffect(cameraController, cameraSelector) {
        cameraController.cameraSelector = cameraSelector
        cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO
    }
    // Estado del permiso de la cámara utilizando Accompanist Permissions.
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Box(modifier = modifier) {
        // Si el permiso de la cámara está concedido, mostrar la vista previa y el botón de captura.
        if (permissionState.status.isGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    // Crear y configurar la vista previa de CameraX.
                    PreviewView(it).apply {
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        controller = cameraController
                        // Vincular el controlador de la cámara al ciclo de vida del Composable.
                        cameraController.bindToLifecycle(lifecycleOwner)
                    }
                }
            )
            // Botón para capturar la imagen.
            IconButton(
                onClick = {
                    captureImage(cameraController, context, onImageCaptured, onError)
                },
                enabled = !isCapturing,
                modifier = Modifier
                    .align(Alignment.BottomCenter) // Alinear en la parte inferior central.
                    .padding(16.dp)
                    .semantics { contentDescription = "Tomar foto" } // contentDescription en el IconButton via semantics
            ) {
                Icon(Icons.Default.Camera, contentDescription = null) // contentDescription nulo en el Icon
            }
        } else {
            // Si el permiso no está concedido, mostrar un botón para solicitarlo.
            Button(
                onClick = { permissionState.launchPermissionRequest() },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("Solicitar Permiso de Cámara")
            }
        }
    }
}

/**
 * Función auxiliar para capturar una imagen utilizando el controlador de la cámara.
 * @param cameraController El [LifecycleCameraController] para realizar la captura.
 * @param context El contexto de la aplicación.
 * @param onImageCaptured Callback para la imagen capturada exitosamente.
 * @param onError Callback para errores durante la captura.
 */
private fun captureImage(
    cameraController: LifecycleCameraController,
    context: Context,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    cameraController.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Convertir ImageProxy a Bitmap.
                val bitmap = image.toBitmap()
                // Obtener la rotación de la imagen y rotar el Bitmap si es necesario.
                val rotationDegrees = image.imageInfo.rotationDegrees
                val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
                onImageCaptured(rotatedBitmap)
                image.close() // Es importante cerrar la ImageProxy.
            }

            override fun onError(exception: ImageCaptureException) {
                // Registrar el error y notificar a través del callback onError.
                Log.e("CameraView", "Error capturing image: ", exception)
                onError(exception.localizedMessage ?: "Error al capturar la imagen")
            }
        }
    )
}

/**
 * Rota un [Bitmap] dado un número de grados.
 * @param bitmap El [Bitmap] a rotar.
 * @param rotationDegrees Los grados de rotación (ej. 90, 180, 270).
 * @return El [Bitmap] rotado.
 */
private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}