package com.nudge.drive.ui.screens.viewer

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.ui.theme.Nd

/**
 * Word / Google Docs reader: the document as HTML, re-flowed to the phone's width (no tiny A4 pages),
 * readable in light and dark, pinch to zoom. Scripts are off; links open in a browser tab.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocHtmlViewer(c: AppContainer, f: FileItem, top: Dp, reloadKey: Int, onError: (String) -> Unit) {
    var html by remember(f.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(f.id, reloadKey) {
        try { html = c.repo.documentHtml(f.id) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { if (html == null) onError((e as? ApiException)?.friendly ?: "Couldn't open this document.") }
    }
    val doc = html ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }
    val dark = Nd.isDark
    val bg = Nd.Haze; val ink = Nd.Ink; val line = Nd.Line; val link = Nd.IrisDeep; val muted = Nd.Muted
    val page = remember(doc, dark, top) { styled(doc, dark, top.value.toInt(), css(bg.toArgb(), ink.toArgb(), line.toArgb(), link.toArgb(), muted.toArgb())) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = true
                settings.allowFileAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        runCatching { CustomTabsIntent.Builder().build().launchUrl(view.context, request.url) }
                        return true
                    }
                }
            }
        },
        update = { wv ->
            if (wv.tag != page) { wv.tag = page; wv.loadDataWithBaseURL("https://docs.google.com/", page, "text/html", "utf-8", null) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

private fun css(bg: Int, ink: Int, line: Int, link: Int, muted: Int) = mapOf("bg" to hex(bg), "ink" to hex(ink), "line" to hex(line), "link" to hex(link), "muted" to hex(muted))

private fun styled(html: String, dark: Boolean, topPx: Int, c: Map<String, String>): String {
    val style = """
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
<style>
html,body{margin:0!important;max-width:none!important;width:auto!important;background:${c["bg"]}!important}
body{padding:${topPx + 8}px 18px 96px 18px!important;color:${c["ink"]};font-family:-apple-system,Roboto,'Segoe UI',sans-serif;font-size:16px;line-height:1.6;word-wrap:break-word;overflow-wrap:anywhere}
p,li,span,div{max-width:100%!important}
h1{font-size:1.6em;line-height:1.25} h2{font-size:1.3em} h3{font-size:1.12em}
img{max-width:100%!important;height:auto!important;border-radius:6px}
table{border-collapse:collapse;display:block;overflow-x:auto;max-width:100%;margin:12px 0}
td,th{border:1px solid ${c["line"]}!important;padding:6px 8px;vertical-align:top;min-width:48px}
a{color:${c["link"]}!important}
hr{border:none;border-top:1px solid ${c["line"]}}
${if (dark) "*{color:${c["ink"]}!important;background-color:transparent!important;border-color:${c["line"]}!important} body{background:${c["bg"]}!important}" else ""}
</style>"""
    val i = html.indexOf("</head>", ignoreCase = true)
    return if (i >= 0) html.substring(0, i) + style + html.substring(i)
    else "<html><head>$style</head><body>$html</body></html>"
}

