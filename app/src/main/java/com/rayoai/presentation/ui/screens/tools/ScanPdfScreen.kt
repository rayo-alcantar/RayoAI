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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.model.AccessiblePdfDocument
import com.rayoai.domain.usecase.AccessiblePdfHtmlRenderer
import com.rayoai.domain.usecase.ExtractAccessiblePdfUseCase
import com.rayoai.domain.usecase.pdf.SavePdfDocumentUseCase
import com.rayoai.core.ResultWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class ScanPdfViewModel @Inject constructor(
    private val extractAccessiblePdfUseCase: ExtractAccessiblePdfUseCase,
    private val savePdfDocumentUseCase: SavePdfDocumentUseCase,
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

    private suspend fun extractRange(
        apiKey: String,
        images: List<Bitmap>,
        pageRange: String,
        language: String,
        isFirstRange: Boolean,
        model: String
    ): String {
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
        error?.let { throw IllegalStateException(it) }
        return text ?: throw IllegalStateException("Gemini no devolvio contenido para $pageRange")
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
    onPdfConsumed: () -> Unit = {},
    viewModel: ScanPdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasConsumedIncoming by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.saveAccessiblePdf(context, it) }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { launcher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = "Seleccionar PDF"
                }
            ) {
                Text("Seleccionar PDF")
            }

            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = viewModel.status ?: "Cargando"
                    }
                )
                viewModel.status?.let { Text(it) }
            }

            viewModel.resultHtml?.let { html ->
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
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (viewModel.resultHtml != null) {
                Button(
                    onClick = { saveLauncher.launch(viewModel.suggestedFileName) },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Guardar PDF accesible"
                    }
                ) {
                    Text("Guardar PDF accesible")
                }

                viewModel.exportStatus?.let { Text(it) }

                Button(onClick = onNavigateBack) {
                    Text("Volver")
                }
            }
        }
    }
}
