# RayoAI - Tu Asistente Visual Accesible (Documentación Técnica)

![RayoAI Logo](docs/logo.png) <!-- Placeholder para el logo -->

## 🚀 Visión General

RayoAI es una aplicación móvil Android diseñada para apoyar a personas ciegas o con baja visión, proporcionándoles descripciones detalladas de su entorno a través de la inteligencia artificial. Utilizando el modelo multimodal Google Gemini 2.5 Flash, la aplicación permite a los usuarios capturar imágenes o seleccionarlas de su galería para obtener descripciones de texto precisas y concisas, que luego pueden ser escuchadas, copiadas o compartidas. Además, ofrece una experiencia de chat conversacional sobre la imagen, permitiendo explorar detalles específicos.

## ✨ Características Principales

- **Descripción de Imágenes con IA:** Integra Google Gemini 2.5 Flash para generar descripciones de texto ricas y contextuales de cualquier imagen.
- **Captura de Imágenes Flexible:** Permite tomar fotos con la cámara (frontal/trasera, con temporizador opcional) o cargar desde la galería/compartir de otras apps.
- **Chat Conversacional:** Continúa la conversación sobre una imagen ya descrita, profundizando en detalles específicos.
- **Accesibilidad Prioritaria:** Diseñada desde cero con los principios de WCAG 2.2 AA, asegurando una navegación completa con TalkBack, alto contraste, y escalado de texto.
- **Configuración Personalizable:** Ajusta la clave de API de Gemini, el tema visual (claro, oscuro, alto contraste) y el tamaño del texto.
- **Persistencia Local:** Guarda el historial de descripciones para referencia futura.

## 🏗️ Arquitectura de Software

La aplicación sigue una arquitectura limpia y modular para facilitar el mantenimiento y la escalabilidad, basada en el patrón **MVVM (Model-View-ViewModel)** y los principios de **Clean Architecture**.

### Desglose de Capas y Componentes:

1.  **Capa de Presentación (`app/src/main/java/com/rayoai/presentation`)**
    *   **Responsabilidad:** Maneja la interfaz de usuario (UI) y la interacción con el usuario. Observa los datos del ViewModel y actualiza la UI en consecuencia. Envía eventos de usuario al ViewModel.
    *   **Tecnologías:**
        *   **Jetpack Compose:** Toolkit moderno y declarativo para construir la UI nativa de Android.
        *   **Material 3:** Sistema de diseño que proporciona componentes UI y directrices para una experiencia de usuario moderna y accesible.
        *   **`MainActivity.kt`:** El punto de entrada principal de la aplicación, responsable de inicializar el motor de Text-to-Speech y configurar la navegación principal.
        *   **`SharingActivity.kt`:** Actividad auxiliar para manejar Intents de compartir imágenes desde otras aplicaciones, redirigiéndolas a `MainActivity`.
        *   **`ui/navigation/AppNavigation.kt`:** Define el grafo de navegación de la aplicación utilizando Jetpack Compose Navigation, incluyendo las rutas y los argumentos de navegación.
        *   **`ui/screens/`:** Contiene los Composables que representan las diferentes pantallas de la aplicación (Home, History, Settings, About, Welcome, ApiInstructions). Cada pantalla suele tener un Composable principal y un ViewModel asociado.
        *   **`ui/components/`:** Contiene Composables reutilizables (ej. `CameraView`, `ChatBubble`, `SecureTextField`).
        *   **`ui/theme/`:** Define los temas de la aplicación (colores, tipografía) y la lógica para aplicar el escalado de texto y los modos de tema.
        *   **`LocalTextToSpeech.kt`:** Un `CompositionLocal` para proporcionar la instancia de `TextToSpeech` a través del árbol de Composables.
    *   **Conexión:** Los Composables observan los `StateFlow` y `SharedFlow` expuestos por los ViewModels. Los ViewModels, a su vez, invocan los Casos de Uso de la capa de Dominio.

