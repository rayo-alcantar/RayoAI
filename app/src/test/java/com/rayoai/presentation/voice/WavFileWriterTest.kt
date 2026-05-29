package com.rayoai.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavFileWriterTest {
    @Test
    fun createHeader_writesExpectedPcmWavFields() {
        val header = WavFileWriter.createHeader(
            pcmLength = 32_000,
            sampleRate = 16_000,
            channelCount = 1,
            bitsPerSample = 16
        )

        assertEquals(44, header.size)
        assertAscii(header, 0, "RIFF")
        assertAscii(header, 8, "WAVE")
        assertAscii(header, 12, "fmt ")
        assertAscii(header, 36, "data")
        assertEquals(36 + 32_000, readIntLe(header, 4))
        assertEquals(1, readShortLe(header, 20))
        assertEquals(1, readShortLe(header, 22))
        assertEquals(16_000, readIntLe(header, 24))
        assertEquals(32_000, readIntLe(header, 28))
        assertEquals(2, readShortLe(header, 32))
        assertEquals(16, readShortLe(header, 34))
        assertEquals(32_000, readIntLe(header, 40))
    }

    private fun assertAscii(bytes: ByteArray, offset: Int, value: String) {
        assertTrue(value.encodeToByteArray().contentEquals(bytes.copyOfRange(offset, offset + value.length)))
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }
}
