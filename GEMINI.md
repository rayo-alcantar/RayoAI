# RayoAI - Información para Gemini CLI

Este archivo contiene información relevante para el agente Gemini CLI para operar de manera más eficiente dentro del proyecto RayoAI.

## 🚀 Información General del Proyecto

*   **Tipo de Proyecto:** Aplicación móvil Android.
*   **Lenguaje Principal:** Kotlin.
*   **Sistema de Construcción:** Gradle (Kotlin DSL).
*   **Framework UI:** Jetpack Compose.
*   **Inyección de Dependencias:** Hilt.
*   **Persistencia Local:** Room (Base de datos), Jetpack DataStore (Preferencias).
*   **API de IA:** Google Gemini 2.0 Flash.
*   **Gestión de Cámara:** CameraX.
*   **Gestión de Permisos:** Accompanist Permissions.
*   **Seguridad:** Jetpack Security Crypto (para API Key).
*   **Concurrencia:** Kotlin Coroutines y Flows.

## 📂 Estructura de Directorios Clave

*   `app/src/main/java/`: Código fuente principal de la aplicación en Kotlin.
    *   `com/rayoai/core/`: Clases utilitarias y genéricas (ej. `ResultWrapper`).
    *   `com/rayoai/data/`: Implementaciones de repositorios, gestión de almacenamiento local (imágenes, DB, DataStore) y lógica de interacción con APIs remotas.
        *   `local/`: Lógica de datos local (DB, DataStore, ImageStorageManager).
        *   `remote/`: Lógica de interacción con APIs externas (Gemini).
        *   `repository/`: Implementaciones de las interfaces de repositorio.
    *   `com/rayoai/di/`: Módulos de Hilt para la inyección de dependencias.
    *   `com/rayoai/domain/`: Lógica de negocio pura, casos de uso, interfaces de repositorios y modelos de dominio.
        *   `model/`: Clases de datos de dominio.
        *   `repository/`: Interfaces de repositorios.
        *   `usecase/`: Casos de uso de la aplicación.
    *   `com/rayoai/presentation/`: Capa de UI (Jetpack Compose Composables, ViewModels, Actividades).
        *   `ui/`: Componentes de UI, pantallas, navegación y temas.
*   `app/src/main/res/`: Recursos de Android (layouts, drawables, values, etc.).
*   `app/src/androidTest/`: Pruebas de instrumentación.
*   `app/src/test/`: Pruebas unitarias.
*   `build.gradle.kts`: Archivo de configuración de Gradle a nivel de proyecto.
*   `app/build.gradle.kts`: Archivo de configuración de Gradle a nivel de módulo de la aplicación.
*   `settings.gradle.kts`: Configuración de los módulos del proyecto Gradle.
*   `gradle.properties`: Propiedades globales de Gradle.

## ⚙️ Comandos Útiles de Gradle

*   **Limpiar el proyecto:**
    ```bash
    ./gradlew clean
    ```
*   **Ejecutar pruebas unitarias:**
    ```bash
    ./gradlew test
    ```
*   **Ejecutar pruebas de instrumentación (requiere dispositivo/emulador conectado):**
    ```bash
    ./gradlew connectedCheck
    ```
*   **Construir el proyecto:**
    ```bash
    ./gradlew assembleDebug # Para una construcción de depuración
    ./gradlew assembleRelease # Para una construcción de lanzamiento
    ```
*   **Instalar la aplicación en un dispositivo conectado (versión debug):**
    ```bash
    ./gradlew installDebug
    ```

## 💡 Consideraciones Adicionales

*   **API Key de Gemini:** La aplicación requiere una API Key de Google Gemini para funcionar. Esta se configura dentro de la aplicación en la pantalla de Ajustes y se almacena de forma segura.
*   **Accesibilidad:** La aplicación está diseñada con un fuerte enfoque en la accesibilidad (WCAG 2.2 AA). Al realizar cambios en la UI, es crucial considerar `contentDescription` y `Modifier.semantics` para TalkBack.
*   **Manejo de Errores:** Las operaciones asíncronas utilizan `ResultWrapper` para manejar los estados de `Loading`, `Success` y `Error` de manera consistente.
*   **Corrección de Chat:** Se ha corregido un problema donde las respuestas del modelo Gemini no se mostraban ni se guardaban en el historial debido a un análisis incorrecto de la respuesta de la API. Ahora, el chat funciona correctamente y las respuestas se procesan y almacenan adecuadamente.
*   **Contexto de Archivos:** Al modificar archivos, siempre es recomendable leer el contexto circundante para mantener la coherencia del código y el estilo.
A demás de que siempre que vayas a añadir texto que el usuario verá, asegúrate de no harcodearlo en el código, si no que añadir las referencias en strings.xml y en values-en/strings.xml.
la aplicación inicialmente está en español.
