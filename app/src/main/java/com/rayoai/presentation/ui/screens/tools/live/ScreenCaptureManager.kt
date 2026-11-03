package com.rayoai.presentation.ui.screens.tools.live

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura la pantalla del dispositivo a intervalos regulares mediante MediaProjection,
 * produciendo mapas de bits listos para transmitir a Gemini Live.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val onFrameCaptured: (Bitmap) -> Unit,
    private val intervalMs: Long = 1000L
) {
    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val isCapturing = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null

    fun start(resultCode: Int, data: Intent) {
        if (isCapturing.getAndSet(true)) return

        try {
            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val metrics = context.resources.displayMetrics
            val density = metrics.densityDpi
            val targetWidth = 720
            val targetHeight = ((720f * metrics.heightPixels) / metrics.widthPixels).toInt()

            imageReader = ImageReader.newInstance(
                targetWidth,
                targetHeight,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "RayoAI-LiveScreen",
                targetWidth,
                targetHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            captureRunnable = object : Runnable {
                override fun run() {
                    if (!isCapturing.get()) return
                    captureLatestFrame(targetWidth, targetHeight)
                    handler.postDelayed(this, intervalMs)
                }
            }
            handler.postDelayed(captureRunnable!!, intervalMs)
            Log.i(TAG, "Captura de pantalla iniciada con resolución ${targetWidth}x${targetHeight}.")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando captura de pantalla", e)
            stop()
        }
    }

    private fun captureLatestFrame(width: Int, height: Int) {
        val reader = imageReader ?: return
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val rawBitmap = Bitmap.createBitmap(
                width + (rowPadding / pixelStride),
                height,
                Bitmap.Config.ARGB_8888
            )
            rawBitmap.copyPixelsFromBuffer(buffer)

            val cleanBitmap = if (rowPadding > 0) {
                Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
            } else {
                rawBitmap
            }
            onFrameCaptured(cleanBitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Error leyendo imagen de pantalla: ${e.message}")
        } finally {
            try {
                image.close()
            } catch (e: Exception) {
                // Ya cerrada
            }
        }
    }

    fun stop() {
        isCapturing.set(false)
        captureRunnable?.let { handler.removeCallbacks(it) }
        captureRunnable = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando virtualDisplay", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error cerrando imageReader", e)
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error deteniendo mediaProjection", e)
        }
        mediaProjection = null
        Log.i(TAG, "Captura de pantalla detenida y recursos liberados.")
    }
}
