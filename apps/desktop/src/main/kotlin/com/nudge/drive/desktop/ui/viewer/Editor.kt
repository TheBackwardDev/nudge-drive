package com.nudge.drive.desktop.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.nudge.drive.Endpoints
import com.nudge.drive.data.AppJson
import com.nudge.drive.data.FileItem
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.NudgeTheme
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.io.File
import java.net.URLEncoder

/** State of the embedded Chromium engine (downloaded once, ~100 MB, into the app-data folder). */
sealed interface EngineState {
    data object Idle : EngineState
    data class Downloading(val progress: Float) : EngineState
    data object Installing : EngineState
    data object Ready : EngineState
    data object RestartRequired : EngineState
    data class Failed(val message: String) : EngineState
}

/**
 * Embedded Chromium (KCEF / JetBrains JCEF) for the in-app editor. JavaFX's WebKit can't run
 * Collabora (its WebSocket support is broken), so the editor uses a real Chromium.
 */
object EditorEngine {
    val state = MutableStateFlow<EngineState>(EngineState.Idle)
    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var started = false

    val installDir: File get() = File(Platform.dataDir, "editor-engine")

    fun start(scope: kotlinx.coroutines.CoroutineScope) {
        if (started) return
        synchronized(this) { if (started) return; started = true }
        scope.launch(Dispatchers.IO) {
            try {
                KCEF.init(builder = {
                    installDir(installDir)
                    progress {
                        onDownloading { p -> state.value = EngineState.Downloading((p / 100f).coerceIn(0f, 1f)) }
                        onExtracting { state.value = EngineState.Installing }
                        onInstall { state.value = EngineState.Installing }
                        onInitialized { state.value = EngineState.Ready; ready.complete(true) }
                    }
                    settings {
                        cachePath = File(Platform.cacheDir, "editor-cache").absolutePath
                        windowlessRenderingEnabled = false
                        // Point Chromium at the downloaded engine explicitly (otherwise it looks inside the Java
                        // runtime, can't start its helper processes and aborts the whole app). Set these even
                        // when the engine isn't downloaded yet: these settings are read before the download
                        // and used after it, when the files exist.
                        if (!Platform.isMac) {
                            browserSubProcessPath = File(installDir, if (Platform.isWindows) "jcef_helper.exe" else "jcef_helper").absolutePath
                            resourcesDirPath = installDir.absolutePath
                            localesDirPath = File(installDir, "locales").absolutePath
                        }
                    }
                    // Software rendering where there's no GPU (VMs, remote desktops) still works.
                    addArgs("--disable-gpu-shader-disk-cache")
                }, onError = { e ->
                    state.value = EngineState.Failed(e?.message ?: "The editor couldn't be set up.")
                    ready.complete(false)
                }, onRestartRequired = {
                    state.value = EngineState.RestartRequired
                    ready.complete(false)
                })
            } catch (t: Throwable) {
                state.value = EngineState.Failed(t.message ?: "The editor couldn't be set up.")
                ready.complete(false)
            }
        }
    }

    suspend fun awaitReady(): Boolean = ready.await()

    fun shutdown() { if (state.value == EngineState.Ready) runCatching { KCEF.disposeBlocking() } }
}

private sealed interface DocState {
    data object Loading : DocState
    data object Ready : DocState
    data class Failed(val message: String) : DocState
}

