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
import androidx.compose.ui.res.stringResource
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
import com.rayoai.R

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
    val pdfDocumentTitle = stringResource(R.string.scan_pdf_document_title)
    val loadingPdfText = stringResource(R.string.scan_pdf_loading_document)
    val loadingPdfDescription = stringResource(R.string.scan_pdf_loading_document)
    val saveAccessiblePdfText = stringResource(R.string.scan_pdf_save_accessible)
    val savedText = stringResource(R.string.scan_pdf_saved)
    val saveFailedText = stringResource(R.string.scan_pdf_save_failed)
    val unnamedDocument = stringResource(R.string.scan_pdf_no_name)
    val defaultFileStem = stringResource(R.string.scan_pdf_default_file_stem)
    val backText = stringResource(R.string.back)
    val backToToolsText = stringResource(R.string.scan_pdf_back)
    val noContentHtml = stringResource(R.string.scan_pdf_no_content_html)
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val html = doc?.content
        if (uri != null && html != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        AccessiblePdfExporter.savePdf(
                            context.contentResolver,
                            uri,
                            html,
                            context.getString(R.string.scan_pdf_open_destination_failed)
                        )
                    }
                    exportStatus = savedText
                } catch (e: Exception) {
                    exportStatus = e.message ?: saveFailedText
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(pdfDocumentTitle) }) }
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
                                contentDescription = loadingPdfDescription
                            }
                    )
                    Text(
                        text = loadingPdfText,
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
                        text = doc?.name ?: unnamedDocument,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = { saveLauncher.launch(buildSuggestedPdfFileName(doc?.name ?: defaultFileStem)) },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = saveAccessiblePdfText
                        }
                    ) {
                        Text(saveAccessiblePdfText)
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
                                    renderStoredPdfContent(doc?.content, noContentHtml),
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
                            contentDescription = backToToolsText
                        }
                    ) {
                        Text(backText)
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

private fun renderStoredPdfContent(content: String?, noContentHtml: String): String {
    val value = content?.takeIf { it.isNotBlank() } ?: return noContentHtml
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
