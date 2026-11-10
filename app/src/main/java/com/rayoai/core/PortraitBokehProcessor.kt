package com.rayoai.core

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance, natural optical portrait bokeh processor using Google ML Kit Selfie Segmentation.
 * Employs normalized convolution (alpha-weighted blur) to suppress foreground silhouette bleed,
 * optical depth tuning (f/1.8-f/2.2 emulation), and full-resolution alpha-channel feathering.
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
            val totalMaskPixels = maskWidth * maskHeight
            val maskPixels = IntArray(totalMaskPixels)
            var maxConfidence = 0f

            for (i in 0 until totalMaskPixels) {
                val rawConfidence = maskBuffer.float
                if (rawConfidence > maxConfidence) {
                    maxConfidence = rawConfidence
                }
                // Smoothstep curve (0.25 to 0.75) for soft, natural optical boundary feathering
                val feathered = smoothStep(0.20f, 0.80f, rawConfidence)
                val alpha = (feathered * 255f).toInt().coerceIn(0, 255)
                maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
            }

            // If no person is detected with sufficient confidence, return untouched source
            if (maxConfidence < 0.25f) {
                Log.d(TAG, "No person detected with sufficient confidence ($maxConfidence), skipping blur")
                return@withContext source
            }

            // 1. Create full-resolution feathered alpha mask
            val rawMaskBitmap = Bitmap.createBitmap(maskPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            val fullMask = Bitmap.createScaledBitmap(rawMaskBitmap, source.width, source.height, true)
            if (fullMask != rawMaskBitmap) {
                rawMaskBitmap.recycle()
            }

            // 2. Generate natural optical bokeh background with foreground suppression
            val blurredBackground = createNaturalBokehBackground(
                source = source,
                fullMask = fullMask,
                scaleFactor = 3,
                blurRadius = 5,
                blurPasses = 2
            )

            // 3. Pixel-exact ARGB composite
            val width = source.width
            val height = source.height
            val totalPixels = width * height

            val srcPixels = IntArray(totalPixels)
            val bgPixels = IntArray(totalPixels)
            val maskAlphaPixels = IntArray(totalPixels)
            val compositePixels = IntArray(totalPixels)

            source.getPixels(srcPixels, 0, width, 0, 0, width, height)
            blurredBackground.getPixels(bgPixels, 0, width, 0, 0, width, height)
            fullMask.getPixels(maskAlphaPixels, 0, width, 0, 0, width, height)

            for (i in 0 until totalPixels) {
                val a = (maskAlphaPixels[i] ushr 24) and 0xFF
                if (a == 255) {
                    compositePixels[i] = srcPixels[i]
                } else if (a == 0) {
                    compositePixels[i] = bgPixels[i]
                } else {
                    val invA = 255 - a
                    val sr = (srcPixels[i] shr 16) and 0xFF
                    val sg = (srcPixels[i] shr 8) and 0xFF
                    val sb = srcPixels[i] and 0xFF

                    val br = (bgPixels[i] shr 16) and 0xFF
                    val bg = (bgPixels[i] shr 8) and 0xFF
                    val bb = bgPixels[i] and 0xFF

                    val r = (sr * a + br * invA) / 255
                    val g = (sg * a + bg * invA) / 255
                    val b = (sb * a + bb * invA) / 255

                    compositePixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            fullMask.recycle()
            blurredBackground.recycle()

            Bitmap.createBitmap(compositePixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying natural portrait bokeh segmentation", e)
            source
        }
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Creates an optical bokeh background using normalized convolution.
     * Weights foreground pixels near 0 so dark subject silhouettes do not bleed into the background.
     */
    private fun createNaturalBokehBackground(
        source: Bitmap,
        fullMask: Bitmap,
        scaleFactor: Int,
        blurRadius: Int,
        blurPasses: Int
    ): Bitmap {
        val sw = maxOf(1, source.width / scaleFactor)
        val sh = maxOf(1, source.height / scaleFactor)

        val smallSource = Bitmap.createScaledBitmap(source, sw, sh, true)
        val smallMask = Bitmap.createScaledBitmap(fullMask, sw, sh, true)

        val count = sw * sh
        val srcPx = IntArray(count)
        val maskPx = IntArray(count)

        smallSource.getPixels(srcPx, 0, sw, 0, 0, sw, sh)
        smallMask.getPixels(maskPx, 0, sw, 0, 0, sw, sh)

        smallSource.recycle()
        smallMask.recycle()

        // Weighted color and weight buffers
        val rWeighted = IntArray(count)
        val gWeighted = IntArray(count)
        val bWeighted = IntArray(count)
        val weights = IntArray(count)

        for (i in 0 until count) {
            val maskAlpha = (maskPx[i] ushr 24) and 0xFF
            // Background pixels have weight ~255. Foreground subject has minimal weight (min 10) to suppress bleed.
            val w = (255 - maskAlpha).coerceAtLeast(12)
            val c = srcPx[i]

            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            rWeighted[i] = (r * w) / 255
            gWeighted[i] = (g * w) / 255
            bWeighted[i] = (b * w) / 255
            weights[i] = w
        }

        for (p in 0 until blurPasses) {
            boxBlurBuffer(rWeighted, sw, sh, blurRadius)
            boxBlurBuffer(gWeighted, sw, sh, blurRadius)
            boxBlurBuffer(bWeighted, sw, sh, blurRadius)
            boxBlurBuffer(weights, sw, sh, blurRadius)
        }

        // Normalize weighted blur
        val normalizedPixels = IntArray(count)
        for (i in 0 until count) {
            val w = weights[i].coerceAtLeast(1)
            val r = ((rWeighted[i] * 255) / w).coerceIn(0, 255)
            val g = ((gWeighted[i] * 255) / w).coerceIn(0, 255)
            val b = ((bWeighted[i] * 255) / w).coerceIn(0, 255)
            normalizedPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val blurredSmall = Bitmap.createBitmap(normalizedPixels, sw, sh, Bitmap.Config.ARGB_8888)
        val blurredFull = Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true)
        blurredSmall.recycle()

        return blurredFull
    }

    private fun boxBlurBuffer(buffer: IntArray, w: Int, h: Int, r: Int) {
        if (r < 1) return
        boxBlurHorizontal(buffer, w, h, r)
        boxBlurVertical(buffer, w, h, r)
    }

    private fun boxBlurHorizontal(pixels: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val row = IntArray(w)

        for (y in 0 until h) {
            val yOffset = y * w
            System.arraycopy(pixels, yOffset, row, 0, w)

            var sum = 0
            for (i in -r..r) {
                sum += row[i.coerceIn(0, w - 1)]
            }

            for (x in 0 until w) {
                pixels[yOffset + x] = sum / div
                val leaving = row[(x - r).coerceIn(0, w - 1)]
                val entering = row[(x + r + 1).coerceIn(0, w - 1)]
                sum += entering - leaving
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

            var sum = 0
            for (i in -r..r) {
                sum += col[i.coerceIn(0, h - 1)]
            }

            for (y in 0 until h) {
                pixels[y * w + x] = sum / div
                val leaving = col[(y - r).coerceIn(0, h - 1)]
                val entering = col[(y + r + 1).coerceIn(0, h - 1)]
                sum += entering - leaving
            }
        }
    }
}
