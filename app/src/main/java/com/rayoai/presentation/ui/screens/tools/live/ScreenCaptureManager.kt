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
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura la pantalla del dispositivo a intervalos regulares mediante MediaProjection,
 * ejecutando el procesamiento en segundo plano y manteniendo un fotograma en caché
 * para que Gemini Live nunca pierda la visión de la pantalla aunque la imagen sea estática.
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

    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var captureRunnable: Runnable? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private val bitmapLock = Any()
    private var latestBitmap: Bitmap? = null

    fun start(resultCode: Int, data: Intent) {
        if (isCapturing.getAndSet(true)) return

        try {
            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val thread = HandlerThread("RayoAI-ScreenCapture").apply { start() }
            handlerThread = thread
            val bgHandler = Handler(thread.looper)
            backgroundHandler = bgHandler

            // Obligatorio en Android 14+ antes de llamar a createVirtualDisplay
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.i(TAG, "MediaProjection detenido por el sistema o por el usuario.")
                    stop()
                }
            }
            projectionCallback = callback
            mediaProjection?.registerCallback(callback, bgHandler)

            val metrics = context.resources.displayMetrics
            val density = metrics.densityDpi
            val targetWidth = 720
            val rawHeight = ((720f * metrics.heightPixels) / metrics.widthPixels).toInt()
            // Dimensiones pares para codificadores de imagen
            val targetHeight = if (rawHeight % 2 == 0) rawHeight else rawHeight + 1

            imageReader = ImageReader.newInstance(
                targetWidth,
                targetHeight,
                PixelFormat.RGBA_8888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = try {
                        reader.acquireLatestImage()
                    } catch (e: Exception) {
                        null
                    } ?: return@setOnImageAvailableListener

                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * targetWidth

                        val rawBitmap = Bitmap.createBitmap(
                            targetWidth + (rowPadding / pixelStride),
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        rawBitmap.copyPixelsFromBuffer(buffer)

                        val cleanBitmap = if (rowPadding > 0) {
                            val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, targetWidth, targetHeight)
                            rawBitmap.recycle()
                            cropped
                        } else {
                            rawBitmap
                        }

                        synchronized(bitmapLock) {
                            latestBitmap?.recycle()
                            latestBitmap = cleanBitmap
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error procesando frame capturado de pantalla: ${e.message}")
                    } finally {
                        try {
                            image.close()
                        } catch (_: Exception) {}
                    }
                }, bgHandler)
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "RayoAI-LiveScreen",
                targetWidth,
                targetHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                bgHandler
            )

            captureRunnable = object : Runnable {
                override fun run() {
                    if (!isCapturing.get()) return

                    val frameToSend: Bitmap? = synchronized(bitmapLock) {
                        latestBitmap?.let {
                            if (!it.isRecycled) Bitmap.createBitmap(it) else null
                        }
                    }

                    if (frameToSend != null) {
                        onFrameCaptured(frameToSend)
                    }

                    bgHandler.postDelayed(this, intervalMs)
                }
            }
            bgHandler.postDelayed(captureRunnable!!, intervalMs)
            Log.i(TAG, "Captura de pantalla iniciada (${targetWidth}x${targetHeight}) a ${intervalMs}ms.")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando captura de pantalla", e)
            stop()
        }
    }

    fun stop() {
        if (!isCapturing.getAndSet(false)) return

        backgroundHandler?.let { handler ->
            captureRunnable?.let { handler.removeCallbacks(it) }
        }
        captureRunnable = null

        try {
            projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        } catch (_: Exception) {}
        projectionCallback = null

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

        synchronized(bitmapLock) {
            try {
                latestBitmap?.recycle()
            } catch (_: Exception) {}
            latestBitmap = null
        }

        try {
            handlerThread?.quitSafely()
        } catch (_: Exception) {}
        handlerThread = null
        backgroundHandler = null

        Log.i(TAG, "Captura de pantalla detenida y recursos liberados.")
    }
}
