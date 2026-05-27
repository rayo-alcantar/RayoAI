@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.domain.model.PdfDocument
import com.rayoai.domain.model.VideoDocument
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LightLevel(val label: String) {
    UNAVAILABLE("Sensor de luz no disponible"),
    NONE("Ausencia de luz"),
    LOW("Intensidad baja"),
    MEDIUM("Intensidad media"),
    HIGH("Intensidad alta")
}

private fun lightLevelForLux(lux: Float?): LightLevel {
    return when {
        lux == null -> LightLevel.UNAVAILABLE
        lux < 1f -> LightLevel.NONE
        lux < 50f -> LightLevel.LOW
        lux < 500f -> LightLevel.MEDIUM
        else -> LightLevel.HIGH
    }
}

@Composable
private fun LightDetectorCard() {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val lightSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }
    val vibrator = remember { context.appVibrator() }

    var isDetecting by remember { mutableStateOf(false) }
    var lux by remember { mutableStateOf<Float?>(null) }
    val level = if (lightSensor == null) LightLevel.UNAVAILABLE else lightLevelForLux(lux)
    val buttonText = if (isDetecting) "Detectando... tocar para detener" else "Detectar luz"
    val statusText = when {
        lightSensor == null -> LightLevel.UNAVAILABLE.label
        isDetecting -> "${level.label}. ${lux?.toInt() ?: 0} lux"
        else -> "Usa vibraciones para ubicar fuentes de luz"
    }

    DisposableEffect(isDetecting, lightSensor) {
        if (!isDetecting || lightSensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    lux = event.values.firstOrNull()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose {
                sensorManager.unregisterListener(listener)
                vibrator.cancel()
            }
        }
    }

    LaunchedEffect(isDetecting, level) {
        val interval = vibrationIntervalForLevel(level) ?: return@LaunchedEffect
        while (isDetecting) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(45L)
            }
            delay(interval)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "$buttonText. $statusText"
                stateDescription = statusText
                liveRegion = LiveRegionMode.Polite
            }
            .clickable(enabled = lightSensor != null) {
                isDetecting = !isDetecting
                if (!isDetecting) {
                    vibrator.cancel()
                    lux = null
                }
            },
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.LightMode,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = buttonText, style = MaterialTheme.typography.titleMedium)
                Text(text = statusText)
            }
        }
    }
}

private fun vibrationIntervalForLevel(level: LightLevel): Long? {
    return when (level) {
        LightLevel.HIGH -> 130L
        LightLevel.MEDIUM -> 320L
        LightLevel.LOW -> 800L
        LightLevel.NONE -> 1600L
        LightLevel.UNAVAILABLE -> null
    }
}

private fun Context.appVibrator(): Vibrator {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}

@Composable
fun ToolsScreen(
    onScanPdf: () -> Unit,
    onScanVideo: () -> Unit,
    onOpenProcessed: (PdfDocument) -> Unit,
    onOpenVideo: (VideoDocument) -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val pdfDocs by viewModel.pdfDocuments.collectAsState()
    val videoDocs by viewModel.videoDocuments.collectAsState()
    var toDelete by remember { mutableStateOf<PdfDocument?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoDocument?>(null) }

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(text = "Eliminar documento") },
            text = { Text(text = "¿Deseas eliminar este documento procesado?") },
            confirmButton = {
                Button(onClick = {
                    toDelete?.let { viewModel.delete(it) }
                    toDelete = null
                }) { Text(text = "Eliminar") }
            },
            dismissButton = {
                Button(onClick = { toDelete = null }) { Text(text = "Cancelar") }
            }
        )
    }

    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text(text = "Eliminar video") },
            text = { Text(text = "¿Deseas eliminar este video procesado?") },
            confirmButton = {
                Button(onClick = {
                    videoToDelete?.let { viewModel.deleteVideo(it) }
                    videoToDelete = null
                }) { Text(text = "Eliminar") }
            },
            dismissButton = {
                Button(onClick = { videoToDelete = null }) { Text(text = "Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "Herramientas") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LightDetectorCard()
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            contentDescription = "Escanear PDF"
                        }
                        .clickable(onClick = onScanPdf),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null
                        )
                        Text(text = "Escanear PDF", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Extrae texto y descripciones con IA")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            contentDescription = "Escanear Video"
                        }
                        .clickable(onClick = onScanVideo),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null
                        )
                        Text(text = "Escanear Video", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Analiza videos con IA (máx 2 GB)")
                    }
                }
            }

            items(pdfDocs) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProcessed(doc) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = doc.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = SimpleDateFormat(
                                "dd/MM/yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date(doc.timestamp)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { toDelete = doc }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar documento")
                        }
                    }
                }
            }

            items(videoDocs) { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVideo(video) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = video.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = SimpleDateFormat(
                                "dd/MM/yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date(video.timestamp)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val sizeMB = video.sizeBytes / (1024 * 1024)
                        Text(
                            text = "Tamaño: $sizeMB MB",
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { videoToDelete = video }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar video")
                        }
                    }
                }
            }
        }
    }
}
