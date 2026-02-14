# RayoAI - Agent Context & Rules

This file serves as the primary source of truth for AI agents (Antigravity, Gemini CLI, etc.) working on the RayoAI project.

## 🚀 Project Overview

**RayoAI** is a native Android application built with modern development practices. It leverages Google's Gemini AI for intelligent features and prioritizes accessibility and clean architecture.

### Tech Stack
-   **Language**: Kotlin
-   **UI Framework**: Jetpack Compose (Material3)
-   **Architecture**: Clean Architecture (MVVM + Repository Pattern)
-   **Dependency Injection**: Hilt
-   **Async/Concurrency**: Coroutines & Flows
-   **Local Data**: Room Database, DataStore (Preferences)
-   **Remote Data**: Retrofit, Gemini AI client (`google-generativeai`)
-   **Media**: CameraX, Coil (Image Loading)
-   **Permissions**: Accompanist Permissions

### Key Directories
-   `app/src/main/java/com/rayoai/domain/`: **Business Logic** (Models, UseCases, Repository Interfaces).
-   `app/src/main/java/com/rayoai/data/`: **Data Layer** (Repository Impl, API/DB sources).
-   `app/src/main/java/com/rayoai/presentation/`: **UI Layer** (Composables, ViewModels, Themes).
-   `app/src/main/res/values/strings.xml`: **Source of Truth for Text** (Spanish).

---

## 🛡️ Coding Rules & Best Practices

### 1. UI & Accessibility (Critical)
-   **WCAG 2.2 AA Compliance**: All UI elements must be accessible.
-   **TalkBack**: implementation must include correct `contentDescription` and strict `Modifier.semantics` usage.
-   **No Hardcoded Strings**: NEVER hardcode text in Composables.
    -   Add string to `values/strings.xml` (Spanish) first.
    -   Add translation to `values-en/strings.xml` (English) with same key.
    -   Use `stringResource(R.string.key)` in code.
    -   **Important**: Do NOT call `stringResource` inside non-composable lambdas (e.g., `semantics { ... }`).resolve string in the Composable scope first.

### 2. Error Handling & State
-   **ResultWrapper**: Use `com.rayoai.core.ResultWrapper<T>` for all asynchronous operations (Repository/UseCase returns).
    -   States: `Success(data)`, `Error(message)`, `Loading`.
-   **Safe Calls**: Network and DB calls must be wrapped in `try-catch` blocks within the Data layer, mapping exceptions to `ResultWrapper.Error`.

### 3. Architecture Guidelines
-   **Dependency Direction**: `Presentation` -> `Domain` <- `Data`.
-   **Domain Purity**: Domain layer must NOT depend on Android framework classes (where possible).
-   **Hilt**: Use `@HiltViewModel` for ViewModels and `@Inject constructor` for classes. Use `@Module` and `@InstallIn` for interface bindings and third-party types.

### 4. Gemini Integration
-   **API Key**: Managed securely via **Jetpack Security Crypto**. Do not expose keys in code.
-   **Chat**: Ensure responses are parsed correctly to be displayed and stored in history.

---

## ⚙️ Operational Commands

### Build & Run
-   **Clean**: `./gradlew clean`
-   **Debug Build**: `./gradlew assembleDebug`
-   **Release Build**: `./gradlew assembleRelease`
-   **Run on Device**: `./gradlew installDebug`

### Testing
-   **Unit Tests**: `./gradlew test`
-   **Instrumentation**: `./gradlew connectedCheck`

---

## 📝 Workflow for Agents
1.  **Read Context**: before editing, read related files in `domain` and `data` to understand the flow.
2.  **Plan**: If changing UI, check `strings.xml` first.
4.  **Verify**: Ensure strict type safety and null safety (Kotlin).
5.  **Release & Deploy**:
    -   Upon user confirmation of success, ask if they want to create release notes (in **Spanish**) and run deployment.
    -   If yes:
        1.  Create/Overwrite `release-notes.txt` with a summary of changes in Spanish.
        2.  Run `deploy.bat` (this script handles the GitHub release).
