package com.nudge.drive.desktop.data

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A small diagnostics log (no file contents, tokens or passwords) so problems on a user's machine
 * can be looked at afterwards: <app data>/logs/nudge.log, kept under ~1 MB (one older copy).
 */
object AppLog {
    val file: File by lazy { File(Platform.dataDir, "logs").apply { mkdirs() }.resolve("nudge.log") }
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Synchronized
    fun w(tag: String, message: String) {
        runCatching {
            if (file.length() > 1_000_000) {
                file.copyTo(File(file.parentFile, "nudge.old.log"), overwrite = true)
                file.writeText("")
            }
            file.appendText("${LocalDateTime.now().format(time)} [$tag] $message\n")
        }
    }

    fun e(tag: String, message: String, t: Throwable) =
        w(tag, "$message: ${t.javaClass.name}: ${t.message}\n    " + t.stackTrace.take(8).joinToString("\n    "))
}
