package com.rayoai.presentation.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.rayoai.BuildConfig
import com.rayoai.R
import com.rayoai.accessibility.RayoAccessibilityService
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.UpdateChannel
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.presentation.ui.components.SecureTextField
import com.rayoai.presentation.ui.navigation.Screen
import com.rayoai.presentation.ui.updates.UpdateCheckResult
import com.rayoai.presentation.ui.updates.UpdateCheckViewModel
import com.rayoai.presentation.ui.updates.UpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updatesEnabled = BuildConfig.GITHUB_UPDATES_ENABLED
    val updateViewModel: UpdateCheckViewModel? = if (updatesEnabled) hiltViewModel(activity) else null
    val updateUiState by (
        updateViewModel?.uiState?.collectAsState()
            ?: remember { mutableStateOf(UpdateUiState()) }
        )
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var isThemeMenuExpanded by remember { mutableStateOf(false) }
    var isUpdateChannelMenuExpanded by remember { mutableStateOf(false) }
    var showAccessibilitySetupDialog by remember { mutableStateOf(false) }
    var googleSectionExpanded by remember { mutableStateOf(false) }
    var appearanceSectionExpanded by remember { mutableStateOf(false) }
    var accessibilitySectionExpanded by remember { mutableStateOf(false) }

    val modelOptions = listOf(
        GeminiModelConfig.DEFAULT_MODEL to stringResource(R.string.model_gemini_31_flash_lite),
        "gemini-3.1-pro-preview" to stringResource(R.string.model_gemini_31_pro),
        "gemini-3-flash-preview" to stringResource(R.string.model_gemini_3_flash),
        GeminiModelConfig.FALLBACK_MODEL to stringResource(R.string.model_gemini_25_flash),
        "gemini-2.5-pro" to stringResource(R.string.model_gemini_25_pro)
    )
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_mode_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_mode_light),
        ThemeMode.DARK to stringResource(R.string.theme_mode_dark),
        ThemeMode.HIGH_CONTRAST to stringResource(R.string.theme_mode_high_contrast)
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

    LaunchedEffect(Unit) {
        viewModel.syncAccessibilityQuickCaptureEnabled(
            context.isRayoAccessibilityServiceEnabled()
        )
    }

    LaunchedEffect(updateUiState.lastCheckResult) {
        if (!updatesEnabled || updateViewModel == null) return@LaunchedEffect
        when (updateUiState.lastCheckResult) {
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncAccessibilityQuickCaptureEnabled(
                    context.isRayoAccessibilityServiceEnabled()
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.settings_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            ExpandableSettingsSection(
                title = stringResource(R.string.settings_google_section_title),
                description = stringResource(R.string.settings_google_section_description),
                expanded = googleSectionExpanded,
                onExpandedChange = { googleSectionExpanded = it }
            ) {
                SecureTextField(
                    value = uiState.apiKeyInput,
                    onValueChange = { viewModel.onApiKeyChanged(it) },
                    label = stringResource(R.string.settings_api_key_hint),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.saveApiKey(uiState.apiKeyInput) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_api_key_save))
                    }
                    OutlinedButton(
                        onClick = { viewModel.onNavigateToApiInstructions() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_api_key_instructions_button))
                    }
                }

                val selectedModelLabel = modelOptions.firstOrNull { it.first == uiState.currentDefaultModel }?.second
                    ?: uiState.currentDefaultModel
                BoxedDropdownField(
                    value = selectedModelLabel,
                    label = stringResource(R.string.settings_default_model_hint),
                    expanded = isModelMenuExpanded,
                    onExpand = { isModelMenuExpanded = true },
                    onDismiss = { isModelMenuExpanded = false }
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.maxImagesInChat,
                        onValueChange = { viewModel.onMaxImagesInChatChanged(it) },
                        label = { Text(stringResource(R.string.settings_max_images_label_compact)) },
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
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.saveMaxImagesInChat()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier
                            .weight(0.8f)
                            .height(56.dp)
                    ) {
                        Text(stringResource(R.string.settings_max_images_save_compact))
                    }
                }
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.settings_appearance_design_section_title),
                description = stringResource(R.string.settings_appearance_section_description),
                expanded = appearanceSectionExpanded,
                onExpandedChange = { appearanceSectionExpanded = it }
            ) {
                val selectedThemeLabel = themeOptions.firstOrNull { it.first == uiState.currentThemeMode }?.second
                    ?: uiState.currentThemeMode.name
                BoxedDropdownField(
                    value = selectedThemeLabel,
                    label = stringResource(R.string.settings_theme_label),
                    expanded = isThemeMenuExpanded,
                    onExpand = { isThemeMenuExpanded = true },
                    onDismiss = { isThemeMenuExpanded = false }
                ) {
                    themeOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.saveThemeMode(value)
                                isThemeMenuExpanded = false
                            }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_text_scale, uiState.currentTextScale),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.currentTextScale,
                    onValueChange = { viewModel.saveTextScale(it) },
                    valueRange = 0.8f..1.5f,
                    steps = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ExpandableSettingsSection(
                title = if (updatesEnabled) {
                    stringResource(R.string.settings_accessibility_updates_section_title)
                } else {
                    stringResource(R.string.settings_accessibility_capture_title)
                },
                description = if (updatesEnabled) {
                    stringResource(R.string.settings_accessibility_updates_section_description)
                } else {
                    stringResource(R.string.settings_accessibility_section_description)
                },
                expanded = accessibilitySectionExpanded,
                onExpandedChange = { accessibilitySectionExpanded = it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_accessibility_capture_switch),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_accessibility_capture_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.accessibilityQuickCaptureEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showAccessibilitySetupDialog = true
                            } else {
                                viewModel.saveAccessibilityQuickCaptureEnabled(false)
                            }
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_accessibility_capture_open_settings))
                }

                if (updatesEnabled && updateViewModel != null) {
                    val selectedChannelLabel = updateChannelOptions.firstOrNull { it.first == uiState.currentUpdateChannel }?.second
                        ?: uiState.currentUpdateChannel.name
                    BoxedDropdownField(
                        value = selectedChannelLabel,
                        label = stringResource(R.string.settings_update_channel_hint),
                        expanded = isUpdateChannelMenuExpanded,
                        onExpand = { isUpdateChannelMenuExpanded = true },
                        onDismiss = { isUpdateChannelMenuExpanded = false }
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
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showAccessibilitySetupDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilitySetupDialog = false },
            title = { Text(stringResource(R.string.settings_accessibility_capture_setup_title)) },
            text = { Text(stringResource(R.string.settings_accessibility_capture_setup_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilitySetupDialog = false
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ) {
                    Text(stringResource(R.string.settings_accessibility_capture_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilitySetupDialog = false }) {
                    Text(stringResource(R.string.accept))
                }
            }
        )
    }
}

private fun android.content.Context.isRayoAccessibilityServiceEnabled(): Boolean {
    val accessibilityManager = getSystemService(AccessibilityManager::class.java) ?: return false
    val expectedServiceName = RayoAccessibilityService::class.java.name
    return accessibilityManager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.packageName == packageName &&
                serviceInfo.resolveInfo.serviceInfo.name == expectedServiceName
        }
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    description: String? = null,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val stateDescription = if (expanded) {
        stringResource(R.string.settings_section_collapse)
    } else {
        stringResource(R.string.settings_section_expand)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .weight(0.44f)
                    .heightIn(min = 56.dp)
                    .semantics {
                        contentDescription = "$title. $stateDescription"
                    }
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    modifier = Modifier.weight(0.56f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun BoxedDropdownField(
    value: String,
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() },
            label = { Text(label) },
            readOnly = true,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            menuContent()
        }
    }
}
