# Documentación Técnica del Sistema de Actualizaciones

Este documento describe en detalle el funcionamiento del sistema de actualizaciones dentro de la aplicación, diseñado para ser replicado en otros proyectos de Android. El sistema utiliza la API de GitHub Releases como fuente de verdad para las nuevas versiones y gestiona el proceso completo, desde la comprobación hasta la instalación.

## 1. Vistazo General del Flujo

El proceso de actualización se puede resumir en los siguientes pasos:

1.  **Comprobación Automática**: Al iniciar la aplicación, se realiza una comprobación silenciosa contra la API de GitHub Releases.
2.  **Diálogo de Actualización**: Si se encuentra una versión más nueva, se presenta un diálogo al usuario con el número de versión y el changelog.
3.  **Descarga en Segundo Plano**: Si el usuario acepta, la descarga del archivo APK se delega al `DownloadManager` del sistema Android, mostrando el progreso en la barra de notificaciones.
4.  **Notificación de Instalación**: Una vez completada la descarga, se muestra una notificación persistente que, al ser presionada, inicia el instalador de paquetes de Android.
5.  **Instalación**: El usuario confirma la instalación a través de la interfaz del sistema operativo.
6.  **Limpieza**: Tras una actualización exitosa, la aplicación limpia el APK descargado y las referencias guardadas.

## 2. Componentes Clave

El sistema está modularizado en varios componentes que interactúan entre sí.

| Componente                                   | Ubicación                                              | Responsabilidad                                                                                                     |
| -------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| **GithubApiService**                         | `data/remote/GithubApiService.kt`                      | Define la interfaz de Retrofit para consultar el endpoint de releases de la API de GitHub.                          |
| **UpdateRepository**                         | `data/repository/UpdateRepository.kt`                  | Orquesta la lógica de comprobación, contacta la API, compara versiones y selecciona la mejor versión candidata.     |
| **UpdateCheckViewModel**                     | `ui/updates/UpdateCheckViewModel.kt`                   | Gestiona el estado de la UI para la comprobación de actualizaciones y dispara el proceso al iniciar.              |
| **UpdateFlowDialog**                         | `ui/updates/UpdateFlowDialog.kt`                       | Composable de Jetpack Compose que muestra el diálogo de "nueva versión disponible" y el progreso de la descarga.    |
| **UpdateInstaller**                          | `ui/updates/UpdateInstaller.kt`                        | Objeto que encapsula la lógica para iniciar la descarga con `DownloadManager` y construir el `Intent` de instalación. |
| **UpdateDownloadReceiver**                   | `ui/updates/UpdateDownloadReceiver.kt`                 | `BroadcastReceiver` que escucha cuando `DownloadManager` completa una descarga y dispara la notificación de instalación. |
| **UpdatePreferences**                        | `data/local/UpdatePreferences.kt`                      | Gestiona el almacenamiento local (usando `SharedPreferences`) del canal de actualización y los datos de la descarga pendiente. |
| **AndroidManifest.xml**                      | `app/src/main/AndroidManifest.xml`                     | Declara los permisos necesarios (`REQUEST_INSTALL_PACKAGES`) y registra el `UpdateDownloadReceiver`.                |

## 3. Proceso Detallado

### 3.1. Configuración y Comprobación

-   **Disparador**: El proceso comienza en el `init` de `UpdateCheckViewModel`, que llama a `checkSilentlyOnStart`. Esto significa que la comprobación se realiza tan pronto como el ViewModel es creado por primera vez (generalmente al navegar a la pantalla principal).
-   **Fuente de Datos**: `UpdateRepository` contiene la configuración del repositorio de GitHub (hardcodeada en este caso):
    -   `owner = "rayo-alcantar"`
    -   `repo = "mlf_for_android"`
-   **Llamada a la API**: Usando `GithubApiService`, se realiza una petición GET a `repos/{owner}/{repo}/releases`.

### 3.2. Selección de la Versión Candidata

Una vez obtenida la lista de releases, `UpdateRepository.selectCandidate` aplica la siguiente lógica:

1.  Filtra los releases que son `draft`.
2.  Filtra los releases que no contienen un asset `.apk`.
3.  Separa los releases en dos grupos: `stable` (aquellos donde `isPrerelease` es `false`) y `beta` (`isPrerelease` es `true`).
4.  Basado en el `UpdateChannel` seleccionado por el usuario (guardado en `UpdatePreferences`), selecciona el release más reciente:
    -   `STABLE`: El release no-prerelease con la versión más alta.
    -   `BETA`: El release prerelease con la versión más alta.
    -   `ALL`: Compara el mejor estable y el mejor beta y elige el más reciente de los dos.

La comparación de versiones (`compareVersions`) es robusta: extrae los segmentos numéricos de los tags (ej. "v1.10.2" -> `[1, 10, 2]`) y los compara numéricamente.

### 3.3. Interfaz de Usuario y Descarga

