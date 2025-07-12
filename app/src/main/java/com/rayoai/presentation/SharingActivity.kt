package com.rayoai.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.rayoai.R
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Actividad encargada de manejar los Intents de compartir imágenes desde otras aplicaciones.
 * Recibe la imagen, la procesa y, si la opción está habilitada, la auto-describe usando Gemini
 * y muestra una notificación con el resultado.
 */
@AndroidEntryPoint
class SharingActivity : ComponentActivity() {

    @Inject
    lateinit var describeImageUseCase: DescribeImageUseCase

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val CHANNEL_ID = "rayo_ai_channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear el canal de notificación al inicio de la actividad.
        createNotificationChannel()

        // Manejar el Intent de compartir.
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    handleSendImage(intent)
                }
            }
        }
        // Finalizar la actividad inmediatamente después de manejar el Intent.
        // Esto evita que la actividad de compartir permanezca en la pila de tareas.
        finish()
    }

    /**
     * Procesa el Intent de compartir una imagen.
     * Extrae la URI de la imagen, la convierte a Bitmap y la envía a describir si la opción está habilitada.
     * @param intent El Intent recibido.
     */
    private fun handleSendImage(intent: Intent) {
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { imageUri ->
            lifecycleScope.launch {
                // Obtener la API Key y el estado de auto-descripción desde las preferencias del usuario.
                val apiKey = userPreferencesRepository.apiKey.first()
                val autoDescribeEnabled = userPreferencesRepository.autoDescribeOnShare.first()

                // Si la auto-descripción no está habilitada, o no hay API Key, no proceder.
                if (!autoDescribeEnabled) {
                    Log.d("SharingActivity", "Auto-describe on share is disabled.")
                    showNotification("Imagen Recibida", "La auto-descripción está deshabilitada.")
                    return@launch
                }

                if (apiKey.isNullOrBlank()) {
                    Log.e("SharingActivity", "API Key not configured. Cannot auto-describe.")
                    showNotification("Error", "API Key no configurada. No se pudo describir la imagen.")
                    return@launch
                }

                // Convertir la URI de la imagen a Bitmap.
                val bitmap = uriToBitmap(imageUri)
                if (bitmap != null) {
                    // Iniciar la descripción de la imagen.
                    describeImageUseCase(apiKey, bitmap).collect {
                        when (it) {
                            is ResultWrapper.Success -> {
                                val description = it.data
                                showNotification("Descripción de Imagen", description)
                                Log.d("SharingActivity", "Description: ${it.data}")
                            }
                            is ResultWrapper.Error -> {
                                val errorMessage = it.message
                                showNotification("Error", "Error al describir la imagen: $errorMessage")
                                Log.e("SharingActivity", "Error describing image: $errorMessage")
                            }
                            ResultWrapper.Loading -> {
                                // Opcional: Mostrar una notificación de carga.
                            }
                        }
                    }
                } else {
                    showNotification("Error", "No se pudo cargar la imagen.")
                    Log.e("SharingActivity", "Could not convert URI to Bitmap.")
                }
            }
        }
    }

    /**
     * Convierte una [Uri] de imagen a un [Bitmap].
     * @param selectedFileUri La URI de la imagen.
     * @return El [Bitmap] resultante, o `null` si la conversión falla.
     */
    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Para Android P (API 28) y superiores, usar ImageDecoder.
                val source = ImageDecoder.createSource(contentResolver, selectedFileUri)
                ImageDecoder.decodeBitmap(source)
            } else {
                // Para versiones anteriores, usar MediaStore.
                MediaStore.Images.Media.getBitmap(contentResolver, selectedFileUri)
            }
        } catch (e: Exception) {
            Log.e("SharingActivity", "Error converting URI to Bitmap: ", e)
            null
        }
    }

    /**
     * Crea el canal de notificación para la aplicación.
     * Necesario para Android 8.0 (API 26) y superiores.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Muestra una notificación al usuario.
     * @param title El título de la notificación.
     * @param content El contenido del texto de la notificación.
     */
    private fun showNotification(title: String, content: String) {
        // Intent para abrir la MainActivity cuando se toca la notificación.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Construir la notificación.
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground) // Icono pequeño de la notificación.
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // Intent a lanzar al tocar la notificación.
            .setAutoCancel(true) // La notificación se cierra automáticamente al tocarla.

        // Mostrar la notificación.
        with(NotificationManagerCompat.from(this)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}