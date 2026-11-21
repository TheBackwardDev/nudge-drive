package com.nudge.drive.desktop.data

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.Desktop
import java.io.File
import java.net.URI

/** OS helpers for Windows and macOS (Linux works too, for development). */
object Platform {
    private val os = System.getProperty("os.name").lowercase()
    val isWindows = os.contains("win")
    val isMac = os.contains("mac")
    val version: String = System.getProperty("app.version") ?: "dev"
    val userAgent = "NudgeDrive-${if (isWindows) "Windows" else if (isMac) "macOS" else "Desktop"}/$version"

    /** %APPDATA%\Nudge Drive, ~/Library/Application Support/Nudge Drive, or ~/.config/nudge-drive. */
    val dataDir: File by lazy {
        val home = System.getProperty("user.home")
        when {
            isWindows -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Nudge Drive")
            isMac -> File(home, "Library/Application Support/Nudge Drive")
            else -> File(home, ".config/nudge-drive")
        }.apply { mkdirs() }
    }
    val cacheDir: File by lazy {
        val home = System.getProperty("user.home")
        when {
            isWindows -> File(System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local", "Nudge Drive\\Cache")
            isMac -> File(home, "Library/Caches/Nudge Drive")
            else -> File(home, ".cache/nudge-drive")
        }.apply { mkdirs() }
    }
    val defaultDownloads: File get() = File(System.getProperty("user.home"), "Downloads${File.separator}Nudge Drive")

    /** Whether the OS is set to dark mode. */
    fun systemDark(): Boolean = runCatching {
        when {
            isWindows -> Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme",
            ) == 0
            isMac -> ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start().inputStream.bufferedReader().readText().contains("Dark")
            else -> false
        }
    }.getOrDefault(false)

    /** Per-user encryption (Windows DPAPI): only this Windows account can read the result. */
    fun protect(data: ByteArray): ByteArray = if (isWindows) Crypt32Util.cryptProtectData(data) else data
    fun unprotect(data: ByteArray): ByteArray = if (isWindows) Crypt32Util.cryptUnprotectData(data) else data

    /** rw------- on macOS/Linux (Windows profile folders are already per-user). */
    fun ownerOnly(f: File) {
        if (!isWindows) runCatching { java.nio.file.Files.setPosixFilePermissions(f.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) }
    }

    fun openInBrowser(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure {
            when {
                isWindows -> Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
                isMac -> Runtime.getRuntime().exec(arrayOf("open", url))
                else -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            }
        }
    }

    /** Shows a file selected in Explorer / Finder. */
    fun reveal(file: File) {
        runCatching {
            when {
                isWindows -> Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
                isMac -> Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
                else -> Desktop.getDesktop().open(file.parentFile)
            }
        }
    }

    fun openFile(file: File) { runCatching { Desktop.getDesktop().open(file) } }

    /** Path of the running app's launcher (for "start with Windows" and the Send to shortcut). */
    val launcher: File? by lazy {
        ProcessHandle.current().info().command().orElse(null)?.let { File(it) }
            ?.takeIf { it.name.lowercase().let { n -> n.startsWith("nudge") } }
    }

    // ---------------------------------------------------------------- Windows integration
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val RUN_NAME = "Nudge Drive"

    var startWithSystem: Boolean
        get() = isWindows && runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME) }.getOrDefault(false)
        set(on) {
            if (!isWindows) return
            val exe = launcher ?: return
            runCatching {
                if (on) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME, "\"${exe.absolutePath}\" --background")
                else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME)) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME)
            }
        }

    /**
     * Adds "Nudge Drive" to Explorer's right-click › Send to menu (a shortcut in the user's SendTo
     * folder). Selected files are passed to the running app, which uploads them.
     */
    fun installSendTo() {
        if (!isWindows) return
        val exe = launcher ?: return
        val sendTo = File(System.getenv("APPDATA") ?: return, "Microsoft\\Windows\\SendTo\\Nudge Drive.lnk")
        if (sendTo.exists()) return
        val ps = """
            ${'$'}s = (New-Object -ComObject WScript.Shell).CreateShortcut('${sendTo.absolutePath.replace("'", "''")}')
            ${'$'}s.TargetPath = '${exe.absolutePath.replace("'", "''")}'
            ${'$'}s.Arguments = '--upload'
            ${'$'}s.IconLocation = '${exe.absolutePath.replace("'", "''")},0'
            ${'$'}s.Description = 'Upload to Nudge Drive'
            ${'$'}s.Save()
        """.trimIndent()
        runCatching { ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps).redirectErrorStream(true).start().waitFor() }
    }
}
