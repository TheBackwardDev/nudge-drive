import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Nudge Drive for Windows and macOS (Compose Multiplatform desktop, shares code with Android via :shared).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersion = (findProperty("desktopVersion") as String?) ?: "1.0.0"

kotlin { jvmToolchain(17) }

// JavaFX (embedded WebKit for the editor + platform media codecs) for the OS we're building on;
// each installer is built on its own OS in CI.
val fxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    // Embedded Chromium for the in-app editor (engine downloaded on first use).
    implementation("dev.datlag:kcef:2025.03.23")
    listOf("base", "graphics", "controls", "media", "swing").forEach {
        implementation("org.openjfx:javafx-$it:21.0.6:$fxPlatform")
    }
}

compose.desktop {
    application {
        mainClass = "com.nudge.drive.desktop.MainKt"
        jvmArgs += listOf(
            "-Dapp.version=$appVersion",
            "-Xmx1g",
            "-Dfile.encoding=UTF-8",
            "-Dprism.lcdtext=false",
            *(if (System.getenv("NUDGE_DEBUG") != null) arrayOf("-Dnudge.debug=1") else emptyArray()),
            // Embedded Chromium (KCEF) needs these.
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "Nudge Drive"
            packageVersion = appVersion
            description = "All your drives in one place"
            vendor = "Nudge"
            copyright = "Nudge Drive"
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http", "java.sql", "java.prefs",
                "java.scripting", "java.xml", "java.logging", "java.desktop", "jdk.crypto.ec", "jdk.unsupported",
                "jdk.accessibility", "jdk.httpserver", "jdk.jsobject", "jdk.xml.dom", "jdk.zipfs", "jdk.charsets", "jdk.localedata",
            )
            windows {
                menuGroup = "Nudge Drive"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                // Fixed id so new versions upgrade the installed one instead of installing side by side.
                upgradeUuid = "5b2f8a8e-4a61-4c3e-9d7a-1e6c0d9a7f21"
                iconFile.set(project.file("icons/nudge.ico"))
            }
            macOS {
                bundleID = "com.nudge.drive.desktop"
                iconFile.set(project.file("icons/nudge.icns"))
            }
            linux { iconFile.set(project.file("icons/nudge.png")) }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
