package com.rayoai.presentation.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.usecase.pdf.GetPdfDocumentByIdUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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

    val clipboard = LocalClipboardManager.current

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

                    Text(
                        text = doc?.content ?: "Sin contenido disponible",
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )

                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(doc?.content ?: ""))
                        },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Copiar contenido del documento"
                        }
                    ) {
                        Text("Copiar contenido")
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
