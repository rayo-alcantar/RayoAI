package com.rayoai.presentation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import android.content.ClipData
import com.rayoai.presentation.ui.MainActivity
import android.widget.Toast
import com.rayoai.R

/**
 * Actividad intermediaria que maneja los Intents de compartir imágenes desde otras aplicaciones.
 * Su única responsabilidad es recibir la URI de la imagen y reenviarla a la MainActivity.
 */
class SharingActivity : ComponentActivity() {

    /**
     * Helper function para obtener Parcelable de manera compatible con todas las versiones de Android.
     * En Android 13+ (API 33), getParcelableExtra(String) está deprecado y puede causar crashes.
     */
    private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key)
            }
        } catch (e: Exception) {
            Log.e("SharingActivity", "Error getting parcelable extra: $key", e)
            null
        }
    }

    private inline fun <reified T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String): ArrayList<T>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra(key)
            }
        } catch (e: Exception) {
            Log.e("SharingActivity", "Error getting parcelable list extra: $key", e)
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Manejar el Intent de compartir.
            when (intent?.action) {
                Intent.ACTION_SEND -> {
                    val type = intent.type
                    when {
                        isImageShare(intent) -> openSharedImages(intent.sharedImageUris())
                        type == "application/pdf" -> {
                            intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { pdfUri ->
                                val mainIntent = Intent(this@SharingActivity, MainActivity::class.java).apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                                    putExtra("EXTRA_IS_PDF", true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = ClipData.newUri(contentResolver, "shared_pdf", pdfUri)
                                }
                                startActivity(mainIntent)
                            }
                        }
                        type?.startsWith("video/") == true -> {
                            intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { videoUri ->
                                val mainIntent = Intent(this@SharingActivity, MainActivity::class.java).apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, videoUri)
                                    putExtra("EXTRA_IS_VIDEO", true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = ClipData.newUri(contentResolver, "shared_video", videoUri)
                                }
                                startActivity(mainIntent)
                            }
                        }
                        type == "text/plain" -> {
                            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                                val mainIntent = Intent(this@SharingActivity, MainActivity::class.java).apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(MainActivity.EXTRA_VIDEO_URL, sharedText)
                                    putExtra("EXTRA_IS_VIDEO_URL", true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(mainIntent)
                            }
                        }
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    if (isImageShare(intent)) openSharedImages(intent.sharedImageUris())
                }
            }
        } catch (e: Exception) {
            Log.e("SharingActivity", "Error handling shared content", e)
            Toast.makeText(this, getString(R.string.error_loading_shared_image), Toast.LENGTH_LONG).show()
        } finally {
            // Finalizar esta actividad intermediaria.
            finish()
        }
    }

    private fun isImageShare(sharedIntent: Intent): Boolean {
        return sharedIntent.type?.startsWith("image/") == true ||
            sharedIntent.clipData?.description?.hasMimeType("image/*") == true
    }

    private fun Intent.sharedImageUris(): List<Uri> {
        val extraUris = getParcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM).orEmpty()
        val singleExtraUri = getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
        val clipUris = clipData?.let { data ->
            (0 until data.itemCount).mapNotNull { data.getItemAt(it).uri }
        }.orEmpty()
        return (extraUris + listOfNotNull(singleExtraUri) + clipUris).distinct()
    }

    private fun openSharedImages(sourceUris: List<Uri>) {
        if (sourceUris.isEmpty()) {
            Log.w("SharingActivity", "Image share did not include a readable URI")
            Toast.makeText(this, getString(R.string.error_loading_shared_image), Toast.LENGTH_LONG).show()
            return
        }
        if (sourceUris.size == 1) {
            openSingleImage(sourceUris.first())
            return
        }

        val truncatedCount = (sourceUris.size - MAX_SHARED_IMAGES).coerceAtLeast(0)
        val imageUris = sourceUris.take(MAX_SHARED_IMAGES)
        val clipData = ClipData.newUri(contentResolver, "shared_images", imageUris.first())
        imageUris.drop(1).forEach { clipData.addItem(ClipData.Item(it)) }
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(MainActivity.EXTRA_SHARED_IMAGE_URIS, ArrayList(imageUris))
            putExtra(MainActivity.EXTRA_SHARED_IMAGE_TRUNCATED_COUNT, truncatedCount)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            this.clipData = clipData
        })
    }

    private fun openSingleImage(imageUri: Uri) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "shared_image", imageUri)
        })
    }

    private companion object {
        // Límite defensivo para evitar que un Intent externo agote memoria, red o cuota de API.
        const val MAX_SHARED_IMAGES = 50
    }
}
