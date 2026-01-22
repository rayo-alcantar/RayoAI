package com.rayoai.presentation.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rayoai.R
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.UpdateChannel
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.presentation.ui.components.SecureTextField
import com.rayoai.presentation.ui.navigation.Screen
import com.rayoai.presentation.ui.updates.UpdateCheckResult
import com.rayoai.presentation.ui.updates.UpdateCheckViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.activity.ComponentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as ComponentActivity
    val updateViewModel: UpdateCheckViewModel = hiltViewModel(activity)
    val updateUiState by updateViewModel.uiState.collectAsState()
    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var isUpdateChannelMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val modelOptions = listOf(
        GeminiModelConfig.DEFAULT_MODEL to stringResource(R.string.model_gemini_25_flash),
        "gemini-2.5-pro" to stringResource(R.string.model_gemini_25_pro),
        "gemini-3-flash" to stringResource(R.string.model_gemini_3_flash),
        "gemini-3-pro" to stringResource(R.string.model_gemini_3_pro),
        "gemini-3" to stringResource(R.string.model_gemini_3)
    )
    val updateChannelOptions = listOf(
        UpdateChannel.STABLE to stringResource(R.string.update_channel_stable),
        UpdateChannel.BETA to stringResource(R.string.update_channel_beta),
        UpdateChannel.ALL to stringResource(R.string.update_channel_all)
    )

    val apiKeySavedMsg = stringResource(R.string.settings_api_key_saved_message)
    val updateNoUpdatesMsg = stringResource(R.string.update_check_no_updates)
    val updateBetaAvailableMsg = stringResource(R.string.update_check_beta_available)

    LaunchedEffect(uiState.isApiKeySaved) {
        if (uiState.isApiKeySaved) {
            snackbarHostState.showSnackbar(apiKeySavedMsg)
            viewModel.clearApiKeySavedStatus()
        }
    }

    LaunchedEffect(updateUiState.lastCheckResult) {
        when (val result = updateUiState.lastCheckResult) {
            UpdateCheckResult.UpToDate -> {
                snackbarHostState.showSnackbar(updateNoUpdatesMsg)
                updateViewModel.clearCheckResult()
            }
            UpdateCheckResult.BetaAvailable -> {
                snackbarHostState.showSnackbar(updateBetaAvailableMsg)
                updateViewModel.clearCheckResult()
            }
            null -> Unit
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
                value = uiState.apiKeyInput,
                onValueChange = { viewModel.onApiKeyChanged(it) },
                label = stringResource(R.string.settings_api_key_hint),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveApiKey(uiState.apiKeyInput) },
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

            // Canal de actualizaciones
            Text(stringResource(R.string.settings_update_channel_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val selectedChannelLabel = updateChannelOptions.firstOrNull { it.first == uiState.currentUpdateChannel }?.second
                ?: uiState.currentUpdateChannel.name
            Box {
                OutlinedTextField(
                    value = selectedChannelLabel,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isUpdateChannelMenuExpanded = true },
                    label = { Text(stringResource(R.string.settings_update_channel_hint)) },
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null
                        )
                    }
                )
                DropdownMenu(
                    expanded = isUpdateChannelMenuExpanded,
                    onDismissRequest = { isUpdateChannelMenuExpanded = false }
                ) {
                    updateChannelOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.saveUpdateChannel(value)
                                isUpdateChannelMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val checkUpdatesLabel = if (updateUiState.isChecking) {
                stringResource(R.string.update_checking)
            } else {
                stringResource(R.string.update_check_now)
            }
            OutlinedButton(
                onClick = { updateViewModel.checkForUpdates(manual = true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !updateUiState.isChecking
            ) {
                Text(checkUpdatesLabel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Modelo predeterminado
            Text(stringResource(R.string.settings_default_model_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val selectedModelLabel = modelOptions.firstOrNull { it.first == uiState.currentDefaultModel }?.second
                ?: uiState.currentDefaultModel
            Box {
                OutlinedTextField(
                    value = selectedModelLabel,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isModelMenuExpanded = true },
                    label = { Text(stringResource(R.string.settings_default_model_hint)) },
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null
                        )
                    }
                )
                DropdownMenu(
                    expanded = isModelMenuExpanded,
                    onDismissRequest = { isModelMenuExpanded = false }
                ) {
                    modelOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.saveDefaultModel(value)
                                isModelMenuExpanded = false
                            }
                        )
                    }
                }
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

        }
    }
}
