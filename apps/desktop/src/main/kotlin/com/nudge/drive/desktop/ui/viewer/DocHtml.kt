package com.nudge.drive.desktop.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.nudge.drive.data.FileItem
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.ui.theme.Nd
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.io.File
import kotlin.math.ln

/** Word / Google Docs / ODT / RTF: shown as re-flowing HTML instead of fixed PDF pages. */
fun isWordDoc(f: FileItem): Boolean {
    val m = f.mimeType; val ext = f.name.substringAfterLast('.', "").lowercase()
    return m == "application/vnd.google-apps.document" || m.contains("wordprocessingml") || m.contains("msword") ||
        m.contains("opendocument.text") || m.contains("rtf") || ext in setOf("doc", "docx", "odt", "rtf")
}

/** The HTML view needs the embedded browser (set up in the background after sign-in); otherwise PDF pages are used. */
val htmlViewAvailable: Boolean get() = EditorEngine.state.value == EngineState.Ready

/**
 * The document as HTML in the embedded Chromium, re-flowed to the window, readable in light and dark.
 * Links open in the normal browser. [onFail] is called when the server can't make HTML (the caller falls back to PDF).
 */
@Composable
fun DocHtmlView(app: AppState, f: FileItem, zoom: Float, hidden: Boolean, onFail: () -> Unit) {
    var html by remember(f.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(f.id) {
        try { html = app.repo.documentHtml(f.id) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { onFail() }
    }
    val dark = Nd.isDark
    val colors = mapOf("bg" to hex(Nd.Haze), "ink" to hex(Nd.Ink), "line" to hex(Nd.Line), "link" to hex(Nd.IrisDeep), "muted" to hex(Nd.Muted))
    val page = remember(html, dark) { html?.let { styled(it, dark, colors) } }
    var browser by remember(f.id) { mutableStateOf<KCEFBrowser?>(null) }
    var client by remember(f.id) { mutableStateOf<KCEFClient?>(null) }
    val zoomNow by rememberUpdatedState(zoom)
    fun level(z: Float) = ln(z.toDouble()) / ln(1.2) // Chromium zoom: factor = 1.2 ^ level

    LaunchedEffect(page) {
        val p = page ?: return@LaunchedEffect
        val url = runCatching {
            withContext(Dispatchers.IO) {
                // Under "previews" so signing out clears it.
                val out = File(Platform.cacheDir, "previews/doc-html/${f.id.filter { it.isLetterOrDigit() || it == '-' }}.html")
                out.parentFile.mkdirs(); out.writeText(p, Charsets.UTF_8)
                out.toPath().toUri().toString()
            }
        }.getOrElse { onFail(); return@LaunchedEffect }
        browser?.let { it.loadURL(url); return@LaunchedEffect }
        val c = runCatching { withContext(Dispatchers.IO) { KCEF.newClient() } }.getOrElse { onFail(); return@LaunchedEffect }
        c.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(b: CefBrowser?, frame: CefFrame?, request: CefRequest?, userGesture: Boolean, isRedirect: Boolean): Boolean {
                val u = request?.url ?: return false
                if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("mailto:")) { Platform.openInBrowser(u); return true }
                return false
            }
        })
        c.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) b?.zoomLevel = level(zoomNow)
            }
        })
        client = c
        browser = c.createBrowser(url, CefRendering.DEFAULT, false)
    }
    LaunchedEffect(browser, zoom) { browser?.let { runCatching { it.zoomLevel = level(zoom) } } }
    DisposableEffect(f.id) {
        onDispose {
            val b = browser; val c = client
            if (b != null || c != null) Thread({ runCatching { b?.close(true) }; runCatching { c?.dispose() } }, "nudge-doc-close").apply { isDaemon = true }.start()
        }
    }

    val b = browser
    if (b == null) { Loading("Opening…"); return }
    Box(Modifier.fillMaxSize().padding(horizontal = 84.dp, vertical = 8.dp).background(Nd.Haze)) {
        // Heavyweight view: it draws over Compose, so it's hidden while a dialog is open on top of the viewer.
        SwingPanel(background = Color.Transparent, modifier = Modifier.fillMaxSize(), factory = { b.uiComponent }, update = { it.isVisible = !hidden })
    }
}

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

private fun styled(html: String, dark: Boolean, c: Map<String, String>): String {
    val style = """
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html,body{margin:0!important;max-width:none!important;width:auto!important;background:${c["bg"]}!important}
body{padding:28px 48px 96px 48px!important;color:${c["ink"]};font-family:'Segoe UI',-apple-system,Roboto,sans-serif;font-size:16px;line-height:1.6;word-wrap:break-word;overflow-wrap:anywhere}
body>*{max-width:860px;margin-left:auto!important;margin-right:auto!important}
p,li,span,div{max-width:100%!important}
h1{font-size:1.6em;line-height:1.25} h2{font-size:1.3em} h3{font-size:1.12em}
img{max-width:100%!important;height:auto!important;border-radius:6px}
table{border-collapse:collapse;display:block;overflow-x:auto;max-width:100%;margin:12px auto}
td,th{border:1px solid ${c["line"]}!important;padding:6px 8px;vertical-align:top;min-width:48px}
a{color:${c["link"]}!important}
hr{border:none;border-top:1px solid ${c["line"]}}
${if (dark) "*{color:${c["ink"]}!important;background-color:transparent!important;border-color:${c["line"]}!important} body{background:${c["bg"]}!important}" else ""}
</style>"""
    val i = html.indexOf("</head>", ignoreCase = true)
    return if (i >= 0) html.substring(0, i) + style + html.substring(i)
    else "<!doctype html><html><head>$style</head><body>$html</body></html>"
}
