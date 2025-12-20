@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.ExtractTextFromImagesUseCase
import com.rayoai.domain.usecase.pdf.SavePdfDocumentUseCase
import com.rayoai.core.ResultWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class ScanPdfViewModel @Inject constructor(
    private val extractTextFromImagesUseCase: ExtractTextFromImagesUseCase,
    private val savePdfDocumentUseCase: SavePdfDocumentUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var status by mutableStateOf<String?>(null)
        private set

    var resultText by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun analyze(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                isLoading = true
                status = "Cargando documento..."

                val images = renderAllPages(context.contentResolver, uri)

                status = "Analizando con Gemini..."
                val apiKey = userPreferencesRepository.apiKey.first() ?: ""
                val language = Locale.getDefault().language
                val model = userPreferencesRepository.defaultModel.firstOrNull()
                    ?: GeminiModelConfig.DEFAULT_MODEL

                extractTextFromImagesUseCase(apiKey, images, language, model).collect { res ->
                    when (res) {
                        is ResultWrapper.Loading -> status = "Procesando texto..."
                        is ResultWrapper.Success -> {
                            resultText = res.data
                            status = "Listo"
                            isLoading = false

                            val name = getDisplayName(context.contentResolver, uri) ?: "Documento PDF"
                            savePdfDocumentUseCase(
                                name,
                                uri.toString(),
                                res.data,
                                System.currentTimeMillis()
                            )
                        }
                        is ResultWrapper.Error -> {
                            status = res.message ?: "Error desconocido"
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                status = e.message
                isLoading = false
            }
        }
    }

    private fun renderAllPages(resolver: ContentResolver, uri: Uri): List<Bitmap> {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount
                val bitmaps = mutableListOf<Bitmap>()
                repeat(count) { index ->
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
}

@Composable
fun ScanPdfScreen(
    incomingPdfUri: Uri? = null,
    onNavigateBack: () -> Unit,
    onPdfConsumed: () -> Unit = {},
    viewModel: ScanPdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var hasConsumedIncoming by remember { mutableStateOf(false) }

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

            viewModel.resultText?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            }

            if (viewModel.resultText != null) {
                Button(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(viewModel.resultText ?: ""))
                }) {
                    Text("Copiar contenido")
                }

                Button(onClick = onNavigateBack) {
                    Text("Volver")
                }
            }
        }
    }
}
