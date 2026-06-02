@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.R
import com.rayoai.domain.model.VideoDocument
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

private enum class LightLevel(val labelRes: Int) {
    UNAVAILABLE(R.string.light_level_unavailable),
    NONE(R.string.light_level_none),
    LOW(R.string.light_level_low),
    MEDIUM(R.string.light_level_medium),
    HIGH(R.string.light_level_high)
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
    val theremin = remember { LightTheremin() }
    val lightUnavailable = stringResource(R.string.light_level_unavailable)
    val detectorStopped = stringResource(R.string.light_detector_stopped)
    val detectingStop = stringResource(R.string.light_detector_detecting_stop)
    val detectLight = stringResource(R.string.light_detector_start)
    val detectorHint = stringResource(R.string.light_detector_hint)

    var isDetecting by remember { mutableStateOf(false) }
    var lux by remember { mutableStateOf<Float?>(null) }
    var announcedLevel by remember { mutableStateOf<LightLevel?>(null) }
    var announcedLux by remember { mutableStateOf<Float?>(null) }
    var accessibleStatus by remember { mutableStateOf(detectorStopped) }
    val level = if (lightSensor == null) LightLevel.UNAVAILABLE else lightLevelForLux(lux)
    val levelLabel = stringResource(level.labelRes)
    val buttonText = if (isDetecting) detectingStop else detectLight
    val statusText = when {
        lightSensor == null -> lightUnavailable
        isDetecting -> "$levelLabel. ${lux?.toInt() ?: 0} lux"
        else -> detectorHint
    }
    val accessibleText = if (isDetecting) accessibleStatus else detectorStopped

    LaunchedEffect(isDetecting, level, levelLabel, detectorStopped, lux) {
        if (!isDetecting) {
            announcedLevel = null
            announcedLux = null
            accessibleStatus = detectorStopped
            return@LaunchedEffect
        }

        val currentLux = lux
        val previousLux = announcedLux
        val significantLuxChange = currentLux != null && previousLux != null &&
                kotlin.math.abs(currentLux - previousLux) >= when (level) {
                    LightLevel.HIGH -> 500f
                    LightLevel.MEDIUM -> 150f
                    LightLevel.LOW -> 20f
                    LightLevel.NONE -> 1f
                    LightLevel.UNAVAILABLE -> Float.MAX_VALUE
                }

        if (level != announcedLevel || significantLuxChange) {
            announcedLevel = level
            announcedLux = currentLux
            accessibleStatus = levelLabel
        }
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
            theremin.start()
            onDispose {
                sensorManager.unregisterListener(listener)
                vibrator.cancel()
                theremin.stop()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            theremin.stop()
        }
    }

    LaunchedEffect(isDetecting, lux) {
        if (isDetecting) {
            theremin.updateLux(lux)
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
                contentDescription = "$buttonText. $accessibleText"
                stateDescription = accessibleText
                liveRegion = LiveRegionMode.Polite
            }
            .clickable(enabled = lightSensor != null) {
                isDetecting = !isDetecting
                if (!isDetecting) {
                    vibrator.cancel()
                    theremin.stop()
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
                Text(
                    text = statusText,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

private class LightTheremin {
    @Volatile
    private var isRunning = false

    @Volatile
    private var targetFrequency = 220.0

    @Volatile
    private var targetVolume = 0.03

    private var audioThread: Thread? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        updateLux(null)
        audioThread = Thread(::runAudioLoop, "RayoAI-LightTheremin").also { it.start() }
    }

    fun stop() {
        isRunning = false
        audioThread = null
    }

    fun updateLux(lux: Float?) {
        val normalized = when {
            lux == null -> 0.0
            lux <= 0f -> 0.0
            else -> (ln(lux.toDouble() + 1.0) / ln(1001.0)).coerceIn(0.0, 1.0)
        }
        targetFrequency = 180.0 + (normalized * 920.0)
        targetVolume = 0.02 + (normalized * 0.16)
    }

    private fun runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val sampleRate = 44_100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buffer = ShortArray((minBufferSize / 2).coerceAtLeast(1024))
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        var phase = 0.0
        var frequency = targetFrequency
        var volume = targetVolume
        try {
            audioTrack.play()
            while (isRunning) {
                frequency += (targetFrequency - frequency) * 0.08
                volume += (targetVolume - volume) * 0.08
                val phaseStep = 2.0 * PI * frequency / sampleRate
                for (i in buffer.indices) {
                    val sample = sin(phase) * volume
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                    phase += phaseStep
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                audioTrack.write(buffer, 0, buffer.size)
            }
        } finally {
            audioTrack.pause()
            audioTrack.flush()
            audioTrack.release()
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
    onOpenVideo: (VideoDocument) -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val videoDocs by viewModel.videoDocuments.collectAsState()
    var videoToDelete by remember { mutableStateOf<VideoDocument?>(null) }
    val deleteVideoTitle = stringResource(R.string.video_delete_confirm_title)
    val deleteVideoText = stringResource(R.string.video_delete_confirm_text)
    val deleteText = stringResource(R.string.delete)
    val cancelText = stringResource(R.string.cancel)
    val toolsTitle = stringResource(R.string.tools_title)
    val scanPdfText = stringResource(R.string.tool_scan_pdf)
    val scanPdfDescription = stringResource(R.string.tool_scan_pdf_description)
    val scanVideoText = stringResource(R.string.scan_video)
    val scanVideoDescription = stringResource(R.string.tool_scan_video_description)

    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text(text = deleteVideoTitle) },
            text = { Text(text = deleteVideoText) },
            confirmButton = {
                Button(onClick = {
                    videoToDelete?.let { viewModel.deleteVideo(it) }
                    videoToDelete = null
                }) { Text(text = deleteText) }
            },
            dismissButton = {
                Button(onClick = { videoToDelete = null }) { Text(text = cancelText) }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = toolsTitle) }) }
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
                            contentDescription = scanPdfText
                        }
                        .clickable(onClick = onScanPdf),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null
                        )
                        Text(text = scanPdfText, style = MaterialTheme.typography.titleMedium)
                        Text(text = scanPdfDescription)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            contentDescription = scanVideoText
                        }
                        .clickable(onClick = onScanVideo),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null
                        )
                        Text(text = scanVideoText, style = MaterialTheme.typography.titleMedium)
                        Text(text = scanVideoDescription)
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
                            text = stringResource(R.string.video_size, sizeMB),
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { videoToDelete = video }) {
                            Icon(Icons.Filled.Delete, contentDescription = deleteVideoTitle)
                        }
                    }
                }
            }
        }
    }
}
