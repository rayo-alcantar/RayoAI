package com.rayoai.presentation.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
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
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_developed_by),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.about_developer_name),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_tester_label),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.about_tester_name),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.about_legal_label),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.about_legal_name),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.about_donation_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Botón para contactar con el desarrollador (correo)
        Button(onClick = {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${context.getString(R.string.about_email)}")
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_email_subject))
            }
            context.startActivity(emailIntent)
        }) {
            Text(stringResource(R.string.about_contact_developer))
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Botón de sitio web
        Button(onClick = {
            val websiteUrl = context.getString(R.string.about_website_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
            context.startActivity(intent)
        }) {
            Text(stringResource(R.string.about_visit_website))
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Botón PayPal
        Button(onClick = {
            val paypalUrl = context.getString(R.string.about_paypal_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
            context.startActivity(intent)
        }) {
            Text(stringResource(R.string.about_donate_paypal))
        }
    }
}
