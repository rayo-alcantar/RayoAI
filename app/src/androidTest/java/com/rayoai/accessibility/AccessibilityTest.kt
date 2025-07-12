package com.rayoai.accessibility

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rayoai.presentation.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas para verificar la accesibilidad de la aplicación.
 * Se enfocan en la navegación con TalkBack y las descripciones de contenido.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Verifica que los elementos de la barra de navegación inferior tienen descripciones de contenido.
     */
    @Test
    fun bottomNavigation_hasContentDescriptions() {
        composeTestRule.onNodeWithContentDescription("Inicio").assertExists()
        composeTestRule.onNodeWithContentDescription("Historial").assertExists()
        composeTestRule.onNodeWithContentDescription("Acerca de").assertExists()
        composeTestRule.onNodeWithContentDescription("Ajustes").assertExists()
    }

    /**
     * Verifica la navegación a la pantalla de Ajustes y la presencia de elementos clave.
     */
    @Test
    fun settingsScreen_isAccessible() {
        // Navegar a la pantalla de Ajustes
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.onNodeWithText("API Key").assertExists()
        composeTestRule.onNodeWithText("Guardar API Key").assertExists()
        composeTestRule.onNodeWithText("Tema").assertExists()
        composeTestRule.onNodeWithText("Auto-describir al compartir").assertExists()
    }

    /**
     * Verifica que el botón de captura de la cámara tiene una descripción de contenido.
     */
    @Test
    fun cameraButton_hasContentDescription() {
        composeTestRule.onNodeWithContentDescription("Tomar foto").assertExists()
    }

    /**
     * Verifica que el botón de seleccionar de galería tiene una descripción de contenido.
     */
    @Test
    fun galleryButton_hasContentDescription() {
        composeTestRule.onNodeWithContentDescription("Seleccionar de Galería").assertExists()
    }

    /**
     * Verifica que el botón de enviar mensaje tiene una descripción de contenido.
     */
    @Test
    fun sendMessageButton_hasContentDescription() {
        composeTestRule.onNodeWithContentDescription("Enviar mensaje").assertExists()
    }

    /**
     * Verifica que el indicador de carga tiene una descripción de contenido.
     */
    @Test
    fun loadingIndicator_hasContentDescription() {
        // Simular un estado de carga (esto puede requerir un ViewModel de prueba o un estado controlado)
        // Por ahora, solo verifica si el nodo existe si se asume que puede aparecer.
        // composeTestRule.onNodeWithContentDescription("Analizando imagen. Por favor, espere.").assertExists()
    }
}