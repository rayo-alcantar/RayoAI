package com.rayoai.presentation.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rayoai.R
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.presentation.ui.components.SecureTextField
import com.rayoai.presentation.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    val apiKeySavedMsg = stringResource(R.string.settings_api_key_saved_message)

    LaunchedEffect(uiState.isApiKeySaved) {
        if (uiState.isApiKeySaved) {
            snackbarHostState.showSnackbar(apiKeySavedMsg)
            viewModel.clearApiKeySavedStatus()
        }
    }

    LaunchedEffect(uiState.navigateTo) {
        uiState.navigateTo?.let { route ->
            if (route == "api_instructions") {
                navController.navigate(Screen.ApiInstructions.route)
            }
            viewModel.onNavigationHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sección API Key
            Text(stringResource(R.string.settings_api_key_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SecureTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = stringResource(R.string.settings_api_key_hint),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveApiKey(apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_api_key_save))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.onNavigateToApiInstructions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_api_key_instructions_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Max images in chat
            OutlinedTextField(
                value = uiState.maxImagesInChat,
                onValueChange = { viewModel.onMaxImagesInChatChanged(it) },
                label = { Text(stringResource(R.string.settings_max_images_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.saveMaxImagesInChat()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )


            Spacer(modifier = Modifier.height(16.dp))

            // Sección Tema
            Text(stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.titleMedium)
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
            Text(stringResource(R.string.settings_text_size_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = uiState.currentTextScale,
                onValueChange = { viewModel.saveTextScale(it) },
                valueRange = 0.8f..1.5f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.settings_text_scale, uiState.currentTextScale),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Auto-describir al compartir
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_autodescribe_label), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.currentAutoDescribeOnShare,
                    onCheckedChange = { viewModel.saveAutoDescribeOnShare(it) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
