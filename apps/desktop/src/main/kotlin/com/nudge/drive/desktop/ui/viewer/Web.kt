package com.nudge.drive.desktop.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.nudge.drive.ui.components.bounceClick
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.nudge.drive.Endpoints
import com.nudge.drive.data.FileItem
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.NudgeTheme
import javafx.application.Platform as Fx
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * JavaFX (embedded WebKit + platform media codecs) inside Compose. The toolkit starts on a background
 * thread with a timeout, so a machine where it can't start shows a message instead of freezing the app.
 */
object FxRuntime {
    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var starting = false

    fun start() {
        if (starting) return
        synchronized(this) {
            if (starting) return
            starting = true
            thread(isDaemon = true, name = "nudge-fx-start") {
                try {
                    Fx.startup { Fx.setImplicitExit(false); ready.complete(true) }
                } catch (e: IllegalStateException) {
                    System.err.println("JavaFX: ${e.message}")
                    ready.complete(e.message?.contains("already initialized", ignoreCase = true) == true)
                } catch (t: Throwable) {
                    System.err.println("JavaFX couldn't start: $t"); t.printStackTrace()
                    ready.complete(false)
                }
            }
        }
    }

    /** True once JavaFX is up (false if it couldn't start within [timeoutMs]). */
    suspend fun await(timeoutMs: Long = 20_000): Boolean { start(); return withTimeoutOrNull(timeoutMs) { ready.await() } ?: false }
    val isReady: Boolean get() = ready.isCompleted && runCatching { ready.getCompleted() }.getOrDefault(false)

    fun run(block: () -> Unit) { if (Fx.isFxApplicationThread()) block() else Fx.runLater(block) }
}

@Composable
private fun rememberFxReady(): Boolean? {
    var ok by remember { mutableStateOf(if (FxRuntime.isReady) true else null) }
    LaunchedEffect(Unit) { if (ok == null) ok = FxRuntime.await() }
    return ok
}

/** Plays video / audio with the platform's codecs (H.264/AAC MP4, MOV, MP3, M4A, WAV…). */
@Composable
fun MediaView(app: AppState, f: FileItem) {
    var url by remember(f.id) { mutableStateOf<String?>(null) }
    var failed by remember(f.id) { mutableStateOf(false) }
    LaunchedEffect(f.id) { runCatching { app.repo.previewUrl(f.id) }.onSuccess { url = it }.onFailure { failed = true } }
    val u = url
    val fx = rememberFxReady()
    var player by remember(f.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(f.id) { mutableStateOf(false) }
    var position by remember(f.id) { mutableStateOf(0.0) }
    var duration by remember(f.id) { mutableStateOf(0.0) }
    var mediaError by remember(f.id) { mutableStateOf(false) }
    when {
        failed || fx == false || mediaError -> { NoPreview(app, f, "This format can't play here. Try Open with another app."); return }
        u == null || fx == null -> { Loading("Loading…"); return }
    }
    val audio = f.mimeType.startsWith("audio/")
    Column(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 10.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (audio) Text(f.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
            SwingPanel(
                background = Color(0xFF0B0A10),
                modifier = if (audio) Modifier.size(1.dp) else Modifier.fillMaxSize(),
                factory = {
                    JFXPanel().also { panel ->
                        FxRuntime.run {
                            runCatching {
                                val mp = MediaPlayer(Media(u))
                                val view = MediaView(mp).apply { isPreserveRatio = true }
                                val root = StackPane(view).apply { style = "-fx-background-color: #0b0a10;" }
                                view.fitWidthProperty().bind(root.widthProperty()); view.fitHeightProperty().bind(root.heightProperty())
                                panel.scene = Scene(root, javafx.scene.paint.Color.web("#0b0a10"))
                                mp.setOnError { mediaError = true }
                                mp.setOnReady { duration = mp.totalDuration.toSeconds() }
                                mp.currentTimeProperty().addListener { _, _, t -> position = t.toSeconds() }
                                mp.setOnPlaying { playing = true }; mp.setOnPaused { playing = false }
                                mp.setOnEndOfMedia { playing = false; mp.seek(javafx.util.Duration.ZERO); mp.pause() }
                                mp.isAutoPlay = true
                                player = mp
                            }.onFailure { mediaError = true }
                        }
                    }
                },
            )
        }
        // Controls
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE61E1C26)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Nd.Primary).bounceClick { FxRuntime.run { player?.let { if (playing) it.pause() else it.play() } } },
                contentAlignment = Alignment.Center) {
                Icon(if (playing) Lucide.Pause else Lucide.Play, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(clock(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = { v -> val t = v * duration; position = t; FxRuntime.run { player?.seek(javafx.util.Duration.seconds(t)) } },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Nd.Lavender, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
            )
            Text(clock(duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        }
    }
    DisposableEffect(f.id) { onDispose { val p = player; FxRuntime.run { runCatching { p?.stop(); p?.dispose() } } } }
}

private fun clock(sec: Double): String {
    val s = sec.toLong().coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}

private fun String.quoteJs() = "'" + replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"
