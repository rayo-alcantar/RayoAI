package com.rayoai.presentation.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rayoai.R
import com.rayoai.core.VirtualBackgroundMode

/**
 * Pantalla dedicada para elegir el fondo fotográfico.
 * Diseñada bajo WCAG 2.2 AA para lectores de pantalla (TalkBack):
 * cada tipo de fondo es una casilla seleccionable unificada que se lee en un único flick.
 * Visualmente muestra un cuadro con el color o degradado fotográfico correspondiente.
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .selectableGroup(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.select_background_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Opción 1: Sin fondo (Original)
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_none),
                    description = stringResource(R.string.bg_option_none_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.NONE,
                    onClick = { selectedMode = VirtualBackgroundMode.NONE },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
            }

            // Opción 2: Fondo claro (Blanco)
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_white_name),
                    description = stringResource(R.string.bg_option_white_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.WHITE,
                    onClick = { selectedMode = VirtualBackgroundMode.WHITE },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFFFFFFF),
                                            Color(0xFFF8FAFD),
                                            Color(0xFFEEF2F8)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        )
                    }
                )
            }

            // Opción 3: Fondo oscuro (Azul rey)
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_royal_blue_name),
                    description = stringResource(R.string.bg_option_royal_blue_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.ROYAL_BLUE,
                    onClick = { selectedMode = VirtualBackgroundMode.ROYAL_BLUE },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF264E92),
                                            Color(0xFF163462),
                                            Color(0xFF0C1A34)
                                        )
                                    )
                                )
                        )
                    }
                )
            }

            // Opción 4: Gris carbón
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_charcoal_name),
                    description = stringResource(R.string.bg_option_charcoal_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.CHARCOAL_GRAY,
                    onClick = { selectedMode = VirtualBackgroundMode.CHARCOAL_GRAY },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF414144),
                                            Color(0xFF262628),
                                            Color(0xFF141416)
                                        )
                                    )
                                )
                        )
                    }
                )
            }

            // Opción 5: Verde esmeralda
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_emerald_name),
                    description = stringResource(R.string.bg_option_emerald_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.EMERALD_GREEN,
                    onClick = { selectedMode = VirtualBackgroundMode.EMERALD_GREEN },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF206948),
                                            Color(0xFF12442D),
                                            Color(0xFF0A2418)
                                        )
                                    )
                                )
                        )
                    }
                )
            }

            // Opción 6: Beige cálido
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_warm_beige_name),
                    description = stringResource(R.string.bg_option_warm_beige_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.WARM_BEIGE,
                    onClick = { selectedMode = VirtualBackgroundMode.WARM_BEIGE },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFF0E6D8),
                                            Color(0xFFDACAB6),
                                            Color(0xFFBAA892)
                                        )
                                    )
                                )
                        )
                    }
                )
            }

            // Opción 7: Borgoña
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_burgundy_name),
                    description = stringResource(R.string.bg_option_burgundy_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.BURGUNDY,
                    onClick = { selectedMode = VirtualBackgroundMode.BURGUNDY },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF782030),
                                            Color(0xFF52121E),
                                            Color(0xFF2C0A10)
                                        )
                                    )
                                )
                        )
                    }
                )
            }

            // Opción 8: Cian suave
            item {
                SelectableBackgroundSwatch(
                    title = stringResource(R.string.bg_option_soft_cyan_name),
                    description = stringResource(R.string.bg_option_soft_cyan_desc),
                    isSelected = selectedMode == VirtualBackgroundMode.SOFT_CYAN,
                    onClick = { selectedMode = VirtualBackgroundMode.SOFT_CYAN },
                    swatchContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF50AFC8),
                                            Color(0xFF2D829B),
                                            Color(0xFF164B5F)
                                        )
                                    )
                                )
                        )
                    }
                )
            }

            // Opción 9: Fondo personalizado (si hay una imagen cargada)
            if (selectedCustomUri != null) {
                item {
                    SelectableBackgroundSwatch(
                        title = stringResource(R.string.bg_option_custom_photo),
                        description = stringResource(R.string.bg_option_custom_photo_selected),
                        isSelected = selectedMode == VirtualBackgroundMode.CUSTOM,
                        onClick = { selectedMode = VirtualBackgroundMode.CUSTOM },
                        swatchContent = {
                            AsyncImage(
                                model = selectedCustomUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    )
                }
            }

            // Botón para añadir o cambiar fondo personalizado
            item {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedCustomUri != null) {
                            stringResource(R.string.bg_option_custom_change)
                        } else {
                            stringResource(R.string.bg_add_custom_button)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Casilla de selección accesible para TalkBack.
 * Emplea `mergeDescendants = true` y `Modifier.selectable(role = Role.RadioButton)`
 * para consolidar toda la información y estado en un único elemento interactivo.
 * Esto elimina los múltiples saltos/flicks que TalkBack daba previamente en el mismo control.
 */
@Composable
private fun SelectableBackgroundSwatch(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    swatchContent: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
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
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .semantics(mergeDescendants = true) {
                // TalkBack anuncia: "[title], [description], seleccionado/no seleccionado, botón de selección"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cuadro visual que muestra el color o degradado
            swatchContent()

            // Texto descriptivo
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

            // Indicador visual de selección (sin onClick propio para evitar saltos en TalkBack)
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
