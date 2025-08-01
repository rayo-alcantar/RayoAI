plugins {
    // Plugins de Android
    id("com.android.application") version "8.4.0" apply false
    id("com.android.library") version "8.2.0" apply false

    // Plugins de Kotlin
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false

    // Plugins de Hilt
    id("com.google.dagger.hilt.android") version "2.51.1" apply false

    // Plugins de KSP (Kotlin Symbol Processing)
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}