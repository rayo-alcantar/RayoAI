package com.rayoai.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.domain.repository.ThemeMode
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.presentation.ui.screens.settings.SettingsViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider

// Esquema de colores oscuro predeterminado.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Esquema de colores claro predeterminado.
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Otros colores predeterminados para sobrescribir
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Composable que define el tema de la aplicación RayoAI.
 * Aplica el esquema de colores, la tipografía y el escalado de texto según las preferencias del usuario.
 * @param userPreferencesRepository Repositorio para acceder a las preferencias del usuario.
 * @param content El contenido Composable al que se aplicará el tema.
 */
@Composable
fun RayoAITheme(
    // Se inyecta el UserPreferencesRepository a través del ViewModel de Settings para acceder a las preferencias.
    userPreferencesRepository: UserPreferencesRepository = hiltViewModel<SettingsViewModel>().userPreferencesRepository,
    content: @Composable () -> Unit
) {
    // Recolecta el modo de tema y la escala de texto de las preferencias del usuario.
    val themeMode by userPreferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val textScale by userPreferencesRepository.textScale.collectAsState(initial = 1.0f)

    // Determina si el tema debe ser oscuro basado en el modo de tema seleccionado y el tema del sistema.
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.HIGH_CONTRAST -> isSystemInDarkTheme() // Para alto contraste, sigue el tema del sistema (oscuro/claro).
    }

    // Selecciona el esquema de colores adecuado.
    val colorScheme = when {
        themeMode == ThemeMode.HIGH_CONTRAST -> {
            // TODO: Definir un esquema de colores de alto contraste específico.
            // Por ahora, usa el esquema oscuro o claro predeterminado.
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
        // Si el dispositivo soporta colores dinámicos (Android 12+), usarlos.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Si no hay colores dinámicos, usar los esquemas predeterminados.
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Configurar la barra de estado del sistema para que coincida con el tema.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    // Aplicar el escalado de texto personalizado.
    val currentDensity = LocalDensity.current
    val scaledDensity = remember(textScale) {
        // Crea una nueva Density con la escala de fuente ajustada.
        Density(density = currentDensity.density, fontScale = currentDensity.fontScale * textScale)
    }

    // Aplica el tema de Material Design a la jerarquía de Composable.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            // Proporciona la Density escalada a todos los Composable hijos.
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                content()
            }
        }
    )
}