2.  **Capa de Dominio (`app/src/main/java/com/rayoai/domain`)**
    *   **Responsabilidad:** Contiene la lógica de negocio pura de la aplicación. Es independiente de cualquier framework o tecnología específica de Android. Define las interfaces de los repositorios y los casos de uso.
    *   **Componentes:**
        *   **`model/ChatMessage.kt`:** Clase de datos que representa un mensaje en el chat.
        *   **`repository/UserPreferencesRepository.kt`:** Interfaz que define las operaciones para gestionar las preferencias del usuario (API Key, tema, escala de texto).
        *   **`repository/VisionRepository.kt`:** Interfaz que define las operaciones para interactuar con el modelo de visión (Gemini).
        *   **`usecase/`:** Contiene las clases de casos de uso (ej. `DescribeImageUseCase`, `ContinueChatUseCase`, `SaveCaptureUseCase`). Cada caso de uso encapsula una operación de negocio específica y orquesta la interacción entre uno o más repositorios.
    *   **Conexión:** Los ViewModels (Capa de Presentación) invocan los Casos de Uso. Los Casos de Uso utilizan las interfaces de los Repositorios para realizar operaciones, sin conocer las implementaciones concretas.

3.  **Capa de Datos (`app/src/main/java/com/rayoai/data`)**
    *   **Responsabilidad:** Implementa las interfaces de los repositorios definidas en la capa de Dominio. Es responsable de la obtención, almacenamiento y gestión de los datos, ya sea desde fuentes locales (base de datos, DataStore) o remotas (API de Gemini).
    *   **Componentes:**
        *   **`local/ImageStorageManager.kt`:** Clase que gestiona el almacenamiento de imágenes en el dispositivo, incluyendo el guardado en la galería y la obtención de URIs seguras.
        *   **`local/datastore/`:** (No hay archivos directos aquí, pero se refiere al uso de DataStore).
        *   **`local/db/`:**
            *   **`CaptureDao.kt`:** Interfaz DAO (Data Access Object) para interactuar con la base de datos Room para las capturas.
            *   **`RayoAIDatabase.kt`:** Clase abstracta que define la base de datos Room y expone el DAO.
        *   **`local/model/CaptureEntity.kt`:** Clase de datos que representa una entidad de captura para la base de datos Room.
        *   **`remote/gemini/`:** (No hay archivos directos aquí, pero se refiere a la interacción con la API de Gemini).
        *   **`repository/UserPreferencesRepositoryImpl.kt`:** Implementación concreta de `UserPreferencesRepository` utilizando Jetpack DataStore para almacenar las preferencias del usuario.
        *   **`repository/VisionRepositoryImpl.kt`:** Implementación concreta de `VisionRepository` que interactúa directamente con la API de Google Gemini para generar descripciones y respuestas de chat.
    *   **Conexión:** Las implementaciones de los Repositorios son inyectadas en los Casos de Uso (Capa de Dominio) a través de Hilt.

### Inyección de Dependencias

*   **Hilt:** Se utiliza para la inyección de dependencias, facilitando la gestión de las instancias de las clases y sus dependencias a lo largo de la aplicación.
    *   **`RayoAIApp.kt`:** La clase `Application` anotada con `@HiltAndroidApp` para habilitar la inyección de Hilt.
    *   **`di/AppModule.kt`:** Módulos de Hilt que proporcionan las dependencias necesarias (ej. `DataStore`, `RoomDatabase`, implementaciones de repositorios) a los constructores de las clases.

### Gestión de Concurrencia y Estado

*   **Kotlin Coroutines y Flows:** Se utilizan extensivamente para operaciones asíncronas y reactivas, como la interacción con la API de Gemini, la base de datos y DataStore.
    *   `Flow<ResultWrapper<T>>`: Un patrón común para exponer el estado de las operaciones (Loading, Success, Error) de manera reactiva desde la capa de datos hasta la UI.
*   **`core/ResultWrapper.kt`:** Una clase sellada genérica (`sealed class`) que encapsula los posibles estados de un resultado (Success, Error, Loading), proporcionando una forma consistente de manejar los estados de las operaciones asíncronas en toda la aplicación.

## 🛠️ Tecnologías Utilizadas

