package com.rayoai.presentation.voice

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object WavFileWriter {
    fun writeFromPcm(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int
    ) {
        val pcmLength = pcmFile.length()
        FileOutputStream(wavFile).use { output ->
            output.write(createHeader(pcmLength, sampleRate, channelCount, bitsPerSample))
            FileInputStream(pcmFile).use { input -> input.copyTo(output) }
        }
    }

    fun createHeader(
        pcmLength: Long,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        val totalDataLen = pcmLength + 36

        return ByteArray(44).apply {
            writeAscii(0, "RIFF")
            writeIntLe(4, totalDataLen)
            writeAscii(8, "WAVE")
            writeAscii(12, "fmt ")
            writeIntLe(16, 16)
            writeShortLe(20, 1)
            writeShortLe(22, channelCount)
            writeIntLe(24, sampleRate.toLong())
            writeIntLe(28, byteRate.toLong())
            writeShortLe(32, blockAlign)
            writeShortLe(34, bitsPerSample)
            writeAscii(36, "data")
            writeIntLe(40, pcmLength)
        }
    }

    private fun ByteArray.writeAscii(offset: Int, value: String) {
        value.encodeToByteArray().copyInto(this, offset)
    }

    private fun ByteArray.writeIntLe(offset: Int, value: Long) {
        this[offset] = (value and 0xff).toByte()
        this[offset + 1] = ((value shr 8) and 0xff).toByte()
        this[offset + 2] = ((value shr 16) and 0xff).toByte()
        this[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun ByteArray.writeShortLe(offset: Int, value: Int) {
        this[offset] = (value and 0xff).toByte()
        this[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
}
