package com.rayoai.presentation.ui.components

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.widget.LinearLayout
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

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
    cameraSelector: CameraSelector,
    flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    isFocusAssistEnabled: Boolean = false,
    onFocusAssistAnnouncement: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Controlador de la cámara que gestiona el ciclo de vida y las operaciones de la cámara.
    val cameraController = remember { LifecycleCameraController(context) }
    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
            .build()
        )
    }
    val focusAssistAnalyzer = remember(faceDetector, onFocusAssistAnnouncement) {
        FocusAssistAnalyzer(faceDetector, onFocusAssistAnnouncement)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            faceDetector.close()
        }
    }
    LaunchedEffect(cameraController, cameraSelector, flashMode) {
        cameraController.cameraSelector = cameraSelector
        cameraController.imageCaptureFlashMode = flashMode
    }
    LaunchedEffect(cameraController, isFocusAssistEnabled, focusAssistAnalyzer) {
        if (isFocusAssistEnabled) {
            cameraController.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            cameraController.setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                focusAssistAnalyzer
            )
        } else {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            focusAssistAnalyzer.reset()
        }
    }
    // Estado del permiso de la cámara utilizando Accompanist Permissions.
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    LaunchedEffect(isCapturing) {
        Log.d("CameraView", "isCapturing changed to: $isCapturing")
        if (isCapturing) {
            captureImage(cameraController, context, onImageCaptured, onError)
        }
    }

    Box(modifier = modifier) {
        // Si el permiso de la cámara está concedido, mostrar la vista previa y el botón de captura.
        if (permissionState.status.isGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    // Crear y configurar la vista previa de CameraX.
                    PreviewView(it).apply {
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                        // Vincular el controlador de la cámara al ciclo de vida del Composable.
                        cameraController.bindToLifecycle(lifecycleOwner)
                    }
                }
            )
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
    Log.d("CameraView", "Attempting to capture image...")
    try {
        cameraController.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.d("CameraView", "onCaptureSuccess: Image captured")
                    val bitmap = image.toBitmap()
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
                    onImageCaptured(rotatedBitmap)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraView", "Error capturing image: ", exception)
                    onError(exception.localizedMessage ?: "Error al capturar la imagen")
                }
            }
        )
    } catch (e: IllegalStateException) {
        Log.e("CameraView", "Failed to capture image, camera not ready or closed.", e)
        onError("La cámara no está lista. Inténtalo de nuevo.")
    }
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

private class FocusAssistAnalyzer(
    private val faceDetector: FaceDetector,
    private val onAnnouncement: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val isAnalyzing = AtomicBoolean(false)
    private var lastAnalysisTime = 0L
    private var lastAnnouncement = ""
    private var lastAnnouncementTime = 0L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS || !isAnalyzing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isAnalyzing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val faceTask = faceDetector.process(image)

        faceTask
            .addOnSuccessListener { faces ->
                buildAnnouncement(faces, image.width)?.let { announcement ->
                    announceIfNeeded(announcement)
                }
            }
            .addOnCompleteListener {
                isAnalyzing.set(false)
                imageProxy.close()
            }
    }

    fun reset() {
        lastAnnouncement = ""
        lastAnalysisTime = 0L
        lastAnnouncementTime = 0L
        isAnalyzing.set(false)
    }

    private fun buildAnnouncement(faces: List<Face>, imageWidth: Int): String? {
        if (faces.size >= 2) {
            return "${faces.size} rostros detectados."
        }
        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return null
        return "Rostro ${horizontalPosition(largestFace.boundingBox, imageWidth)}."
    }

    private fun announceIfNeeded(announcement: String) {
        val announceNow = System.currentTimeMillis()
        if (
            announcement != lastAnnouncement ||
            announceNow - lastAnnouncementTime >= REPEAT_ANNOUNCEMENT_INTERVAL_MS
        ) {
            lastAnnouncement = announcement
            lastAnnouncementTime = announceNow
            onAnnouncement(announcement)
        }
    }

    private fun horizontalPosition(bounds: Rect, imageWidth: Int): String {
        if (imageWidth <= 0) return "al centro"
        val center = bounds.centerX().toFloat() / imageWidth.toFloat()
        return when {
            center < 0.38f -> "a la izquierda"
            center > 0.62f -> "a la derecha"
            else -> "al centro"
        }
    }

    private companion object {
        private const val ANALYSIS_INTERVAL_MS = 900L
        private const val REPEAT_ANNOUNCEMENT_INTERVAL_MS = 6000L
    }
}
