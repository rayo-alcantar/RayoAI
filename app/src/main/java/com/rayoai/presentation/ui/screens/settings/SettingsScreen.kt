package com.rayoai.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.R
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.presentation.ui.components.SecureTextField

/**
 * Composable para la pantalla de ajustes de la aplicación.
 * Permite al usuario configurar la clave de API de Gemini, el modo de tema, la escala de texto
 * y la opción de auto-describir al compartir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Recolecta el estado de la UI del ViewModel.
    val uiState by viewModel.uiState.collectAsState()
    // Estado mutable para el campo de texto de la API Key.
    var apiKey by remember { mutableStateOf("") }
    // Estado para mostrar SnackBar (mensajes temporales en la parte inferior de la pantalla).
    val snackbarHostState = remember { SnackbarHostState() }

    // Efecto lanzado cuando el estado de `isApiKeySaved` cambia.
    LaunchedEffect(uiState.isApiKeySaved) {
        if (uiState.isApiKeySaved) {
            snackbarHostState.showSnackbar("API Key guardada con éxito!")
            viewModel.clearApiKeySavedStatus() // Limpiar el estado después de mostrar el mensaje.
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Host para mostrar SnackBar.
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Aplicar padding de la barra superior.
                .padding(16.dp) // Padding general para el contenido.
        ) {
            // Sección de API Key
            Text("API Key", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SecureTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = "Gemini API Key",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveApiKey(apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar API Key")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de Tema
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) { // Agrupar RadioButtons para accesibilidad.
                ThemeMode.values().forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (mode == uiState.currentThemeMode),
                                onClick = { viewModel.saveThemeMode(mode) },
                                role = Role.RadioButton // Indicar rol para accesibilidad.
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (mode == uiState.currentThemeMode),
                            onClick = null // `null` para que el clic sea manejado por el `selectable` del `Row`.
                        )
                        Text(
                            text = mode.name.replace("_", " ").lowercase().capitalize(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de Tamaño de Texto
            Text("Tamaño de Texto", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = uiState.currentTextScale,
                onValueChange = { viewModel.saveTextScale(it) },
                valueRange = 0.8f..1.5f, // Rango de escala de texto (ej. 80% a 150%).
                steps = 6, // Pasos para el slider.
                modifier = Modifier.fillMaxWidth()
            )
            Text("Escala actual: ${String.format("%.1f", uiState.currentTextScale)}x")

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de Auto-describir al compartir
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-describir al compartir", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.currentAutoDescribeOnShare,
                    onCheckedChange = { viewModel.saveAutoDescribeOnShare(it) } // Guardar el estado del switch.
                )
            }
        }
    }
}