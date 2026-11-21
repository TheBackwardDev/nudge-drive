package com.nudge.drive.util

import com.nudge.drive.Endpoints
import com.nudge.drive.data.FileItem

/** Server-side thumbnail at roughly [px] on the long edge. Tiny (~30 KB) compared with the original. */
fun thumbUrl(id: String, px: Int): String = "${Endpoints.API_BASE}/files/$id/thumbnail?s=$px"
fun originalUrl(id: String): String = "${Endpoints.API_BASE}/files/$id/download"

/** Thumbnail sizes in use, so different screens hit the same cache entries. */
object ThumbSize {
    const val Small = 200
    const val Grid = 400
    const val Large = 1024
    const val Screen = 1600
}

/** How the in-app viewer shows a file. */
enum class ViewerType { Image, Video, Audio, Document, Text, None }

private val textExtensions = setOf(
    "txt", "md", "log", "json", "xml", "yaml", "yml", "ini", "conf", "cfg", "env", "tsv",
    "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "c", "h", "cpp", "cs", "php", "swift",
    "sh", "bat", "ps1", "sql", "html", "htm", "css", "scss", "gradle", "properties", "toml", "srt", "vtt",
)
private val documentExtensions = setOf("pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "xlsm", "ods", "csv", "ppt", "pptx", "odp")
private val googleDocs = setOf(
    "application/vnd.google-apps.document", "application/vnd.google-apps.spreadsheet", "application/vnd.google-apps.presentation",
)

fun FileItem.viewerType(): ViewerType {
    if (status != "active") return ViewerType.None
    val ext = name.substringAfterLast('.', "").lowercase()
    val m = mimeType
    return when {
        m.startsWith("image/") || m == "application/vnd.google-apps.drawing" -> ViewerType.Image
        m.startsWith("video/") -> ViewerType.Video
        m.startsWith("audio/") -> ViewerType.Audio
        m == "application/pdf" || m in googleDocs || ext in documentExtensions ||
            m.contains("officedocument") || m.contains("msword") || m.contains("ms-excel") || m.contains("ms-powerpoint") ||
            m.contains("opendocument") || m == "text/csv" -> ViewerType.Document
        m.startsWith("text/") || m == "application/json" || m == "application/xml" || m == "application/javascript" || ext in textExtensions -> ViewerType.Text
        else -> ViewerType.None
    }
}

val FileItem.isMedia: Boolean get() = viewerType().let { it == ViewerType.Image || it == ViewerType.Video }

/** Whether the server can produce a picture for this file (Google makes them for photos, videos, PDFs and office files). */
fun FileItem.hasThumbnail(): Boolean = status == "active" && when (viewerType()) {
    ViewerType.Image -> true
    ViewerType.Video, ViewerType.Document -> provider == "google_drive"
    else -> false
}
