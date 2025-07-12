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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rayoai.R

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
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Aj-Alcantara"))
            context.startActivity(intent)
        }) {
            Text("Visitar Sitio Web")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/donate/?business=KUK3S2B8R8M22&no_recurring=0&item_name=Tu+donaci%C3%B3n+me+ayuda+a+seguir+creando+apps+%C3%BAtiles+y+accesibles+para+todos.+%C2%A1Gracias%21&currency_code=MXN"))
            context.startActivity(intent)
        }) {
            Text("Donar vía PayPal")
        }
    }
}
