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
import androidx.compose.ui.res.stringResource
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
import com.rayoai.R
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
    onFocusAssistAnnouncement: (String) -> Unit = {},
    onFocusAssistCentered: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val requestCameraPermission = stringResource(R.string.camera_permission_request)
    val captureError = stringResource(R.string.camera_capture_error)
    val cameraNotReady = stringResource(R.string.camera_not_ready)
    val facesDetectedTemplate = stringResource(R.string.focus_assist_faces_detected)
    val faceDetectedTemplate = stringResource(R.string.focus_assist_face_detected)
    val faceLeft = stringResource(R.string.focus_assist_position_left)
    val faceCenter = stringResource(R.string.focus_assist_position_center)
    val faceRight = stringResource(R.string.focus_assist_position_right)
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
    val focusAssistAnalyzer = remember(
        faceDetector,
        onFocusAssistAnnouncement,
        facesDetectedTemplate,
        faceDetectedTemplate,
        faceLeft,
        faceCenter,
        faceRight,
        onFocusAssistCentered
    ) {
        FocusAssistAnalyzer(
            faceDetector = faceDetector,
            onAnnouncement = onFocusAssistAnnouncement,
            onCentered = onFocusAssistCentered,
            facesDetectedTemplate = facesDetectedTemplate,
            faceDetectedTemplate = faceDetectedTemplate,
            leftLabel = faceLeft,
            centerLabel = faceCenter,
            rightLabel = faceRight
        )
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
            captureImage(cameraController, context, onImageCaptured, onError, captureError, cameraNotReady)
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
                Text(requestCameraPermission)
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
    onError: (String) -> Unit,
    captureError: String,
    cameraNotReady: String
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
                    onError(exception.localizedMessage ?: captureError)
                }
            }
        )
    } catch (e: IllegalStateException) {
        Log.e("CameraView", "Failed to capture image, camera not ready or closed.", e)
        onError(cameraNotReady)
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
    private val onAnnouncement: (String) -> Unit,
    private val onCentered: () -> Unit,
    private val facesDetectedTemplate: String,
    private val faceDetectedTemplate: String,
    private val leftLabel: String,
    private val centerLabel: String,
    private val rightLabel: String
) : ImageAnalysis.Analyzer {

    private val isAnalyzing = AtomicBoolean(false)
    private var lastAnalysisTime = 0L
    private var lastAnnouncement = ""
    private var lastAnnouncementTime = 0L
    private var hasTriggeredCentered = false

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
                notifyCenteredIfNeeded(faces, image.width)
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
        hasTriggeredCentered = false
        isAnalyzing.set(false)
    }

    private fun buildAnnouncement(faces: List<Face>, imageWidth: Int): String? {
        if (faces.size >= 2) {
            return facesDetectedTemplate.format(faces.size)
        }
        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return null
        return faceDetectedTemplate.format(horizontalPosition(largestFace.boundingBox, imageWidth))
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
        if (imageWidth <= 0) return centerLabel
        val center = bounds.centerX().toFloat() / imageWidth.toFloat()
        return when {
            center < 0.38f -> leftLabel
            center > 0.62f -> rightLabel
            else -> centerLabel
        }
    }

    private fun notifyCenteredIfNeeded(faces: List<Face>, imageWidth: Int) {
        if (!hasTriggeredCentered && isCentered(faces, imageWidth)) {
            hasTriggeredCentered = true
            onCentered()
        }
    }

    private fun isCentered(faces: List<Face>, imageWidth: Int): Boolean {
        if (faces.isEmpty() || imageWidth <= 0) return false
        val centerX = if (faces.size == 1) {
            faces.first().boundingBox.centerX().toFloat()
        } else {
            val left = faces.minOf { it.boundingBox.left }
            val right = faces.maxOf { it.boundingBox.right }
            (left + right) / 2f
        }
        val normalizedCenter = centerX / imageWidth.toFloat()
        return normalizedCenter in 0.38f..0.62f
    }

    private companion object {
        private const val ANALYSIS_INTERVAL_MS = 900L
        private const val REPEAT_ANNOUNCEMENT_INTERVAL_MS = 6000L
    }
}
