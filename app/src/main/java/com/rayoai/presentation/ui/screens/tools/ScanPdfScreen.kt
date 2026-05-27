@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.PdfDocument
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.model.AccessiblePdfDocument
import com.rayoai.domain.usecase.AccessiblePdfHtmlRenderer
import com.rayoai.domain.usecase.ExtractAccessiblePdfUseCase
import com.rayoai.domain.usecase.pdf.DeletePdfDocumentUseCase
import com.rayoai.domain.usecase.pdf.GetPdfDocumentsUseCase
import com.rayoai.domain.usecase.pdf.SavePdfDocumentUseCase
import com.rayoai.core.ResultWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class ScanPdfViewModel @Inject constructor(
    private val extractAccessiblePdfUseCase: ExtractAccessiblePdfUseCase,
    private val savePdfDocumentUseCase: SavePdfDocumentUseCase,
    getPdfDocumentsUseCase: GetPdfDocumentsUseCase,
    private val deletePdfDocumentUseCase: DeletePdfDocumentUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var status by mutableStateOf<String?>(null)
        private set

    var resultHtml by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var suggestedFileName by mutableStateOf("rayoai_pdf_accesible.pdf")
        private set

    var exportStatus by mutableStateOf<String?>(null)
        private set

    val pdfDocuments: StateFlow<List<PdfDocument>> = getPdfDocumentsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun analyze(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                isLoading = true
                status = "Cargando documento..."
                resultHtml = null
                exportStatus = null

                val pageCount = getPageCount(context.contentResolver, uri)
                if (pageCount <= 0) {
                    throw IllegalStateException("No se pudieron leer paginas del PDF")
                }

                status = "Analizando con Gemini..."
                val apiKey = userPreferencesRepository.apiKey.first() ?: ""
                val language = Locale.getDefault().language
                val model = userPreferencesRepository.defaultModel.firstOrNull()
                    ?: GeminiModelConfig.DEFAULT_MODEL

                val fragments = mutableListOf<AccessiblePdfDocument>()
                val chunkSize = 1
                var startPage = 0
                while (startPage < pageCount) {
                    val endPage = minOf(startPage + chunkSize - 1, pageCount - 1)
                    val rangeLabel = "paginas ${startPage + 1}-${endPage + 1} de $pageCount"
                    status = "Procesando $rangeLabel..."
                    val images = renderPages(context.contentResolver, uri, startPage, endPage)
                    try {
                        val rawJson = extractRange(
                            apiKey = apiKey,
                            images = images,
                            pageRange = rangeLabel,
                            language = language,
                            isFirstRange = startPage == 0,
                            model = model
                        )
                        fragments.add(AccessiblePdfHtmlRenderer.parseJson(rawJson))
                    } finally {
                        images.forEach { it.recycle() }
                    }
                    startPage = endPage + 1
                    if (pageCount > 6 && startPage < pageCount) {
                        status = "Esperando 15 segundos para no saturar el modelo..."
                        delay(15_000)
                    }
                }

                val accessibleDocument = AccessiblePdfHtmlRenderer.merge(fragments)
                val html = AccessiblePdfHtmlRenderer.renderHtml(accessibleDocument)
                resultHtml = html
                status = "Listo"
                isLoading = false

                val name = getDisplayName(context.contentResolver, uri) ?: "Documento PDF"
                suggestedFileName = buildSuggestedPdfFileName(name)
                savePdfDocumentUseCase(
                    name,
                    uri.toString(),
                    html,
                    System.currentTimeMillis()
                )
            } catch (e: Exception) {
                status = e.message
                isLoading = false
            }
        }
    }

    fun delete(doc: PdfDocument) {
        viewModelScope.launch { deletePdfDocumentUseCase(doc.id) }
    }

    fun saveAccessiblePdf(context: Context, uri: Uri) {
        val html = resultHtml ?: return
        viewModelScope.launch {
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

    fun share(doc: PdfDocument, context: Context) {
        viewModelScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) {
                    AccessiblePdfExporter.createShareIntent(
                        context = context,
                        html = doc.content,
                        fileName = buildSuggestedPdfFileName(doc.name)
                    )
                }
                context.startActivity(intent)
                exportStatus = "PDF listo para compartir"
            } catch (e: Exception) {
                exportStatus = e.message ?: "No se pudo compartir el PDF"
            }
        }
    }

    private suspend fun extractRange(
        apiKey: String,
        images: List<Bitmap>,
        pageRange: String,
        language: String,
        isFirstRange: Boolean,
        model: String
    ): String {
        val delays = listOf(0L, 5_000L, 15_000L, 30_000L)
        var lastError: Throwable? = null

        delays.forEachIndexed { attempt, waitMs ->
            if (waitMs > 0) {
                status = "Reintentando $pageRange en ${waitMs / 1000} segundos..."
                delay(waitMs)
            }

            var text: String? = null
            var error: String? = null
            extractAccessiblePdfUseCase(
                apiKey = apiKey,
                images = images,
                pageRange = pageRange,
                languageCode = language,
                isFirstRange = isFirstRange,
                model = model
            ).collect { res ->
                when (res) {
                    is ResultWrapper.Loading -> status = "Analizando $pageRange con Gemini..."
                    is ResultWrapper.Success -> text = res.data
                    is ResultWrapper.Error -> error = res.message ?: "Error desconocido"
                }
            }

            try {
                val raw = text ?: throw IllegalStateException(error ?: "Gemini no devolvio contenido")
                AccessiblePdfHtmlRenderer.parseJson(raw)
                return raw
            } catch (e: Exception) {
                lastError = e
                val message = error ?: e.message.orEmpty()
                val retryable = isRetryableGeminiError(message) || attempt < 2
                if (!retryable || attempt == delays.lastIndex) {
                    throw IllegalStateException("No se pudo procesar $pageRange: ${message.ifBlank { "respuesta JSON invalida" }}")
                }
            }
        }

        throw IllegalStateException(lastError?.message ?: "No se pudo procesar $pageRange")
    }

    private fun isRetryableGeminiError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("429") ||
                lower.contains("503") ||
                lower.contains("500") ||
                lower.contains("quota") ||
                lower.contains("rate") ||
                lower.contains("overload") ||
                lower.contains("unavailable") ||
                lower.contains("timeout") ||
                lower.contains("temporarily")
    }

    private fun getPageCount(resolver: ContentResolver, uri: Uri): Int {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return 0
        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount
            }
        }
    }

    private suspend fun renderPages(
        resolver: ContentResolver,
        uri: Uri,
        startPage: Int,
        endPage: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val bitmaps = mutableListOf<Bitmap>()
                for (index in startPage..endPage) {
                    renderer.openPage(index).use { page ->
                        val width = 1080
                        val height = (width.toFloat() / page.width * page.height).toInt()
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bmp)
                    }
                }
                bitmaps
            }
        }
    }

    private fun getDisplayName(resolver: ContentResolver, uri: Uri): String? {
        // Puedes mejorar esto consultando MediaStore si lo necesitas.
        return uri.lastPathSegment
    }

    private fun buildSuggestedPdfFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "documento" }
        return "${cleaned}_accesible.pdf"
    }
}

