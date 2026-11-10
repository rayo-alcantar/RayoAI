package com.rayoai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modos de fondo virtual disponibles para la captura de fotos.
 */
enum class VirtualBackgroundMode {
    NONE,
    WHITE,
    ROYAL_BLUE,
    CUSTOM
}

/**
 * Configuración activa del fondo virtual.
 */
data class VirtualBackgroundConfig(
    val mode: VirtualBackgroundMode = VirtualBackgroundMode.NONE,
    val customImageUri: Uri? = null
)

/**
 * Resultado del procesamiento de fondo virtual.
 */
data class BackgroundProcessResult(
    val bitmap: Bitmap,
    val personDetected: Boolean = true
)

/**
 * Procesador fotográfico de fondos virtuales para retratos y fotos de personas.
 * Emplea Google ML Kit Selfie Segmentation con refinamiento óptico de máscara,
 * suavizado subpíxel para eliminar cortes dentados, "light wrap" (despill) para que la luz
 * del entorno abrace suavemente los bordes del cabello y silueta, y fondos de estudio
 * con iluminación fotográfica y microtextura física para evitar el aspecto artificial.
 */
object VirtualBackgroundProcessor {
    private const val TAG = "VirtualBackground"

    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    suspend fun applyBackground(
        source: Bitmap,
        config: VirtualBackgroundConfig,
        context: Context,
        customBitmapLoader: (suspend (Uri) -> Bitmap?)? = null
    ): BackgroundProcessResult = withContext(Dispatchers.Default) {
        if (config.mode == VirtualBackgroundMode.NONE) {
            return@withContext BackgroundProcessResult(bitmap = source, personDetected = true)
        }

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
                // Curva Hermite ajustada: elimina el halo de sombra de habitación (<0.36)
                // y asegura opacidad completa en el sujeto (>0.80)
                val feathered = smoothStep(0.36f, 0.80f, rawConfidence)
                val alpha = (feathered * 255f).toInt().coerceIn(0, 255)
                maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
            }

            // Si no hay persona distinguible, devuelve la imagen original
            if (maxConfidence < 0.25f) {
                Log.d(TAG, "No person detected ($maxConfidence), keeping original image")
                return@withContext BackgroundProcessResult(bitmap = source, personDetected = false)
            }

