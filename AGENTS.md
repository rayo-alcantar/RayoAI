# RayoAI Android — Agent Guide

## Scope and architecture

Native Kotlin Android app using Compose/Material3, Hilt, coroutines/Flow, Room, DataStore, Gemini, and CameraX. Read `app/build.gradle.kts` for current SDKs, dependencies, versions, and distribution flavors; do not copy version snapshots into instructions.

- `app/src/main/java/com/rayoai/domain/`: models, use cases, repository contracts.
- `app/src/main/java/com/rayoai/data/`: persistence and remote implementations.
- `app/src/main/java/com/rayoai/presentation/`: Compose UI and ViewModels.
- `app/src/main/java/com/rayoai/core/ResultWrapper.kt`: existing Success/Error/Loading contract.
- Keep presentation dependent on domain contracts and data implementing them. Avoid Android dependencies in domain where practical; use existing Hilt injection patterns rather than adding another architecture.

## UI, state, and security

- Accessibility is a requirement, not a claim proven by compilation: check TalkBack reading order, focus, labels, actions, and state announcements for changed screens; aim for WCAG 2.2 AA.
- Put Spanish UI text in `app/src/main/res/values/strings.xml` and matching English keys in `app/src/main/res/values-en/strings.xml`. Resolve `stringResource` in composable scope before using the value in non-composable callbacks such as `semantics`.
- Preserve natural Spanish Unicode in existing documentation and release notes.
- Follow the surrounding repository/use-case ResultWrapper and Flow contracts; map operational failures in the data layer without swallowing coroutine cancellation.
- Keep API keys in the existing secure-storage path. Never expose keys, signing material, user media, or conversation history in commits or diagnostics.
- Treat `keystore.properties` and signing files as private local inputs; do not print or replace them.

## Validation and delivery

Run commands from the repository root only when that validation is in scope. Windows uses `gradlew.bat`; other platforms use `./gradlew`.

- Unit checks: `./gradlew.bat test`.
- Device/emulator checks: `./gradlew.bat connectedCheck`; require an available device and are separate from unit checks.
- The Android application plugin and `github`/`play` flavors are declared in `app/build.gradle.kts`. Inspect the intended flavor before a requested build/install; do not silently build every distribution.
- Gradle configuration reads release signing properties even before a release task. If private local prerequisites are missing, report the limitation; never invent credentials or claim tests ran.
- Builds, device installation, and publication need their own authorization. Do not run `clean` as routine validation.
- `deploy.bat` queues the coordinated publication workflow using an original Actions artifact run ID; it is NOT a harmless check. Only run after explicit publication approval for the version/channel/destinations.
- Write release notes in Spanish only when requested. Report the checks actually performed and keep commit, push, build, and release separate.

## Coordinated distribution

- Read `docs/publicacion-android.md` before changing or executing release automation. Both distribution variants share the source version and package ID; preserve their different donation/updater flags.
- Release tooling checks: `python -m unittest discover -s scripts/tests -v`. These use synthetic artifacts and a local HTTP server, not Google credentials or live publication.
- `verify_play_access=true` on the release workflow checks real Play access using a temporary edit and its deletion, without builds, uploads, track changes or edit commits. It still requires authorized Google access; do not run it concurrently with a manual edit from the same service account.
- `release.yml` builds the GitHub APK and Play AAB as one verified pair. `publish-destination.yml` publishes selected destinations from that immutable Actions artifact. Reuse the original run ID; do not rebuild or move a published tag to recover from a store failure.
- `deploy.bat` now queues the coordinated workflow with explicit named options and an original run ID. It does not directly upload a local APK or overwrite public assets. Invoking it is still a publication request, not a harmless check.
- Play submission success is not proof of approval or user availability. Never cancel an existing review, overwrite another draft/rollout, or enable production access without authorization.