-   Si `UpdateRepository` devuelve una `UpdateInfo` (una versión más nueva disponible), `UpdateCheckViewModel` actualiza su `StateFlow`.
-   `UpdateFlowDialog` (o un composable similar) observa este estado y se muestra automáticamente. El diálogo presenta el `version` y `changelog` (extraído del `body` del release de GitHub).
-   Al hacer clic en "Descargar", se llama a `UpdateInstaller.downloadUpdate`.
-   **Limpieza Previa**: Antes de iniciar una nueva descarga, el sistema busca si hay una descarga anterior pendiente en `UpdatePreferences`. Si existe, intenta cancelarla y eliminarla del `DownloadManager`.
-   **Cola de Descarga**: Se utiliza `DownloadManager.enqueue()` para iniciar la descarga. Esto delega toda la gestión de red al sistema operativo. El archivo se guarda en el directorio público de descargas con el nombre `{version}.apk`.
-   **Persistencia**: El ID de la descarga y la versión se guardan en `UpdatePreferences` usando `savePendingUpdate`.

### 3.4. Instalación

1.  **Receptor de Eventos**: El `UpdateDownloadReceiver` está registrado en el `AndroidManifest.xml` para escuchar la acción `android.intent.action.DOWNLOAD_COMPLETE`.
2.  **Verificación**: Cuando se recibe el broadcast, el receptor comprueba si el `downloadId` completado coincide con el que está guardado en `UpdatePreferences`.
3.  **Notificación**: Si coinciden, obtiene la URI del archivo descargado y crea una notificación para el usuario.
    -   **Título**: "Instalación lista ({version})"
    -   **Texto**: "Pulsa para instalar la nueva versión."
    -   **Acción**: La notificación tiene un `PendingIntent` que, al ser presionado, ejecuta el `Intent` de instalación.
4.  **Intent de Instalación**: `UpdateInstaller.buildInstallIntent` crea un `Intent` con:
    -   `ACTION_VIEW`
    -   `data` y `type`: La URI del APK y el MIME type `application/vnd.android.package-archive`.
    -   `FLAG_ACTIVITY_NEW_TASK` y `FLAG_GRANT_READ_URI_PERMISSION`.
5.  **Permisos**: Para que este `Intent` funcione en Android 8 (Oreo) y superior, la aplicación debe tener el permiso `REQUEST_INSTALL_PACKAGES`. El `UpdateFlowDialog` incluye lógica para guiar al usuario a la pantalla de configuración del sistema (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`) si este permiso no está concedido.

### 3.5. Limpieza Post-Instalación

El sistema incluye un mecanismo de autolimpieza en `UpdateInstaller.maybeCleanupAfterUpdate`.

-   **Cuándo se ejecuta**: Esta función está diseñada para ser llamada al inicio de la aplicación.
-   **Lógica**:
    1.  Obtiene la información de la descarga pendiente de `UpdatePreferences`.
    2.  Compara la versión actual de la app (`BuildConfig.VERSION_NAME`) con la versión de la descarga pendiente.
    3.  Si la versión actual es igual o más nueva, significa que la actualización se completó con éxito.
    4.  Procede a eliminar el `downloadId` del `DownloadManager` (lo que borra el archivo APK) y limpia los datos de `UpdatePreferences`. También cancela la notificación de instalación si todavía estuviera visible.

## 4. Guía de Replicación

Para adaptar este sistema a otra aplicación, sigue estos pasos:

1.  **Copiar Archivos**: Copia todos los archivos listados en la tabla de "Componentes Clave" a tu nuevo proyecto, ajustando los nombres de paquete.
2.  **Configurar Repositorio**: En `UpdateRepository.kt`, cambia las variables `owner` y `repo` para que apunten a tu repositorio de GitHub.
3.  **Añadir Permisos**: Asegúrate de que tu `AndroidManifest.xml` contenga los siguientes permisos:
    ```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    ```
4.  **Registrar el Receiver**: Registra `UpdateDownloadReceiver` en tu `AndroidManifest.xml` dentro de la etiqueta `<application>`:
    ```xml
    <receiver
        android:name=".ui.updates.UpdateDownloadReceiver"
        android:exported="true"
        android:permission="android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS">
        <intent-filter>
            <action android:name="android.intent.action.DOWNLOAD_COMPLETE" />
        </intent-filter>
    </receiver>
    ```
5.  **Integrar la UI**:
    -   Inyecta `UpdateCheckViewModel` en tu pantalla principal o en un composable de alto nivel.
    -   Llama a un composable (como `UpdateFlowDialog`) que observe el `state` del ViewModel y se muestre cuando `updateAvailable` no sea nulo.
6.  **Llamar a la Limpieza**: En el `onCreate` de tu clase `Application` o en el `init` de un ViewModel principal, realiza una llamada a `UpdateInstaller.maybeCleanupAfterUpdate(context)`. Esto es crucial para no dejar archivos APK huérfanos.
7.  **Dependencias**: Asegúrate de que tu `build.gradle.kts` incluye las dependencias necesarias (Retrofit, Hilt para inyección de dependencias, etc.).
8.  **Estrategia de Releases en GitHub**:
    -   Crea releases en tu repositorio de GitHub.
    -   Usa tags que sigan un versionado semántico (ej. `v1.2.3`). El sistema de comparación de versiones depende de esto.
    -   Adjunta el `.apk` compilado como un asset a cada release.
    -   Usa el flag "pre-release" de GitHub para marcar las versiones beta.
    -   Rellena el campo "body" del release con el changelog; se mostrará directamente al usuario.

Siguiendo estos pasos, tendrás un sistema de actualizaciones robusto y completamente funcional basado en GitHub.