            // 1. Escalar la máscara a tamaño completo con interpolación bilineal
            val rawMaskBitmap = Bitmap.createBitmap(maskPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            val fullMask = Bitmap.createScaledBitmap(rawMaskBitmap, source.width, source.height, true)
            if (fullMask != rawMaskBitmap) {
                rawMaskBitmap.recycle()
            }

            val width = source.width
            val height = source.height
            val totalPixels = width * height

            val rawAlphaPixels = IntArray(totalPixels)
            fullMask.getPixels(rawAlphaPixels, 0, width, 0, 0, width, height)
            fullMask.recycle()

            // 2. Filtro de suavizado subpíxel para eliminar cortes escalonados (anti-aliasing óptico)
            val smoothedAlpha = smoothMaskAlpha(rawAlphaPixels, width, height, radius = 3)

            // 3. Generar el fondo de estudio calibrado ópticamente
            val backgroundBitmap = createBackgroundBitmap(
                mode = config.mode,
                customUri = config.customImageUri,
                targetWidth = width,
                targetHeight = height,
                context = context,
                customBitmapLoader = customBitmapLoader
            )

            val srcPixels = IntArray(totalPixels)
            val bgPixels = IntArray(totalPixels)
            val compositePixels = IntArray(totalPixels)

            source.getPixels(srcPixels, 0, width, 0, 0, width, height)
            backgroundBitmap.getPixels(bgPixels, 0, width, 0, 0, width, height)
            backgroundBitmap.recycle()

            // 4. Composición con Light Wrap (despill fotográfico):
            // La luz del fondo abraza sutilmente los bordes del cabello y cuerpo
            // eliminando el aspecto de calcomanía / recorte digital plano
            for (i in 0 until totalPixels) {
                val a = smoothedAlpha[i]
                if (a == 255) {
                    compositePixels[i] = srcPixels[i]
                } else if (a == 0) {
                    compositePixels[i] = bgPixels[i]
                } else {
                    val invA = 255 - a
                    // El factor de envoltura de luz es proporcional a la cercanía al borde
                    val wrapStrength = (invA / 255f) * 0.38f

                    val sr = (srcPixels[i] shr 16) and 0xFF
                    val sg = (srcPixels[i] shr 8) and 0xFF
                    val sb = srcPixels[i] and 0xFF

                    val br = (bgPixels[i] shr 16) and 0xFF
                    val bg = (bgPixels[i] shr 8) and 0xFF
                    val bb = bgPixels[i] and 0xFF

                    // Envuelve el borde del sujeto con la tonalidad lumínica del fondo
                    val wrappedSr = sr * (1f - wrapStrength) + br * wrapStrength
                    val wrappedSg = sg * (1f - wrapStrength) + bg * wrapStrength
                    val wrappedSb = sb * (1f - wrapStrength) + bb * wrapStrength

                    val r = ((wrappedSr * a + br * invA) / 255f).toInt().coerceIn(0, 255)
                    val g = ((wrappedSg * a + bg * invA) / 255f).toInt().coerceIn(0, 255)
                    val b = ((wrappedSb * a + bb * invA) / 255f).toInt().coerceIn(0, 255)

                    compositePixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val composite = Bitmap.createBitmap(compositePixels, width, height, Bitmap.Config.ARGB_8888)
            BackgroundProcessResult(bitmap = composite, personDetected = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying virtual background", e)
            BackgroundProcessResult(bitmap = source, personDetected = false)
        }
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Desenfoque de caja separable bidimensional en O(N) para suavizar la máscara alfa
     * y proporcionar bordes naturales y orgánicos alrededor de cabello y contornos.
     */
    private fun smoothMaskAlpha(rawAlpha: IntArray, width: Int, height: Int, radius: Int = 3): IntArray {
        val temp = IntArray(width * height)
        val result = IntArray(width * height)
        val window = 2 * radius + 1

        // Pase horizontal
        for (y in 0 until height) {
            val offset = y * width
            var sum = 0
            for (i in -radius..radius) {
                val cx = i.coerceIn(0, width - 1)
                sum += (rawAlpha[offset + cx] ushr 24) and 0xFF
            }
            for (x in 0 until width) {
                temp[offset + x] = sum / window
                val remX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                sum += ((rawAlpha[offset + addX] ushr 24) and 0xFF) - ((rawAlpha[offset + remX] ushr 24) and 0xFF)
            }
        }

        // Pase vertical
        for (x in 0 until width) {
            var sum = 0
            for (i in -radius..radius) {
                val cy = i.coerceIn(0, height - 1)
                sum += temp[cy * width + x]
            }
            for (y in 0 until height) {
                val alpha = (sum / window).coerceIn(0, 255)
                result[y * width + x] = alpha
                val remY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                sum += temp[addY * width + x] - temp[remY * width + x]
            }
        }
        return result
    }

    private suspend fun createBackgroundBitmap(
        mode: VirtualBackgroundMode,
        customUri: Uri?,
        targetWidth: Int,
        targetHeight: Int,
        context: Context,
        customBitmapLoader: (suspend (Uri) -> Bitmap?)?
    ): Bitmap {
        return when (mode) {
            VirtualBackgroundMode.WHITE -> createStudioWhiteBackground(targetWidth, targetHeight)
            VirtualBackgroundMode.ROYAL_BLUE -> createStudioRoyalBlueBackground(targetWidth, targetHeight)
            VirtualBackgroundMode.CUSTOM -> {
                val customBitmap = customUri?.let { uri ->
                    customBitmapLoader?.invoke(uri) ?: loadBitmapFromUri(context, uri)
                }
                if (customBitmap != null) {
                    val cropped = createCenterCropBitmap(customBitmap, targetWidth, targetHeight)
                    // Suavizado óptico sutil de profundidad de campo (bokeh suave) para separar el fondo
                    softenBackground(cropped)
                } else {
                    Log.w(TAG, "Custom background URI is null or unreadable, falling back to Royal Blue")
                    createStudioRoyalBlueBackground(targetWidth, targetHeight)
                }
            }
            VirtualBackgroundMode.NONE -> createStudioWhiteBackground(targetWidth, targetHeight)
        }
    }

    /**
     * Fondo blanco de estudio de retrato (High-Key):
     * Luz central limpia y suave caída periférica natural con microtextura de ciclorama.
     */
    private fun createStudioWhiteBackground(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centerX = width * 0.5f
        val centerY = height * 0.42f
        val radius = maxOf(width, height) * 0.90f

        val colors = intArrayOf(
            Color.rgb(255, 255, 255),
            Color.rgb(248, 250, 253),
            Color.rgb(238, 242, 248)
        )
        val positions = floatArrayOf(0.0f, 0.65f, 1.0f)
        paint.shader = RadialGradient(centerX, centerY, radius, colors, positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Microtextura sutil de lienzo para evitar apariencia digital plástica
        applySubtleMuslinTexture(bitmap, amplitude = 2)
        return bitmap
    }

    /**
     * Fondo azul rey de estudio fotográfico profesional:
     * Color azul marino/rey elegante, degradado de iluminación de estudio centrado en el sujeto
     * y textura microfina de lienzo/muselina que elimina el aspecto sintético.
     */
    private fun createStudioRoyalBlueBackground(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centerX = width * 0.5f
        val centerY = height * 0.40f
        val radius = maxOf(width, height) * 0.85f

        // Tonalidades fotográficas balanceadas (azul rey de estudio con iluminación cenital)
        val colors = intArrayOf(
            Color.rgb(38, 78, 146),  // Foco de luz suave detrás del sujeto
            Color.rgb(22, 52, 98),   // Azul rey corporativo / retrato
            Color.rgb(12, 26, 52)    // Sombra viñeta exterior de estudio
        )
        val positions = floatArrayOf(0.0f, 0.55f, 1.0f)
        paint.shader = RadialGradient(centerX, centerY, radius, colors, positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Microtextura de muselina fotográfica
        applySubtleMuslinTexture(bitmap, amplitude = 3)
        return bitmap
    }

    /**
     * Aplica una microvariación pseudoaleatoria imperceptible pero biológicamente apreciable
     * que rompe el degradado digital sintético y emula tela de fondo de estudio.
     */
    private fun applySubtleMuslinTexture(bitmap: Bitmap, amplitude: Int = 2) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                val noise = (((x * 37 + y * 17) xor (x * y)) and 7) - 3
                val factor = (noise * amplitude) / 7

                val c = pixels[offset + x]
                val a = (c ushr 24) and 0xFF
                val r = (((c shr 16) and 0xFF) + factor).coerceIn(0, 255)
                val g = (((c shr 8) and 0xFF) + factor).coerceIn(0, 255)
                val b = ((c and 0xFF) + factor).coerceIn(0, 255)
                pixels[offset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Aplica un desenfoque sutil a la imagen personalizada para simular profundidad
     * de campo de apertura fotográfica, haciendo que el sujeto destaque de forma tridimensional.
     */
    private fun softenBackground(source: Bitmap, radius: Int = 4): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val temp = IntArray(width * height)
        val window = 2 * radius + 1

        // Horizontal
        for (y in 0 until height) {
            val offset = y * width
            var sumR = 0; var sumG = 0; var sumB = 0
            for (i in -radius..radius) {
                val cx = i.coerceIn(0, width - 1)
                val px = pixels[offset + cx]
                sumR += (px shr 16) and 0xFF
                sumG += (px shr 8) and 0xFF
                sumB += px and 0xFF
            }
            for (x in 0 until width) {
                val r = (sumR / window).coerceIn(0, 255)
                val g = (sumG / window).coerceIn(0, 255)
                val b = (sumB / window).coerceIn(0, 255)
                temp[offset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

                val remX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                val pxRem = pixels[offset + remX]
                val pxAdd = pixels[offset + addX]

                sumR += ((pxAdd shr 16) and 0xFF) - ((pxRem shr 16) and 0xFF)
                sumG += ((pxAdd shr 8) and 0xFF) - ((pxRem shr 8) and 0xFF)
                sumB += (pxAdd and 0xFF) - (pxRem and 0xFF)
            }
        }

        // Vertical
        for (x in 0 until width) {
            var sumR = 0; var sumG = 0; var sumB = 0
            for (i in -radius..radius) {
                val cy = i.coerceIn(0, height - 1)
                val px = temp[cy * width + x]
                sumR += (px shr 16) and 0xFF
                sumG += (px shr 8) and 0xFF
                sumB += px and 0xFF
            }
            for (y in 0 until height) {
                val r = (sumR / window).coerceIn(0, 255)
                val g = (sumG / window).coerceIn(0, 255)
                val b = (sumB / window).coerceIn(0, 255)
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

                val remY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                val pxRem = temp[remY * width + x]
                val pxAdd = temp[addY * width + x]

                sumR += ((pxAdd shr 16) and 0xFF) - ((pxRem shr 16) and 0xFF)
                sumG += ((pxAdd shr 8) and 0xFF) - ((pxRem shr 8) and 0xFF)
                sumB += (pxAdd and 0xFF) - (pxRem and 0xFF)
            }
        }

        source.setPixels(pixels, 0, width, 0, 0, width, height)
        return source
    }

    fun createCenterCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) {
            return source
        }
        val scaleX = targetWidth.toFloat() / source.width.toFloat()
        val scaleY = targetHeight.toFloat() / source.height.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = maxOf(1, (source.width * scale).toInt())
        val scaledHeight = maxOf(1, (source.height * scale).toInt())

        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val x = ((scaledWidth - targetWidth) / 2).coerceIn(0, maxOf(0, scaledWidth - targetWidth))
        val y = ((scaledHeight - targetHeight) / 2).coerceIn(0, maxOf(0, scaledHeight - targetHeight))

        val cropped = Bitmap.createBitmap(scaled, x, y, targetWidth, targetHeight)
        if (scaled != source && scaled != cropped) {
            scaled.recycle()
        }
        return cropped
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading background bitmap from URI: $uri", e)
            null
        }
    }
}
