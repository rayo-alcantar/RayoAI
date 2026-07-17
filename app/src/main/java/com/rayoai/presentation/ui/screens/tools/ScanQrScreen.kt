@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rayoai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private enum class QrSource { CAMERA, IMAGE }

@Composable
fun ScanQrScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { createQrScanner() }
    var source by remember { mutableStateOf<QrSource?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            source = null
            return@rememberLauncherForActivityResult
        }
        isLoading = true
        status = null
        scope.launch {
            try {
                val value = withContext(Dispatchers.Default) {
                    scanner.process(InputImage.fromFilePath(context, uri)).await()
                        .firstNotNullOfOrNull { it.rawValue }
                }
                if (value == null) {
                    status = context.getString(R.string.scan_qr_not_found)
                } else {
                    result = value
                    vibrateQrDetected(context)
                }
            } catch (_: Exception) {
                status = context.getString(R.string.scan_qr_error)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_qr_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.scan_pdf_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (source) {
                QrSource.CAMERA -> QrCameraPreview(
                    scanner = scanner,
                    onQrDetected = { value ->
                        result = value
                        vibrateQrDetected(context)
                    }
                )
                QrSource.IMAGE, null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                    }
                    status?.let {
                        Text(
                            text = it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Button(onClick = {
                        status = null
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text(stringResource(R.string.scan_qr_select_another))
                    }
                }
            }
        }
    }

    if (source == null) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { Text(stringResource(R.string.scan_qr_source_title)) },
            confirmButton = {
                TextButton(onClick = {
                    source = QrSource.IMAGE
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Text(stringResource(R.string.scan_qr_from_image))
                }
            },
            dismissButton = {
                TextButton(onClick = { source = QrSource.CAMERA }) {
                    Text(stringResource(R.string.scan_qr_from_camera))
                }
            }
        )
    }

    result?.let { value ->
        QrResultDialog(
            value = value,
            onFinished = onNavigateBack
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrCameraPreview(scanner: BarcodeScanner, onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val cameraController = remember { LifecycleCameraController(context) }
    val analyzer = remember(scanner, onQrDetected) { QrCameraAnalyzer(scanner, onQrDetected) }

    DisposableEffect(cameraController, analyzer) {
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        cameraController.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
        }
    }

    if (permissionState.status.isGranted) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PreviewView(it).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                        cameraController.bindToLifecycle(lifecycleOwner)
                    }
                }
            )
            Text(
                text = stringResource(R.string.scan_qr_camera_hint),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text(stringResource(R.string.camera_permission_request))
            }
        }
    }
}

private class QrCameraAnalyzer(
    private val scanner: BarcodeScanner,
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val isProcessing = AtomicBoolean(false)
    private val hasDetectedQr = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (hasDetectedQr.get() || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                codes.firstNotNullOfOrNull { it.rawValue }?.let { value ->
                    if (hasDetectedQr.compareAndSet(false, true)) onQrDetected(value)
                }
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }
}

@Composable
private fun QrResultDialog(value: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    val canOpen = remember(value) { isOpenableWebLink(value) }
    AlertDialog(
        onDismissRequest = onFinished,
        title = { Text(stringResource(R.string.scan_qr_result_title)) },
        text = { Text(stringResource(R.string.scan_qr_result_message, value)) },
        confirmButton = {
            TextButton(onClick = {
                copyQrValue(context, value)
                onFinished()
            }) {
                Text(stringResource(R.string.scan_qr_copy))
            }
        },
        dismissButton = {
            if (canOpen) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                    onFinished()
                }) {
                    Text(stringResource(R.string.scan_qr_open))
                }
            }
        }
    )
}

private fun createQrScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
)

private fun isOpenableWebLink(value: String): Boolean {
    val uri = Uri.parse(value)
    return uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)
}

private fun copyQrValue(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QR", value))
}

private fun vibrateQrDetected(context: Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
}
