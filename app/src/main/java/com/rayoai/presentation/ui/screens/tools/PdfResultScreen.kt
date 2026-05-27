package com.rayoai.presentation.ui.screens.tools

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.usecase.pdf.GetPdfDocumentByIdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PdfResultViewModel @Inject constructor(
    private val getPdfDocumentByIdUseCase: GetPdfDocumentByIdUseCase
) : ViewModel() {

    fun getPdfDocument(id: Long): StateFlow<com.rayoai.domain.model.PdfDocument?> {
        return getPdfDocumentByIdUseCase(id)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PdfResultScreen(
    docId: Long,
    onNavigateBack: () -> Unit,
    viewModel: PdfResultViewModel = hiltViewModel()
) {
    val doc by viewModel.getPdfDocument(docId).collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val html = doc?.content
        if (uri != null && html != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        AccessiblePdfExporter.savePdf(context.contentResolver, uri, html)
                    }
                    exportStatus = "PDF guardado"
                } catch (e: Exception) {
                    exportStatus = e.message ?: "No se pudo guardar el PDF"
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Documento PDF") }) }
    ) { padding ->
        when {
            doc == null -> {
                // Estado de carga
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .semantics {
                                contentDescription = "Cargando documento PDF"
                            }
                    )
                    Text(
                        text = "Cargando...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            else -> {
                // Contenido cargado
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = doc?.name ?: "Documento sin nombre",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = { saveLauncher.launch(buildSuggestedPdfFileName(doc?.name ?: "documento")) },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Guardar PDF accesible"
                        }
                    ) {
                        Text("Guardar PDF accesible")
                    }

                    exportStatus?.let { Text(it) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        AndroidView(
                            factory = { webContext ->
                                WebView(webContext).apply {
                                    settings.javaScriptEnabled = false
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(
                                    null,
                                    renderStoredPdfContent(doc?.content),
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Volver a herramientas"
                        }
                    ) {
                        Text("Volver")
                    }
                }
            }
        }
    }
}

private fun buildSuggestedPdfFileName(name: String): String {
    val cleaned = name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "documento" }
    return "${cleaned}_accesible.pdf"
}

private fun renderStoredPdfContent(content: String?): String {
    val value = content?.takeIf { it.isNotBlank() } ?: return "<p>Sin contenido disponible</p>"
    if (value.contains("<html", ignoreCase = true) || value.contains("<!doctype", ignoreCase = true)) {
        return value
    }
    val escaped = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")
    return "<html><body><p>$escaped</p></body></html>"
}
