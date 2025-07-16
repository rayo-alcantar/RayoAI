package com.rayoai.presentation.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "RayoAI",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Desarrollado por:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Ángel De Jesús Alcántar Garza",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tester Alpha, Diseñadora y Creativa:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Jessica Herrera",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Asesor legal",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Eddie Cortes",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Este software fue hecho sin fines de lucro, pero siempre es bienvenida una donación para continuar su desarrollo.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Botón para contactar con el desarrollador (correo)
        Button(onClick = {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:contacto@rayoscompany.com")
                putExtra(Intent.EXTRA_SUBJECT, "Contacto desde RayoAI")
            }
            context.startActivity(emailIntent)
        }) {
            Text("Contactar con el desarrollador")
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Botón de sitio web
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rayoscompany.com/"))
            context.startActivity(intent)
        }) {
            Text("Visitar Sitio Web")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/rayoalcantar?country.x=MX&locale.x=es_XC"))
            context.startActivity(intent)
        }) {
            Text("Donar vía PayPal")
        }
    }
}
