import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android app and the desktop (Windows / macOS) app:
// API client, models, repository, theme and the Compose design components.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    sourceSets {
        // Both targets run on the JVM, so common code may use JVM libraries (OkHttp, java.io…).
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.animation)
            api(compose.ui)
            api(compose.material3)
            api(compose.components.resources)
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            api("com.squareup.okhttp3:okhttp:4.12.0")
            api("com.composables:icons-lucide:1.1.0")
            api("dev.chrisbanes.haze:haze:1.6.0")
            api("io.coil-kt.coil3:coil-compose:3.2.0")
            api("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("androidx.datastore:datastore-preferences:1.1.7")
            implementation("androidx.core:core-ktx:1.16.0")
        }
        val desktopMain by getting {
            dependencies {
                api(compose.desktop.common)
                api("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("net.java.dev.jna:jna-platform:5.17.0")
            }
        }
    }
}

android {
    namespace = "com.nudge.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.nudge.shared.res"
}