@Composable
fun ScanPdfScreen(
    incomingPdfUri: Uri? = null,
    onNavigateBack: () -> Unit,
    onOpenProcessed: (PdfDocument) -> Unit,
    onPdfConsumed: () -> Unit = {},
    viewModel: ScanPdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val pdfDocs by viewModel.pdfDocuments.collectAsState()
    var hasConsumedIncoming by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<PdfDocument?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.saveAccessiblePdf(context, it) }
    }

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

    // Launcher para seleccionar PDF
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.analyze(context, it)
        }
    }

    // Si viene un PDF compartido desde otra app
    LaunchedEffect(incomingPdfUri) {
        if (incomingPdfUri != null && !hasConsumedIncoming) {
            hasConsumedIncoming = true
            try {
                context.contentResolver.takePersistableUriPermission(
                    incomingPdfUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.analyze(context, incomingPdfUri)
            onPdfConsumed()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Escanear PDF") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = { launcher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Seleccionar PDF"
                    }
                ) {
                    Text("Seleccionar PDF")
                }
            }

            if (viewModel.isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = viewModel.status ?: "Cargando"
                        }
                    )
                    viewModel.status?.let { Text(it) }
                }
            }

            viewModel.resultHtml?.let { html ->
                item {
                    Button(
                        onClick = { saveLauncher.launch(viewModel.suggestedFileName) },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Guardar PDF accesible"
                        }
                    ) {
                        Text("Guardar PDF accesible")
                    }
                }

                viewModel.exportStatus?.let { status ->
                    item { Text(status) }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
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
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(520.dp)
                        )
                    }
                }
            }

            if (viewModel.resultHtml != null) {
                item {
                    Button(onClick = onNavigateBack) {
                        Text("Volver")
                    }
                }
            }

            if (pdfDocs.isNotEmpty()) {
                item {
                    Text(
                        text = "PDFs escaneados",
                        style = MaterialTheme.typography.titleMedium
                    )
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
                        Row {
                            IconButton(onClick = { viewModel.share(doc, context) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartir PDF accesible")
                            }
                            IconButton(onClick = { toDelete = doc }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar documento")
                            }
                        }
                    }
                }
            }
        }
    }
}
