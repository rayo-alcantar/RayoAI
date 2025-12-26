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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Manejar el Intent de compartir.
            when (intent?.action) {
                Intent.ACTION_SEND -> {
                    val type = intent.type
                    when {
                        type?.startsWith("image/") == true -> {
                            intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { imageUri ->
                                val mainIntent = Intent(this, MainActivity::class.java).apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, imageUri)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = ClipData.newUri(contentResolver, "shared_image", imageUri)
                                }
                                startActivity(mainIntent)
                            }
                        }
                        type == "application/pdf" -> {
                            intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { pdfUri ->
                                val mainIntent = Intent(this, MainActivity::class.java).apply {
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
                                val mainIntent = Intent(this, MainActivity::class.java).apply {
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
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SharingActivity", "Error handling shared content", e)
        }
        // Finalizar esta actividad intermediaria.
        finish()
    }
}
