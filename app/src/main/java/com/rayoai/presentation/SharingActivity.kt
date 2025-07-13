package com.rayoai.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.rayoai.presentation.ui.MainActivity

/**
 * Actividad intermediaria que maneja los Intents de compartir imágenes desde otras aplicaciones.
 * Su única responsabilidad es recibir la URI de la imagen y reenviarla a la MainActivity.
 */
class SharingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Manejar el Intent de compartir.
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { imageUri ->
                        // Crear un Intent para iniciar MainActivity.
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            // Flags para traer la tarea existente al frente o crear una nueva.
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(mainIntent)
                    }
                }
            }
        }
        // Finalizar esta actividad intermediaria.
        finish()
    }
}