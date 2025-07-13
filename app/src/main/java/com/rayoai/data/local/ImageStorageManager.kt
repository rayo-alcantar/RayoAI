package com.rayoai.data.local

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clase para gestionar el almacenamiento de imágenes en el almacenamiento interno de la aplicación.
 * Utiliza [FileProvider] para generar URIs seguras para las imágenes guardadas.
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Guarda un [Bitmap] en un archivo JPEG dentro del directorio de imágenes de la aplicación
     * y devuelve su [Uri] segura.
     * @param bitmap El [Bitmap] a guardar.
     * @return La [Uri] del archivo guardado, o `null` si ocurre un error.
     */
    fun saveBitmapAndGetUri(bitmap: Bitmap): Uri? {
        // Generar un nombre de archivo único basado en la marca de tiempo.
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        // Obtener el directorio específico de la aplicación para imágenes.
        // Esto no requiere permisos de almacenamiento explícitos en Android 10+.
        val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "RayoAI")
        
        // Crear el directorio si no existe.
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val imageFile = File(storageDir, filename)

        return try {
            // Escribir el Bitmap en el archivo.
            val fos = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) // Comprimir a JPEG con calidad 90%.
            fos.flush()
            fos.close()
            // Obtener una URI segura utilizando FileProvider.
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
        } catch (e: IOException) {
            // Registrar el error si la operación de guardado falla.
            e.printStackTrace()
            null
        }
    }

    /**
     * Guarda un [Bitmap] en la galería de imágenes del dispositivo (Pictures/RayoAI).
     * @param bitmap El [Bitmap] a guardar.
     * @return La [Uri] de la imagen guardada, o `null` si ocurre un error.
     */
    fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RayoAI")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return uri?.also {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                resolver.delete(it, null, null)
                return null
            }
        }
    }
}