package com.rayoai.presentation.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rayoai.R
import com.rayoai.presentation.ui.LocalTextToSpeech

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction

/**
 * Composable que representa una burbuja de mensaje en el chat.
 * Muestra el contenido del mensaje y, para las respuestas de la IA, ofrece opciones para copiar, compartir y escuchar.
 * @param message El [ChatMessage] a mostrar.
 */
@Composable
fun ChatBubble(message: com.rayoai.domain.model.ChatMessage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textToSpeech = LocalTextToSpeech.current
    val listenDescription = stringResource(R.string.chat_listen_description)
    val copyDescription = stringResource(R.string.chat_copy_description)
    val clipboardLabel = stringResource(R.string.chat_clip_label)
    val copiedMessage = stringResource(R.string.chat_copied_to_clipboard)

    Column(
        modifier = modifier.fillMaxWidth(),
        // Alinea la burbuja a la derecha si es un mensaje del usuario, a la izquierda si es de la IA.
        horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
    ) {
        val boxModifier = if (!message.isFromUser) {
            Modifier
                .semantics(mergeDescendants = true) {
                    customActions = listOf(
                        CustomAccessibilityAction(listenDescription) {
                            textToSpeech?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, ""); true
                        },
                        CustomAccessibilityAction(copyDescription) {
                            copyTextToClipboard(context, message.content, clipboardLabel, copiedMessage); true
                        }
                    )
                }
        } else {
            Modifier
        }

        Box(
            modifier = boxModifier
                .clip(RoundedCornerShape(12.dp)) // Bordes redondeados para la burbuja.
                // Color de fondo diferente para mensajes del usuario y de la IA.
                .background(if (message.isFromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp)
        ) {
            // Color del texto basado en el color de fondo de la burbuja para asegurar contraste.
            Text(text = message.content, color = if (message.isFromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
        // Mostrar acciones (copiar/compartir/escuchar) solo para las respuestas de la IA.
        if (!message.isFromUser) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp) // Espacio entre los botones.
            ) {
                // Botón para escuchar el texto.
                IconButton(onClick = { textToSpeech?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, "") }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = listenDescription)
                }
                // Botón para copiar el texto al portapapeles.
                IconButton(onClick = { copyTextToClipboard(context, message.content, clipboardLabel, copiedMessage) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = copyDescription)
                }
                
            }
        }
    }
}

/**
 * Copia el texto proporcionado al portapapeles del sistema.
 * @param context El contexto de la aplicación.
 * @param text El texto a copiar.
 */
private fun copyTextToClipboard(context: Context, text: String, label: String, copiedMessage: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clipData)
    // Mostrar un Toast para confirmar que el texto ha sido copiado.
    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
}

/**
 * Abre un selector de aplicaciones para compartir el texto proporcionado.
 * @param context El contexto de la aplicación.
 * @param text El texto a compartir.
 */
private fun shareText(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    // Asegurarse de que siempre haya una aplicación para manejar el Intent.
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.chat_share_description)))
}
