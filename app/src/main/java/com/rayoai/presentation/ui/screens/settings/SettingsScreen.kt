package com.rayoai.presentation.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.R
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.presentation.ui.components.SecureTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sacar stringResource que puedan usarse en callbacks
    val apiKeyLabel = stringResource(R.string.settings_api_key_label)
    val apiKeyHint = stringResource(R.string.settings_api_key_hint)
    val apiKeySave = stringResource(R.string.settings_api_key_save)
    val apiKeySavedMsg = stringResource(R.string.settings_api_key_saved_message)
    val settingsTitle = stringResource(R.string.settings_title)
    val themeLabel = stringResource(R.string.settings_theme_label)
    val textSizeLabel = stringResource(R.string.settings_text_size_label)
    val textScaleLabelRaw = stringResource(R.string.settings_text_scale)
    val textScaleLabel: (Float) -> String = { scale ->
        String.format(textScaleLabelRaw, scale)
    }
    val autodescribeLabel = stringResource(R.string.settings_autodescribe_label)

    LaunchedEffect(uiState.isApiKeySaved) {
        if (uiState.isApiKeySaved) {
            snackbarHostState.showSnackbar(apiKeySavedMsg)
            viewModel.clearApiKeySavedStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(settingsTitle) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Sección API Key
            Text(apiKeyLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SecureTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = apiKeyHint,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveApiKey(apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(apiKeySave)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección Tema
            Text(themeLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                ThemeMode.values().forEach { mode ->
                    val modeLabel = mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (mode == uiState.currentThemeMode),
                                onClick = { viewModel.saveThemeMode(mode) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (mode == uiState.currentThemeMode),
                            onClick = null
                        )
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección Tamaño de Texto
            Text(textSizeLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = uiState.currentTextScale,
                onValueChange = { viewModel.saveTextScale(it) },
                valueRange = 0.8f..1.5f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = textScaleLabel(uiState.currentTextScale),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Auto-describir al compartir
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(autodescribeLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.currentAutoDescribeOnShare,
                    onCheckedChange = { viewModel.saveAutoDescribeOnShare(it) }
                )
            }
        }
    }
}