- **Kotlin:** Lenguaje de programación principal.
- **Jetpack Compose:** Toolkit moderno para construir UI nativas de Android.
- **Material 3:** Sistema de diseño para una interfaz de usuario moderna y accesible.
- **Google Gemini 2.5 Flash API:** Para la descripción multimodal de imágenes.
- **CameraX:** Para la integración de la cámara.
- **Room Persistence Library:** Para la base de datos local de historial.
- **Jetpack DataStore:** Para almacenar preferencias de usuario de forma asíncrona y segura.
- **Hilt:** Para la inyección de dependencias.
- **Kotlin Coroutines & Flows:** Para la programación asíncrona y reactiva.
- **Jetpack Security Crypto:** Para el almacenamiento seguro de la API Key.
- **Accompanist Permissions:** Para la gestión simplificada de permisos en Compose.
- **Coil:** Para la carga eficiente de imágenes.

## ⚙️ Configuración del Entorno de Desarrollo

Para que el proyecto funcione correctamente, sigue estos pasos:

1.  **Clonar el Repositorio:**
    ```bash
    git clone https://github.com/rayo-alcantar/RayoAI.git
    cd RayoAI
    ```
2.  **Abrir en Android Studio:**
    Abre el proyecto en Android Studio (versión Flamingo o superior recomendada). Android Studio configurará automáticamente el proyecto Gradle.
3.  **Obtener una API Key de Google Gemini:**
    -   Visita [Google AI Studio](https://aistudio.google.com/)
    -   Crea un nuevo proyecto o selecciona uno existente.
    -   Genera una nueva API Key.
4.  **Configurar la API Key en la Aplicación:**
    -   Una vez que la aplicación esté instalada en tu dispositivo o emulador, navega a la sección de **Ajustes**.
    -   Introduce tu API Key de Gemini en el campo correspondiente y guárdala. La clave se almacenará de forma segura utilizando Jetpack Security Crypto.

## 🚀 Ejecutar la Aplicación

1.  Conecta un dispositivo Android o inicia un emulador.
2.  Haz clic en el botón `Run` (▶️) en Android Studio. Gradle construirá el proyecto y desplegará la aplicación en el dispositivo/emulador.

## 🧪 Pruebas

El proyecto incluye pruebas unitarias e instrumentadas para asegurar la calidad del código.

-   **Pruebas Unitarias:** Ubicadas en `app/src/test/`. Se enfocan en la lógica de negocio y las capas de dominio/datos.
    ```bash
    ./gradlew test
    ```
-   **Pruebas Instrumentadas:** Ubicadas en `app/src/androidTest/`. Se ejecutan en un dispositivo o emulador Android y verifican la funcionalidad de la UI y la integración de componentes.
    ```bash
    ./gradlew connectedCheck
    ```

## ♿ Accesibilidad (WCAG 2.2 AA)

La accesibilidad es un pilar fundamental de RayoAI. Se han implementado las siguientes directrices:

-   **Navegación con TalkBack:** Todos los elementos interactivos son navegables y anunciados correctamente mediante el uso de `contentDescription` y `Modifier.semantics`.
-   **Contenido Descriptivo:** Uso extensivo de `contentDescription` y `Modifier.semantics` para proporcionar contexto a los lectores de pantalla, especialmente para elementos visuales.
-   **Contraste y Temas:** Soporte para temas claro, oscuro y de alto contraste, asegurando un contraste de color mínimo de 4.5:1 para la legibilidad.
-   **Escalado de Texto:** La interfaz se adapta dinámicamente al tamaño de fuente preferido por el usuario (hasta 1.3x), gestionado a través de `LocalDensity` en el tema de Compose.
-   **Anuncios Dinámicos:** Uso de `LiveRegion` (implícito en algunos componentes de Compose o explícito con `semantics`) para notificar cambios importantes en la UI a los usuarios de lectores de pantalla.

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Si encuentras un error o tienes una sugerencia de mejora, por favor, abre un *issue* o envía un *pull request*.

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Consulta el archivo `LICENSE` para más detalles.

## 📞 Contacto

Ángel De Jesús Alcántar Garza - [contacto@rayoscompany.com](mailto:contacto@rayoscompany.com) <!-- Reemplazar con tu email real -->
