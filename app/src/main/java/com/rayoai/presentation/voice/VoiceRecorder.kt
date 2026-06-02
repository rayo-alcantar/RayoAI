package com.rayoai.presentation.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.rayoai.R
import com.rayoai.domain.model.AudioRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VoiceRecorder(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var rawFile: File? = null
    private var wavFile: File? = null
    private var startedAt: Long = 0L
    @Volatile private var recording = false

    val isRecording: Boolean
        get() = recording

    @SuppressLint("MissingPermission")
    fun start() {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            context.getString(R.string.voice_record_permission_not_granted)
        }
        if (recording) return

        val cacheDir = File(context.cacheDir, "voice_recordings").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val nextRawFile = File(cacheDir, "$id.pcm")
        val nextWavFile = File(cacheDir, "$id.wav")
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE)

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error(context.getString(R.string.voice_record_init_failed))
        }

        rawFile = nextRawFile
        wavFile = nextWavFile
        audioRecord = recorder
        recording = true
        startedAt = System.currentTimeMillis()
        recorder.startRecording()

        recordingJob = scope.launch {
            FileOutputStream(nextRawFile).use { output ->
                val buffer = ByteArray(minBuffer)
                while (recording) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) output.write(buffer, 0, read)
                }
            }
        }
    }

    suspend fun stop(): AudioRecording = withContext(Dispatchers.IO) {
        if (!recording) error(context.getString(R.string.voice_record_no_active_recording))
        recording = false
        val recorder = audioRecord
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
        } finally {
            recorder?.release()
            audioRecord = null
        }

        listOfNotNull(recordingJob).joinAll()
        recordingJob = null

        val pcm = rawFile ?: error(context.getString(R.string.voice_record_audio_not_found))
        val wav = wavFile ?: error(context.getString(R.string.voice_record_wav_not_found))
        if (pcm.length() <= 0L) {
            pcm.delete()
            wav.delete()
            error(context.getString(R.string.voice_record_empty))
        }

        WavFileWriter.writeFromPcm(
            pcmFile = pcm,
            wavFile = wav,
            sampleRate = SAMPLE_RATE,
            channelCount = CHANNEL_COUNT,
            bitsPerSample = BITS_PER_SAMPLE
        )

        AudioRecording(
            wavFile = wav,
            rawPcmFile = pcm,
            durationMillis = System.currentTimeMillis() - startedAt,
            sampleRate = SAMPLE_RATE,
            channelCount = CHANNEL_COUNT
        )
    }

    fun cancel() {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        audioRecord?.release()
        audioRecord = null
        rawFile?.delete()
        wavFile?.delete()
        rawFile = null
        wavFile = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_COUNT = 1
        const val BITS_PER_SAMPLE = 16
    }
}
