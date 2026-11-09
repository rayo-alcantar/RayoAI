package com.rayoai.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Procesador universal de desenfoque de fondo (Modo Retrato / Bokeh).
 * Utiliza Google ML Kit Selfie Segmentation para aislar el sujeto y aplicar
 * un desenfoque fotográfico al fondo sin depender de extensiones OEM de fabricantes.
 */
object PortraitBokehProcessor {
    private const val TAG = "PortraitBokeh"

    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    suspend fun applyBokehEffect(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(source, 0)
            val segmentationMask = Tasks.await(segmenter.process(inputImage))

            val maskWidth = segmentationMask.width
            val maskHeight = segmentationMask.height
            val maskBuffer = segmentationMask.buffer

            maskBuffer.rewind()
            val totalPixels = maskWidth * maskHeight
            val maskPixels = IntArray(totalPixels)
            var maxConfidence = 0f

            for (i in 0 until totalPixels) {
                val confidence = maskBuffer.float
                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                }
                val alpha = (confidence * 255f).toInt().coerceIn(0, 255)
                maskPixels[i] = Color.argb(alpha, 255, 255, 255)
            }

            // Si no se detecta ninguna persona con suficiente confianza, mantener imagen original
            if (maxConfidence < 0.25f) {
                Log.d(TAG, "No person detected with sufficient confidence ($maxConfidence), skipping blur")
                return@withContext source
            }

            // 1. Crear máscara alfa y escalarla a las dimensiones originales
            val rawMaskBitmap = Bitmap.createBitmap(maskPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            val scaledMask = Bitmap.createScaledBitmap(rawMaskBitmap, source.width, source.height, true)
            if (scaledMask != rawMaskBitmap) {
                rawMaskBitmap.recycle()
            }

            // 2. Crear fondo desenfocado
            val blurredBackground = createBlurredBackground(source, scaleFactor = 8, blurRadius = 5, blurPasses = 3)

            // 3. Componer la persona nítida sobre el fondo desenfocado
            val composite = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composite)

            // Fondo desenfocado
            canvas.drawBitmap(blurredBackground, 0f, 0f, null)

            // Persona nítida enmascarada
            val foreground = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val fgCanvas = Canvas(foreground)
            fgCanvas.drawBitmap(scaledMask, 0f, 0f, null)

            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            fgCanvas.drawBitmap(source, 0f, 0f, maskPaint)

            // Superponer sujeto nítido sobre fondo con bokeh
            canvas.drawBitmap(foreground, 0f, 0f, null)

            // Liberar memoria de bitmaps intermedios
            scaledMask.recycle()
            blurredBackground.recycle()
            foreground.recycle()

            composite
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying selfie bokeh segmentation", e)
            source
        }
    }

    private fun createBlurredBackground(source: Bitmap, scaleFactor: Int, blurRadius: Int, blurPasses: Int): Bitmap {
        val scaledWidth = maxOf(1, source.width / scaleFactor)
        val scaledHeight = maxOf(1, source.height / scaleFactor)

        val small = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val blurredSmall = fastBoxBlur(small, blurRadius, blurPasses)

        val blurredFull = Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true)
        if (blurredSmall != small) {
            blurredSmall.recycle()
        }
        small.recycle()
        return blurredFull
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        if (radius < 1) return src
        val w = src.width
        val h = src.height
        val current = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        current.getPixels(pixels, 0, w, 0, 0, w, h)

        for (p in 0 until passes) {
            boxBlurHorizontal(pixels, w, h, radius)
            boxBlurVertical(pixels, w, h, radius)
        }

        current.setPixels(pixels, 0, w, 0, 0, w, h)
        return current
    }

    private fun boxBlurHorizontal(pixels: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val row = IntArray(w)

        for (y in 0 until h) {
            val yOffset = y * w
            System.arraycopy(pixels, yOffset, row, 0, w)

            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (i in -r..r) {
                val c = row[i.coerceIn(0, w - 1)]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (x in 0 until w) {
                pixels[yOffset + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val leaving = row[(x - r).coerceIn(0, w - 1)]
                val entering = row[(x + r + 1).coerceIn(0, w - 1)]

                rSum += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                gSum += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                bSum += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(pixels: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val col = IntArray(h)

        for (x in 0 until w) {
            for (y in 0 until h) {
                col[y] = pixels[y * w + x]
            }

            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (i in -r..r) {
                val c = col[i.coerceIn(0, h - 1)]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (y in 0 until h) {
                pixels[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val leaving = col[(y - r).coerceIn(0, h - 1)]
                val entering = col[(y + r + 1).coerceIn(0, h - 1)]

                rSum += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                gSum += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                bSum += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
}
