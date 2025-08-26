package com.rayoai.presentation.ui.screens.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rayoai.R

@Composable
fun AboutScreen(showDonationDialogInitially: Boolean = false) {
    val context = LocalContext.current
    var showDonateDialog by remember { mutableStateOf(showDonationDialogInitially) }
    var showBbvaDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showDonationDialogInitially) {
        if (showDonationDialogInitially) {
            showDonateDialog = true
        }
    }

    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = { Text(stringResource(R.string.donate_dialog_title)) },
            confirmButton = {
                TextButton(onClick = {
                    val paypalUrl = context.getString(R.string.about_paypal_url)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
                    context.startActivity(intent)
                    showDonateDialog = false
                }) {
                    Text(stringResource(R.string.donate_paypal))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(
                        "BBVA Details",
                        context.getString(R.string.donate_bbva_details)
                    )
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.donate_bbva_clipboard_message), Toast.LENGTH_SHORT).show()
                    showDonateDialog = false
                    showBbvaDialog = true
                }) {
                    Text(stringResource(R.string.donate_bbva))
                }
            }
        )
    }

    if (showBbvaDialog) {
        AlertDialog(
            onDismissRequest = { showBbvaDialog = false },
            title = { Text(stringResource(R.string.donate_bbva_dialog_title)) },
            text = { Text(stringResource(R.string.donate_bbva_details)) },
            confirmButton = {
                TextButton(onClick = { showBbvaDialog = false }) {
                    Text(stringResource(R.string.accept))
                }
            }
        )
    }

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
        // Botón Donar
        Button(onClick = {
            showDonateDialog = true
        }) {
            Text(stringResource(R.string.about_donate_paypal))
        }
    }
}
