# RayoAI - Tu Asistente Visual Accesible

![RayoAI Logo](docs/logo.png) <!-- Placeholder para el logo -->

## 🚀 Visión General

RayoAI es una aplicación móvil Android diseñada para empoderar a personas ciegas o con baja visión, proporcionándoles descripciones detalladas de su entorno a través de la inteligencia artificial. Utilizando el modelo multimodal Google Gemini 2.0 Flash, la aplicación permite a los usuarios capturar imágenes o seleccionarlas de su galería para obtener descripciones de texto precisas y concisas, que luego pueden ser escuchadas, copiadas o compartidas. Además, ofrece una experiencia de chat conversacional sobre la imagen, permitiendo explorar detalles específicos.

## ✨ Características Principales

- **Descripción de Imágenes con IA:** Integra Google Gemini 2.0 Flash para generar descripciones de texto ricas y contextuales de cualquier imagen.
- **Captura de Imágenes Flexible:** Permite tomar fotos con la cámara (frontal/trasera, con temporizador opcional) o cargar desde la galería/compartir de otras apps.
- **Chat Conversacional:** Continúa la conversación sobre una imagen ya descrita, profundizando en detalles específicos.
- **Accesibilidad Prioritaria:** Diseñada desde cero con los principios de WCAG 2.2 AA, asegurando una navegación completa con TalkBack, alto contraste, y escalado de texto.
- **Configuración Personalizable:** Ajusta la clave de API de Gemini, el tema visual (claro, oscuro, alto contraste) y el tamaño del texto.
- **Persistencia Local:** Guarda el historial de descripciones para referencia futura.

## 🏗️ Arquitectura de Software

La aplicación sigue una arquitectura limpia y modular para facilitar el mantenimiento y la escalabilidad:

- **Patrón:** MVVM (Model-View-ViewModel) con Clean Architecture.
- **Capas:**
    - **Presentation:** Interfaz de usuario construida con Jetpack Compose y Material 3, enfocada en la accesibilidad.
    - **Domain:** Contiene la lógica de negocio (casos de uso) independiente de cualquier framework.
    - **Data:** Implementa los repositorios para la interacción con la API de Gemini y la persistencia local (Room para historial, DataStore para preferencias).
- **Inyección de Dependencias:** Hilt.
- **Concurrencia:** Kotlin Coroutines y Flows para operaciones asíncronas.
- **Gestión de Resultados:** Un `ResultWrapper<T>` genérico para manejar estados de `Success`, `Error` y `Loading` de manera consistente.

## 🛠️ Tecnologías Utilizadas

- **Kotlin:** Lenguaje de programación principal.
- **Jetpack Compose:** Toolkit moderno para construir UI nativas de Android.
- **Material 3:** Sistema de diseño para una interfaz de usuario moderna y accesible.
- **Google Gemini 2.0 Flash API:** Para la descripción multimodal de imágenes.
- **CameraX:** Para la integración de la cámara.
- **Room Persistence Library:** Para la base de datos local de historial.
- **Jetpack DataStore:** Para almacenar preferencias de usuario de forma asíncrona y segura.
- **Hilt:** Para la inyección de dependencias.
- **Kotlin Coroutines & Flows:** Para la programación asíncrona y reactiva.
- **Jetpack Security Crypto:** Para el almacenamiento seguro de la API Key.
- **Accompanist Permissions:** Para la gestión simplificada de permisos en Compose.

## ⚙️ Configuración del Entorno de Desarrollo

1.  **Clonar el Repositorio:**
    ```bash
    git clone https://github.com/tu-usuario/RayoAI.git
    cd RayoAI
    ```
2.  **Abrir en Android Studio:**
    Abre el proyecto en Android Studio (versión Flamingo o superior recomendada).
3.  **Obtener una API Key de Google Gemini:**
    -   Visita [Google AI Studio](https://aistudio.google.com/)
    -   Crea un nuevo proyecto o selecciona uno existente.
    -   Genera una nueva API Key.
4.  **Configurar la API Key en la Aplicación:**
    -   Una vez que la aplicación esté instalada en tu dispositivo o emulador, navega a la sección de **Ajustes**.
    -   Introduce tu API Key de Gemini en el campo correspondiente y guárdala. La clave se almacenará de forma segura.

## 🚀 Ejecutar la Aplicación

1.  Conecta un dispositivo Android o inicia un emulador.
2.  Haz clic en el botón `Run` (▶️) en Android Studio.

## 🧪 Pruebas

El proyecto incluye pruebas unitarias e instrumentadas para asegurar la calidad del código.

-   **Pruebas Unitarias:** Ubicadas en `app/src/test/`.
    ```bash
    ./gradlew test
    ```
-   **Pruebas Instrumentadas:** Ubicadas en `app/src/androidTest/`.
    ```bash
    ./gradlew connectedCheck
    ```

## ♿ Accesibilidad (WCAG 2.2 AA)

La accesibilidad es un pilar fundamental de RayoAI. Se han implementado las siguientes directrices:

-   **Navegación con TalkBack:** Todos los elementos interactivos son navegables y anunciados correctamente.
-   **Contenido Descriptivo:** Uso extensivo de `contentDescription` y `Modifier.semantics` para proporcionar contexto a los lectores de pantalla.
-   **Contraste y Temas:** Soporte para temas claro, oscuro y de alto contraste, asegurando un contraste de color mínimo de 4.5:1.
-   **Escalado de Texto:** La interfaz se adapta dinámicamente al tamaño de fuente preferido por el usuario (hasta 1.3x).
-   **Anuncios Dinámicos:** Uso de `LiveRegion` para notificar cambios importantes en la UI a los usuarios de lectores de pantalla.

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Si encuentras un error o tienes una sugerencia de mejora, por favor, abre un *issue* o envía un *pull request*.

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Consulta el archivo `LICENSE` para más detalles.

## 📞 Contacto

Ángel De Jesús Alcántar Garza - [angel.alcantar@example.com](mailto:angel.alcantar@example.com) <!-- Reemplazar con tu email real -->

[GitHub Profile](https://github.com/Aj-Alcantara)
