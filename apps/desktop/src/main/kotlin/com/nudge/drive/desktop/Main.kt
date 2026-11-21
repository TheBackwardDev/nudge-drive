package com.nudge.drive.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.nudge.drive.desktop.data.AppLog
import com.nudge.drive.desktop.data.Instance
import com.nudge.drive.desktop.data.NoticeRoute
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.desktop.ui.AuthFlow
import com.nudge.drive.desktop.ui.Shell
import com.nudge.drive.desktop.ui.Splash
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.NudgeTheme
import java.awt.Frame
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    if (Instance.handOff(args.toList())) exitProcess(0)
    AppLog.w("app", "start ${Platform.version} on ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e -> AppLog.e("crash", "uncaught in ${t.name}", e); previous?.uncaughtException(t, e) }
    val app = AppState()
    val background = "--background" in args
    Instance.listen { more -> app.handleArgs(more, bringToFront = true) }
    app.handleArgs(args.toList(), bringToFront = false)
    if (Platform.launcher != null && !app.settings.value.sendToInstalled) {
        Platform.installSendTo(); app.settings.update { it.copy(sendToInstalled = true) }
    }

    application {
        var visible by remember { mutableStateOf(!background) }
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(Alignment.Center))
        val trayState = rememberTrayState()
        val icon = remember { appIcon() }
        app.icon = icon
        app.bringToFront = { visible = true; windowState.isMinimized = false; app.focusWindow() }
        LaunchedEffect(Unit) {
            app.inbox.systemNotify = { n -> if (app.settings.value.systemNotifications) trayState.sendNotification(androidx.compose.ui.window.Notification(n.title, n.body)) }
        }
        val settings by app.settings.state.collectAsState()

        Tray(
            icon = icon, state = trayState, tooltip = "Nudge Drive",
            onAction = { app.bringToFront() },
            menu = {
                Item("Open Nudge Drive", onClick = { app.bringToFront() })
                Item("Upload files…", onClick = { app.bringToFront(); app.pickAndUpload() })
                Item(if (settings.backupPaused) "Resume backup" else "Pause backup", onClick = {
                    app.settings.update { it.copy(backupPaused = !it.backupPaused) }; if (!app.settings.value.backupPaused) app.backup.trigger()
                })
                Separator()
                Item("Quit", onClick = { app.quit(); exitApplication() })
            },
        )

        Window(
            onCloseRequest = { if (settings.closeToTray) visible = false else { app.quit(); exitApplication() } },
            visible = visible,
            state = windowState,
            title = "Nudge Drive",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && ((e.key == Key.DirectionLeft && e.isAltPressed) || e.key == Key.Back) && app.viewer == null) app.back() else false
            },
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            app.focusWindow = { window.isVisible = true; window.extendedState = window.extendedState and Frame.ICONIFIED.inv(); window.toFront(); window.requestFocus() }
            app.awtWindow = window
            val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> app.darkSystem }
            NudgeTheme(dark = dark) { Root(app) }
        }
    }
}

@Composable
private fun Root(app: AppState) {
    val loggedIn by app.session.loggedIn.collectAsState()
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.session.load(); com.nudge.drive.desktop.ui.migrateOnboarding(app) }
    Box(Modifier.fillMaxSize().background(Nd.Background)) {
        AnimatedContent(
            targetState = when { !splashDone || loggedIn == null -> 0; loggedIn == true -> 2; else -> 1 },
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
            label = "root",
        ) { s ->
            when (s) {
                0 -> Splash { splashDone = true }
                1 -> AuthFlow(app)
                else -> Shell(app)
            }
        }
    }
}

/** Window / tray icon from the bundled logo. */
private fun appIcon(): BitmapPainter {
    val img = AppState::class.java.getResourceAsStream("/icon.png")?.use { ImageIO.read(it) }
        ?: java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    return BitmapPainter(img.toComposeImageBitmap())
}

/** Command-line arguments: "--upload <files…>" from Explorer's Send to, plain paths too. */
fun AppState.handleArgs(args: List<String>, bringToFront: Boolean) {
    val files = args.filter { !it.startsWith("--") }.map { File(it) }.filter { it.exists() }
    if (files.isNotEmpty()) { incoming.addAll(files) }
    if (bringToFront && (files.isNotEmpty() || "--background" !in args)) this.bringToFront()
}

fun AppState.quit() {
    backup.stop()
    nearby.lan.stopAll()
    com.nudge.drive.desktop.ui.viewer.EditorEngine.shutdown()
    scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
}

fun AppState.route(r: NoticeRoute?) {
    when (r) {
        is NoticeRoute.OpenFile -> scope.launch_ { runCatching { repo.file(r.fileId) }.onSuccess { openFile(it, listOf(it)) }.onFailure { toast("That file no longer exists.") } }
        NoticeRoute.Transfers -> showTransfers = true
        NoticeRoute.Backup -> go(Page.Backup)
        NoticeRoute.Drives -> go(Page.Drives)
        NoticeRoute.Nearby -> go(Page.Nearby)
        NoticeRoute.Shared -> go(Page.Shared)
        is NoticeRoute.ShowLocal -> Platform.reveal(File(r.path))
        null -> {}
    }
}

private fun kotlinx.coroutines.CoroutineScope.launch_(block: suspend () -> Unit) =
    this.launch(kotlinx.coroutines.Dispatchers.Main) { block() }
