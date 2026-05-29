package com.rayoai.domain.model

import java.io.File

data class AudioRecording(
    val wavFile: File,
    val rawPcmFile: File,
    val durationMillis: Long,
    val sampleRate: Int,
    val channelCount: Int
)
