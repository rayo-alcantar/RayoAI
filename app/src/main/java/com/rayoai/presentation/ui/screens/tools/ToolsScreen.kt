@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.domain.model.PdfDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolsScreen(
    onScanPdf: () -> Unit,
    onOpenProcessed: (PdfDocument) -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val pdfDocs by viewModel.pdfDocuments.collectAsState()
    var toDelete by remember { mutableStateOf<PdfDocument?>(null) }

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(text = "Eliminar documento") },
            text = { Text(text = "¿Deseas eliminar este documento procesado?") },
            confirmButton = {
                Button(onClick = {
                    toDelete?.let { viewModel.delete(it) }
                    toDelete = null
                }) { Text(text = "Eliminar") }
            },
            dismissButton = {
                Button(onClick = { toDelete = null }) { Text(text = "Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "Herramientas") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            contentDescription = "Escanear PDF"
                        }
                        .clickable(onClick = onScanPdf),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null
                        )
                        Text(text = "Escanear PDF", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Extrae texto y descripciones con IA")
                    }
                }
            }

            items(pdfDocs) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProcessed(doc) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = doc.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = SimpleDateFormat(
                                "dd/MM/yyyy HH:mm",
                                Locale.getDefault()
                            ).format(Date(doc.timestamp)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { toDelete = doc }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar documento")
                        }
                    }
                }
            }
        }
    }
}