/** Opens the in-app editor (self-hosted Collabora Online) in its own window. Changes save back to the drive. */
@Composable
fun EditorWindow(app: AppState, file: FileItem) {
    val scope = rememberCoroutineScope()
    val engine by EditorEngine.state.collectAsState()
    val winState = rememberWindowState(size = DpSize(1320.dp, 880.dp), position = WindowPosition(Alignment.Center))
    var doc by remember(file.id) { mutableStateOf<DocState>(DocState.Loading) }
    var browser by remember { mutableStateOf<KCEFBrowser?>(null) }
    var client by remember { mutableStateOf<KCEFClient?>(null) }
    var modified by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var closedByEditor by remember { mutableStateOf(false) }
    val saveAck = remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val bg = Nd.Haze

    fun post(json: String) {
        val js = "window.nudgePost && window.nudgePost(" + AppJson.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(json)) + ")"
        browser?.executeJavaScript(js, "", 0)
    }

    fun onMessage(raw: String) {
        val msg = runCatching { AppJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val values = msg["Values"] as? JsonObject
        when (msg["MessageId"]?.jsonPrimitive?.content) {
            "App_LoadingStatus" -> when (values?.get("Status")?.jsonPrimitive?.content) {
                // Handshake: without it Collabora won't send close/save events to the host.
                "Frame_Ready" -> post("""{"MessageId":"Host_PostmessageReady"}""")
                "Document_Loaded" -> doc = DocState.Ready
            }
            "Doc_ModifiedStatus" -> modified = values?.get("Modified")?.jsonPrimitive?.booleanOrNull == true
            "Action_Save_Resp" -> saveAck.value?.complete(values?.get("success")?.jsonPrimitive?.booleanOrNull != false)
            // Collabora's own close button: it ends the session and saves by itself.
            "UI_Close", "close" -> { closedByEditor = true; closing = true }
        }
    }

    LaunchedEffect(Unit) { EditorEngine.start(app.scope) }

    // Create the browser once the engine is ready, pointing at the host page with a fresh session.
    LaunchedEffect(engine is EngineState.Ready) {
        if (engine !is EngineState.Ready || browser != null) return@LaunchedEffect
        val session = runCatching { app.repo.officeSession(file.id) }.getOrElse {
            doc = DocState.Failed((it as? com.nudge.drive.data.ApiException)?.friendly ?: "Couldn't open the editor."); return@LaunchedEffect
        }
        val color = "#%06X".format(bg.toArgb() and 0xFFFFFF)
        val hash = listOf("url" to session.url, "token" to session.accessToken, "ttl" to session.accessTokenTtl.toString(), "bg" to color)
            .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val c = withContext(Dispatchers.IO) { KCEF.newClient() }
        // JS → app: the host page calls window.NudgeHost.onMessage(json), routed through cefQuery.
        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig("nudgeQuery", "nudgeQueryCancel"))
        router.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(b: CefBrowser?, f: CefFrame?, id: Long, request: String?, persistent: Boolean, cb: CefQueryCallback?): Boolean {
                if (System.getProperty("nudge.debug") != null) System.err.println("editor msg: ${request?.take(120)}")
                request?.let { r -> scope.launch { onMessage(r) } }
                cb?.success("")
                return true
            }
        }, true)
        c.addMessageRouter(router)
        c.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, f: CefFrame?, httpStatusCode: Int) {
                if (System.getProperty("nudge.debug") != null) System.err.println("editor loadEnd main=${f?.isMain} url=${f?.url?.take(60)}")
                if (f?.isMain == true) b?.executeJavaScript(
                    "window.NudgeHost={onMessage:function(m){window.nudgeQuery({request:String(m),persistent:false,onSuccess:function(){},onFailure:function(){}});}};",
                    f.url, 0,
                )
            }
        })
        client = c
        browser = c.createBrowser("${Endpoints.WEB_BASE}/editor-host.html#$hash", CefRendering.DEFAULT, false)
    }

    suspend fun closeAndSave() {
        if (browser != null && !closedByEditor && (doc == DocState.Ready || modified)) {
            val ack = CompletableDeferred<Boolean>(); saveAck.value = ack
            post("""{"MessageId":"Action_Save","Values":{"DontTerminateEdit":true,"DontSaveIfUnmodified":true,"Notify":true}}""")
            val ok = withTimeoutOrNull(if (modified) 15_000 else 3_000) { ack.await() }
            if (modified && ok != true) app.toast("Saving is taking a while. The editor keeps your changes and saves them automatically.")
        }
        // Close the window first; shutting Chromium down can block, so it happens off the UI thread.
        val b = browser; val c = client
        browser = null; client = null
        app.transfers.forgetCached(file.id)
        app.changed()
        app.editorFor = null
        Thread({ runCatching { b?.close(true) }; runCatching { c?.dispose() } }, "nudge-editor-close").apply { isDaemon = true }.start()
    }
    LaunchedEffect(closing) { if (closing) closeAndSave() }

    Window(onCloseRequest = { closing = true }, state = winState, title = "${file.name} · Nudge Drive", icon = app.icon) {
        NudgeTheme(dark = Nd.isDark) {
            Box(Modifier.fillMaxSize().background(bg)) {
                val b = browser
                if (b != null) SwingPanel(modifier = Modifier.fillMaxSize(), factory = { b.uiComponent })
                val overlay: (@Composable () -> Unit)? = when (val e = engine) {
                    EngineState.Idle -> { { Progress("Starting the editor…", null) } }
                    is EngineState.Downloading -> { { Progress("Setting up the editor (one time only, about 100 MB)…", e.progress) } }
                    EngineState.Installing -> { { Progress("Installing the editor…", null) } }
                    EngineState.RestartRequired -> { { Message("Restart Nudge Drive to finish setting up the editor.") } }
                    is EngineState.Failed -> { { Message("The editor couldn't be set up: ${e.message}") } }
                    EngineState.Ready -> when (val d = doc) {
                        is DocState.Failed -> { { Message(d.message) } }
                        else -> null // Collabora shows its own loading screen
                    }
                }
                // Heavyweight browser views draw over Compose, so only show messages before it exists.
                if (overlay != null && (b == null || doc is DocState.Failed)) Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) { overlay() }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val b = browser; val c = client
            if (b != null || c != null) Thread({ runCatching { b?.close(true) }; runCatching { c?.dispose() } }, "nudge-editor-close").apply { isDaemon = true }.start()
        }
    }
}

@Composable
private fun Progress(label: String, progress: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(420.dp)) {
        if (progress == null) CircularProgressIndicator(Modifier.size(32.dp), color = Nd.Iris, strokeWidth = 3.dp)
        else LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
        Spacer(Modifier.height(14.dp))
        Text(label, color = Nd.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, color = Nd.Ink, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(40.dp))
}
