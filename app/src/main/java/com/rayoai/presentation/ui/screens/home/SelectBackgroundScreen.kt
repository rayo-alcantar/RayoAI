package com.rayoai.presentation.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rayoai.R
import com.rayoai.core.VirtualBackgroundMode

/**
 * Pantalla dedicada para elegir el fondo fotográfico.
 * Permite seleccionar entre sin fondo (original), fondo claro (blanco),
 * fondo oscuro (azul rey) o cargar una fotografía propia de la galería.
 * Al pulsar "Aceptar", guarda la elección y regresa a la pantalla de la cámara.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectBackgroundScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMode by remember { mutableStateOf(uiState.virtualBackgroundConfig.mode) }
    var selectedCustomUri by remember { mutableStateOf(uiState.virtualBackgroundConfig.customImageUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomUri = uri
            selectedMode = VirtualBackgroundMode.CUSTOM
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.select_background_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedMode == VirtualBackgroundMode.CUSTOM && selectedCustomUri != null) {
                                viewModel.setCustomBackgroundUri(selectedCustomUri!!)
                            } else {
                                viewModel.setVirtualBackgroundMode(selectedMode)
                            }
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.accept),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.select_background_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Opción 1: Sin fondo
            SelectableBackgroundCard(
                title = stringResource(R.string.bg_option_none),
                description = stringResource(R.string.bg_option_none_desc),
                isSelected = selectedMode == VirtualBackgroundMode.NONE,
                onClick = { selectedMode = VirtualBackgroundMode.NONE },
                leading = {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Opción 2: Fondo claro (Blanco)
            SelectableBackgroundCard(
                title = stringResource(R.string.bg_option_white_desc),
                description = stringResource(R.string.bg_option_white_name),
                isSelected = selectedMode == VirtualBackgroundMode.WHITE,
                onClick = { selectedMode = VirtualBackgroundMode.WHITE },
                leading = {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedMode == VirtualBackgroundMode.WHITE) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            )

            // Opción 3: Fondo oscuro (Azul rey)
            SelectableBackgroundCard(
                title = stringResource(R.string.bg_option_royal_blue_desc),
                description = stringResource(R.string.bg_option_royal_blue_name),
                isSelected = selectedMode == VirtualBackgroundMode.ROYAL_BLUE,
                onClick = { selectedMode = VirtualBackgroundMode.ROYAL_BLUE },
                leading = {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF204EB9),
                                        Color(0xFF102A71),
                                        Color(0xFF081438)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedMode == VirtualBackgroundMode.ROYAL_BLUE) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            )

            // Opción 4: Cargar fotografía (fondo)
            SelectableBackgroundCard(
                title = stringResource(R.string.bg_option_custom_photo),
                description = if (selectedCustomUri != null) {
                    stringResource(R.string.bg_option_custom_photo_selected)
                } else {
                    stringResource(R.string.bg_option_custom_photo_desc)
                },
                isSelected = selectedMode == VirtualBackgroundMode.CUSTOM,
                onClick = {
                    if (selectedCustomUri != null) {
                        selectedMode = VirtualBackgroundMode.CUSTOM
                    } else {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
                leading = {
                    if (selectedCustomUri != null) {
                        AsyncImage(
                            model = selectedCustomUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (selectedMode == VirtualBackgroundMode.CUSTOM)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                ),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                },
                extraContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedCustomUri != null) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedCustomUri != null)
                                    stringResource(R.string.bg_option_custom_change)
                                else
                                    stringResource(R.string.select_from_gallery),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SelectableBackgroundCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val selectedAnnouncement = if (isSelected) {
        stringResource(R.string.history_accessibility_state_selected)
    } else {
        stringResource(R.string.history_accessibility_state_not_selected)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                stateDescription = selectedAnnouncement
                contentDescription = "$title, $description, $selectedAnnouncement"
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                leading()

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onClick
                )
            }

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}
