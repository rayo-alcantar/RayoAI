// build.gradle.kts (a nivel de proyecto)

plugins {
    // Android Gradle Plugin estable
    id("com.android.application") version "8.6.0" apply false
    // Kotlin estable
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Hilt estable
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    // KSP compatible con Kotlin 1.9.24
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Compilador de Compose compatible con Kotlin 1.9.24
}