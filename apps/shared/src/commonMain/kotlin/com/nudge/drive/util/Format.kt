package com.nudge.drive.util

import com.composables.icons.lucide.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nudge.drive.data.FileItem
import com.nudge.drive.ui.theme.Nd
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "Unlimited"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 4)
    val v = bytes / 1024.0.pow(exp)
    return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[exp - 1])
    else String.format(Locale.US, "%.1f %s", v, units[exp - 1])
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun parseInstant(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun friendlyDate(iso: String?): String {
    val i = parseInstant(iso) ?: return ""
    val d = Duration.between(i, Instant.now())
    return when {
        d.toMinutes() < 1 -> "Just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        d.toDays() < 2 -> "Yesterday"
        d.toDays() < 7 -> "${d.toDays()} days ago"
        else -> dateFmt.format(i.atZone(ZoneId.systemDefault()))
    }
}

fun fullDate(iso: String?): String = parseInstant(iso)?.atZone(ZoneId.systemDefault())?.let { "${dateFmt.format(it)} · ${timeFmt.format(it)}" } ?: "—"

enum class FileKind(val label: String, val badge: String, val icon: ImageVector, val color: Color, val apiKind: String?) {
    Image("Images", "JPG", Lucide.Image, Nd.Image, "image"),
    Video("Videos", "MP4", Lucide.CirclePlay, Nd.Video, "video"),
    Pdf("PDFs", "PDF", Lucide.FileType2, Nd.Pdf, "pdf"),
    Doc("Docs", "DOC", Lucide.FileText, Nd.Doc, "doc"),
    Sheet("Sheets", "XLS", Lucide.FileSpreadsheet, Nd.Image, null),
    Audio("Audio", "MP3", Lucide.FileAudio, Nd.Audio, null),
    Archive("Archives", "ZIP", Lucide.FileArchive, Nd.Zip, "archive"),
    Text("Text", "TXT", Lucide.FileText, Nd.Doc, null),
    Other("Other", "FILE", Lucide.File, Nd.Iris, null),
}

/** Pastel (light) / translucent (dark) tile colour for a file kind. */
val FileKind.soft: Color
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable get() = Nd.soft(color)

fun FileItem.kind(): FileKind = kindOf(mimeType, name)

fun kindOf(mime: String, name: String = ""): FileKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || mime == "application/vnd.google-apps.drawing" -> FileKind.Image
        mime.startsWith("video/") -> FileKind.Video
        mime.startsWith("audio/") -> FileKind.Audio
        mime == "application/pdf" || mime == "application/vnd.google-apps.presentation" -> FileKind.Pdf
        mime.contains("spreadsheet") || mime.contains("excel") || ext in setOf("xls", "xlsx", "csv") -> FileKind.Sheet
        mime.contains("word") || mime == "application/vnd.google-apps.document" || ext in setOf("doc", "docx", "odt") -> FileKind.Doc
        mime.contains("zip") || mime.contains("rar") || mime.contains("tar") || mime.contains("7z") -> FileKind.Archive
        mime.startsWith("text/") || mime == "application/json" -> FileKind.Text
        else -> FileKind.Other
    }
}

fun FileItem.badge(): String = name.substringAfterLast('.', "").uppercase().take(4).ifBlank { kind().badge }

/** Folder colors are "#rrggbb" or legacy tailwind names; map both to a soft pastel. */
fun folderColor(raw: String): Color {
    if (raw.startsWith("#") && raw.length == 7) runCatching {
        return Color(0xFF000000 or raw.substring(1).toLong(16))
    }
    return when {
        raw.contains("red") -> Color(0xFFEF4444); raw.contains("green") || raw.contains("emerald") -> Color(0xFF10B981)
        raw.contains("yellow") || raw.contains("amber") -> Color(0xFFF59E0B); raw.contains("purple") || raw.contains("violet") -> Color(0xFF8B5CF6)
        raw.contains("pink") -> Color(0xFFEC4899); raw.contains("orange") -> Color(0xFFF97316)
        else -> Color(0xFF3B82F6)
    }
}

val FolderPalette = listOf("#8b72f5", "#3b82f6", "#10b981", "#f59e0b", "#ec4899", "#ef4444", "#14b8a6", "#64748b")
