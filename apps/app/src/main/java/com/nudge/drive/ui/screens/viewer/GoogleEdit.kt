package com.nudge.drive.ui.screens.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which in-app reader a document gets, and which Google editor can edit it. */
enum class DocFlavor(val editorName: String, val editorPackage: String?) {
    Sheet("Google Sheets", "com.google.android.apps.docs.editors.sheets"),
    Word("Google Docs", "com.google.android.apps.docs.editors.docs"),
    Slides("Google Slides", "com.google.android.apps.docs.editors.slides"),
    Pdf("Google Drive", null),
    Other("Google Drive", null),
}

fun docFlavor(f: FileItem): DocFlavor {
    val m = f.mimeType
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when {
        m == "application/vnd.google-apps.spreadsheet" || m.contains("spreadsheet") || m.contains("excel") || m == "text/csv" ||
            m == "text/tab-separated-values" || ext in setOf("xls", "xlsx", "xlsm", "ods", "csv", "tsv") -> DocFlavor.Sheet
        m == "application/vnd.google-apps.document" || m.contains("wordprocessingml") || m.contains("msword") ||
            m.contains("opendocument.text") || m.contains("rtf") || ext in setOf("doc", "docx", "odt", "rtf") -> DocFlavor.Word
        m == "application/vnd.google-apps.presentation" || m.contains("presentation") || m.contains("powerpoint") ||
            ext in setOf("ppt", "pptx", "odp") -> DocFlavor.Slides
        m == "application/pdf" -> DocFlavor.Pdf
        else -> DocFlavor.Other
    }
}

fun canEditInGoogle(f: FileItem): Boolean =
    f.provider == "google_drive" && (docFlavor(f) in setOf(DocFlavor.Sheet, DocFlavor.Word, DocFlavor.Slides) ||
        f.mimeType == "application/vnd.google-apps.drawing" || f.mimeType == "application/vnd.google-apps.form")

/**
 * Opens the file for editing: in the Google Docs / Sheets / Slides app when it's installed
 * (full editing, offline, comments), otherwise in an in-app browser tab.
 */
fun openInGoogle(c: AppContainer, context: Context, f: FileItem, knownUrl: String? = null) {
    val flavor = docFlavor(f)
    c.scope.launch {
        val url = knownUrl ?: runCatching { c.repo.editLink(f.id).url }.getOrElse {
            c.toast((it as? ApiException)?.friendly ?: "Couldn't get an edit link for this file"); return@launch
        }
        withContext(Dispatchers.Main) { launchEditor(context, url, flavor) }
    }
}

fun launchEditor(context: Context, url: String, flavor: DocFlavor) {
    val uri = Uri.parse(url)
    val base = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    flavor.editorPackage?.let { pkg ->
        try { context.startActivity(Intent(base).setPackage(pkg)); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
    }
    // Any other native handler (e.g. the Drive app), but never a plain browser here.
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        try {
            context.startActivity(Intent(base).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)); return
        } catch (_: ActivityNotFoundException) {}
    }
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(context, uri)
    } catch (_: Exception) {
        runCatching { context.startActivity(base) }
    }
}

/** Files the in-app editor (Collabora on the Nudge server) can open for editing. */
fun canEditInApp(f: FileItem): Boolean {
    if (f.status != "active") return false
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return f.mimeType in setOf(
        "application/vnd.google-apps.document", "application/vnd.google-apps.spreadsheet", "application/vnd.google-apps.presentation",
    ) || ext in setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "xlsm", "ods", "csv", "ppt", "pptx", "odp")
}
