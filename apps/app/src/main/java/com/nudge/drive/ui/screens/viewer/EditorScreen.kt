package com.nudge.drive.ui.screens.viewer

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.composables.icons.lucide.FileWarning
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.nudge.drive.AppContainer
import com.nudge.drive.BuildConfig
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.OfficeSession
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.theme.Nd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Every state the editor can be in; exactly one at a time. */
private sealed interface EditorState {
    data object Connecting : EditorState
    data class Opening(val session: OfficeSession) : EditorState
    data class Editing(val session: OfficeSession) : EditorState
    data class Closing(val session: OfficeSession) : EditorState
    data class Failed(val message: String) : EditorState
}

/**
 * Full document / spreadsheet / presentation editor inside the app, served by the self-hosted
 * Collabora Online on the Nudge server. Saves straight back to Google Drive / S3.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun EditorScreen(c: AppContainer, id: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    var state by remember(id) { mutableStateOf<EditorState>(EditorState.Connecting) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var modified by remember { mutableStateOf(false) }
    var saveAck by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray()); fileCallback = null
    }

    LaunchedEffect(id, attempt) {
        state = EditorState.Connecting
        state = try {
            EditorState.Opening(c.repo.officeSession(id))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            EditorState.Failed((e as? ApiException)?.friendly ?: "Couldn't open the editor. Check your connection and try again.")
        }
    }
    // Collabora can take a while the very first time; don't spin forever.
    LaunchedEffect(state) {
        if (state is EditorState.Opening) { delay(45_000); if (state is EditorState.Opening) state = EditorState.Failed("The editor is taking too long to load. Try again.") }
    }

    fun post(json: String) { web?.evaluateJavascript("window.nudgePost && nudgePost(${JSONObject.quote(json)})", null) }

    /** Saves unsaved changes (if any), then leaves. */
    fun close() {
        val s = (state as? EditorState.Editing)?.session
        if (s == null) { nav.popBackStack(); return }
        state = EditorState.Closing(s)
        scope.launch {
            if (modified) {
                val ack = CompletableDeferred<Boolean>().also { saveAck = it }
                post("""{"MessageId":"Action_Save","Values":{"DontTerminateEdit":true,"DontSaveIfUnmodified":true,"Notify":true}}""")
                val ok = withTimeoutOrNull(10_000) { ack.await() }
                if (ok == false) c.toast("Couldn't save the last changes") else if (ok == true) c.toast("Saved")
            }
            c.fileEdited.tryEmit(id); c.refreshBus.tryEmit(Unit)
            nav.popBackStack()
        }
    }

    val bridge = remember(id) {
        object {
            @JavascriptInterface
            fun onMessage(raw: String) {
                if (BuildConfig.DEBUG) android.util.Log.d("NudgeEditor", "msg: ${raw.take(200)}")
                val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
                val values = msg.optJSONObject("Values")
                web?.post {
                    when (msg.optString("MessageId")) {
                        "App_LoadingStatus" -> when (values?.optString("Status")) {
                            "Frame_Ready" -> post("""{"MessageId":"Host_PostmessageReady"}""")
                            "Document_Loaded" -> (state as? EditorState.Opening)?.let { state = EditorState.Editing(it.session) }
                            "Failed" -> state = EditorState.Failed("The editor couldn't open this file.")
                        }
                        "Doc_ModifiedStatus" -> modified = values?.optBoolean("Modified") == true
                        "Action_Save_Resp" -> { saveAck?.complete(values?.optBoolean("success", true) != false); if (values?.optBoolean("success", true) != false) modified = false }
                        "UI_Close", "close" -> close()
                    }
                }
            }
        }
    }

    BackHandler { if (state is EditorState.Closing) Unit else close() }
    DisposableEffect(Unit) { onDispose { web?.destroy(); web = null } }

    val bg = Nd.Haze
    Box(Modifier.fillMaxSize().background(bg)) {
        val s = state
        val session = when (s) { is EditorState.Opening -> s.session; is EditorState.Editing -> s.session; is EditorState.Closing -> s.session; else -> null }
        if (session != null) AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(bg.toArgb())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.allowFileAccess = false
                    addJavascriptInterface(bridge, "NudgeHost")
                    webViewClient = WebViewClient()
                    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                            if (BuildConfig.DEBUG) android.util.Log.d("NudgeEditor", "${m.messageLevel()} ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                            return true
                        }
                        override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            fileCallback?.onReceiveValue(null); fileCallback = cb
                            runCatching { pickFile.launch(params.acceptTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }.toTypedArray()) }
                                .onFailure { cb.onReceiveValue(null); fileCallback = null }
                            return true
                        }
                    }
                    setDownloadListener { url, _, disposition, mime, _ -> download(ctx, url, disposition, mime) }
                    web = this
                    loadDataWithBaseURL(BuildConfig.WEB_BASE + "/", hostPage(session, bg.toArgb()), "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        )

        AnimatedVisibility(s is EditorState.Connecting || s is EditorState.Opening, exit = fadeOut()) {
            Column(Modifier.fillMaxSize().background(bg).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Nd.Iris, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Opening the editor…", style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
                Text(c.fileCache[id]?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Nd.Muted, textAlign = TextAlign.Center)
            }
        }
        if (s is EditorState.Closing) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Nd.GlassStrong).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Nd.Iris, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(10.dp))
                Text(if (modified) "Saving…" else "Closing…", style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
            }
        }
        if (s is EditorState.Failed) Column(
            Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
                Icon(Lucide.FileWarning, null, tint = Nd.Iris, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(s.message, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Try again", icon = Lucide.RotateCw) { web?.destroy(); web = null; attempt++ }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Back", container = Nd.GlassStrong, content = Nd.Ink) { nav.popBackStack() }
        }
    }
}

/** A tiny host page: posts the access token to Collabora inside an iframe and relays its postMessages to the app. */
private fun hostPage(s: OfficeSession, bg: Int): String {
    val origin = BuildConfig.WEB_BASE
    val color = String.format("#%06X", bg and 0xFFFFFF)
    fun esc(v: String) = v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
    return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body{margin:0;padding:0;height:100%;overflow:hidden;background:$color}form{display:none}
iframe{position:fixed;top:0;left:0;right:0;bottom:0;width:100%;height:100%;border:0;display:block}</style></head>
<body><form id="f" action="${esc(s.url)}" method="post" target="ed">
<input type="hidden" name="access_token" value="${esc(s.accessToken)}"><input type="hidden" name="access_token_ttl" value="${s.accessTokenTtl}"></form>
<iframe name="ed" id="ed" allow="clipboard-read; clipboard-write; fullscreen"></iframe>
<script>
window.addEventListener('message', function (e) {
  if (e.origin !== '$origin') return;
  try { NudgeHost.onMessage(typeof e.data === 'string' ? e.data : JSON.stringify(e.data)); } catch (x) {}
});
window.nudgePost = function (m) { var f = document.getElementById('ed'); if (f && f.contentWindow) f.contentWindow.postMessage(m, '$origin'); };
document.getElementById('f').submit();
</script></body></html>"""
}

private fun download(ctx: Context, url: String, disposition: String?, mime: String?) {
    runCatching {
        val name = android.webkit.URLUtil.guessFileName(url, disposition, mime)
        val req = DownloadManager.Request(Uri.parse(url)).setMimeType(mime).setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Nudge Drive/$name")
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    }
}
