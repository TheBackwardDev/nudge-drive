package com.nudge.drive.desktop.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composables.icons.lucide.Search
import com.nudge.drive.ui.components.PillField
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ArrowUpFromLine
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.FolderUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.X
import com.nudge.drive.data.LanPeer
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.Page
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.desktop.data.ShareFile
import com.nudge.drive.desktop.data.ShareFileState
import com.nudge.drive.desktop.data.SharePhase
import com.nudge.drive.desktop.ui.NudgeDialog
import com.nudge.drive.desktop.ui.PageHeader
import com.nudge.drive.desktop.ui.ToolButton
import com.nudge.drive.desktop.ui.pickFiles
import com.nudge.drive.desktop.ui.pickFolder
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.UsageRing
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import java.io.File as JFile

private val Green = Color(0xFF1E9E57)

@Composable
fun NearbyPage(app: AppState) {
    val n = app.nearby
    val phase by n.phase.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Nearby share", "Send files between this computer and phones on the same Wi-Fi. No internet needed, end-to-end encrypted.")
        AnimatedContent(
            phase::class, label = "nearby",
            transitionSpec = { (fadeIn(tween(240)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(140)) },
        ) { _ ->
            when (val p = phase) {
                SharePhase.Idle -> Start(app)
                is SharePhase.Asking -> Waiting(app, p)
                is SharePhase.Moving -> Progress(app, p.peer, sending = p.sending, done = false)
                is SharePhase.Done -> Progress(app, p.peer, sending = p.sending, done = true)
                is SharePhase.Failed -> Failed(app, p.message)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- start

@Composable
private fun Start(app: AppState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Card(Modifier.weight(1.25f)) { SendSide(app) }
        Card(Modifier.weight(1f)) { ReceiveSide(app) }
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(22.dp), content = content)
}

@Composable
private fun CardTitle(icon: ImageVector, color: Color, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Nd.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
    }
}

@Composable
private fun SendSide(app: AppState) {
    val n = app.nearby
    val outgoing by n.outgoing.collectAsState()
    val preparing by n.preparing.collectAsState()
    val peers by n.peers.collectAsState()
    // Look for devices only while there's something to send and this screen is open.
    DisposableEffect(outgoing.isNotEmpty()) {
        if (outgoing.isNotEmpty()) n.lan.startDiscovery()
        onDispose { n.lan.stopDiscovery() }
    }
    CardTitle(Lucide.ArrowUpFromLine, Nd.Primary, "Send to a phone", "Or to another computer running Nudge Drive")
    Spacer(Modifier.height(18.dp))
    if (outgoing.isEmpty() && preparing == 0) {
        Text("Choose files on this computer, or right-click files in My files › Send to phone.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Choose files", icon = Lucide.Plus) { n.addFiles(pickFiles(app.awtWindow, "Choose files to send")) }
            SecondaryButton("Choose a folder", icon = Lucide.FolderUp) { pickFolder(app.awtWindow, "Choose a folder to send")?.let { n.addFiles(listOf(it)) } }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${outgoing.size} file${if (outgoing.size == 1) "" else "s"} · ${formatBytes(outgoing.sumOf { it.length() })}",
            style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f))
        ToolButton(Lucide.Plus, "Add") { n.addFiles(pickFiles(app.awtWindow, "Choose files to send")) }
        Spacer(Modifier.width(8.dp))
        ToolButton(Lucide.X, "Clear") { n.clearOutgoing() }
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nd.FieldSolid).padding(vertical = 4.dp)) {
        outgoing.take(6).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.File, null, tint = Nd.Iris, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatBytes(f.length()), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                Spacer(Modifier.width(10.dp))
                Icon(Lucide.X, "Remove", tint = Nd.Subtle, modifier = Modifier.size(15.dp).bounceClick { n.remove(f) })
            }
        }
        if (outgoing.size > 6) Text("and ${outgoing.size - 6} more", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        if (preparing > 0) Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Nd.Iris, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Getting $preparing file${if (preparing == 1) "" else "s"} from your drive…", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("Send to", style = MaterialTheme.typography.labelLarge, color = Nd.Muted)
    Spacer(Modifier.height(8.dp))
    if (peers.isEmpty()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nd.soft(Nd.Iris)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Pulse(Lucide.Wifi, 54.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Looking for devices…", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                Text("On the phone, open Nudge Drive › Nearby share › Receive. It has to be on the same Wi-Fi as this computer.",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
        }
        // Still nothing after a few seconds: the network may be dropping discovery messages. Offer the phone's address.
        var slow by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(8_000); slow = true }
        if (slow) {
            Spacer(Modifier.height(10.dp))
            AddByAddress(app, "Can't see your phone?", "Make sure the phone has the latest Nudge Drive and is on its Receive screen. " +
                "Its Wi-Fi address is shown there: type it here.")
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        peers.forEach { p -> PeerRow(p, enabled = outgoing.isNotEmpty()) { n.send(p) } }
    }
}

/** Ask a device directly by its address, for networks where it doesn't show up by itself. */
@Composable
private fun AddByAddress(app: AppState, title: String, body: String) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var asked by remember { mutableStateOf<String?>(null) }
    val add = {
        if (app.nearby.lan.addAddress(text)) { asked = text.trim(); error = null; text = "" }
        else error = "Type the address like 192.168.1.7"
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nd.Mist).padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillField(text, { text = it; error = null }, "e.g. 192.168.1.7", Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done))
            Spacer(Modifier.width(10.dp))
            SecondaryButton("Find", icon = Lucide.Search) { add() }
        }
        com.nudge.drive.desktop.ui.ErrorLine(error)
        asked?.let { Text("Looking at $it…", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun PeerRow(p: LanPeer, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).bounceClick(enabled = enabled, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Nd.Lavender, Nd.Primary))), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name.substringBefore(" · "), style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Text(p.name.substringAfter(" · ", if (p.kind == "computer") "Computer" else "Phone"), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        Icon(if (p.kind == "computer") Lucide.Monitor else Lucide.Smartphone, null, tint = Nd.Iris, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ReceiveSide(app: AppState) {
    val n = app.nearby
    val settings by app.settings.state.collectAsState()
    val received by n.received.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CardTitle(Lucide.ArrowDownToLine, Green, "Receive from a phone", "You're always asked first") }
        Switch(settings.nearbyVisible) { on -> app.settings.update { it.copy(nearbyVisible = on) } }
    }
    Spacer(Modifier.height(18.dp))
    if (!settings.nearbyVisible) {
        Text("Turn on to let phones on your Wi-Fi send files to this computer.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
    } else {
        Text("Visible as", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
        Text(n.deviceName(), style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
        val addresses = remember { n.lan.localAddresses() }
        if (addresses.isNotEmpty()) Text("Wi-Fi address: ${addresses.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        Spacer(Modifier.height(14.dp))
        listOf(
            "On your phone, open Nudge Drive › Nearby share › Send.",
            "Pick the files, then tap this computer.",
            "Check the code matches, then click Accept here.",
        ).forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Nd.soft(Green)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green)
                }
                Spacer(Modifier.width(10.dp))
                Text(s, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saved to ${app.settings.downloadDir.absolutePath}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ToolButton(Lucide.FolderOpen, null) { app.settings.downloadDir.apply { mkdirs() }.let { Platform.openFile(it) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nd.Mist).padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Lucide.ShieldCheck, null, tint = Nd.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Not showing on the phone? Both need the same Wi-Fi network." +
                    if (Platform.isWindows) " If Windows asked whether Nudge Drive may use the network, allow it on private networks." else "",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted,
            )
        }
    }
    if (received.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Received", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.weight(1f))
            ToolButton(Lucide.CloudUpload, "Upload to my drive") { uploadReceived(app, received) }
        }
        Spacer(Modifier.height(6.dp))
        received.take(6).forEach { f ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick { Platform.reveal(f) }.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.CircleCheck, null, tint = Green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatBytes(f.length()), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
        }
    }
}

private fun uploadReceived(app: AppState, files: List<JFile>) {
    val existing = files.filter { it.isFile }
    if (existing.isEmpty()) { app.toast("Those files were moved or deleted"); return }
    app.transfers.upload(existing, null)
    app.showTransfers = true
    app.toast("Uploading ${existing.size} file${if (existing.size == 1) "" else "s"} to My files")
}

// ---------------------------------------------------------------------------------------- in progress

@Composable
private fun Waiting(app: AppState, p: SharePhase.Asking) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Pulse(Lucide.Wifi, 240.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                p.code == null -> "Connecting to ${p.peer}…"
                p.sending -> "Waiting for ${p.peer} to accept"
                else -> "Connecting to ${p.peer}"
            },
            fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Nd.Ink, textAlign = TextAlign.Center,
        )
        if (p.code != null) {
            Spacer(Modifier.height(14.dp))
            CodeBox(p.code)
            Text("Check that ${p.peer.substringBefore(" · ")} shows the same code.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Cancel", Modifier.widthIn(min = 160.dp)) { app.nearby.cancel() }
    }
}

@Composable
fun CodeBox(code: String) {
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Nd.soft(Nd.Iris)).padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Code", style = MaterialTheme.typography.labelLarge, color = Nd.Muted)
        Spacer(Modifier.width(14.dp))
        Text(code.toCharArray().joinToString(" "), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp, color = Nd.IrisDeep)
    }
}

@Composable
private fun Progress(app: AppState, peer: String, sending: Boolean, done: Boolean) {
    val n = app.nearby
    val files by n.files.collectAsState()
    val total = files.sumOf { it.size }.coerceAtLeast(1)
    val moved = files.sumOf { if (it.state == ShareFileState.Done) it.size else it.bytes }
    val overall by animateFloatAsState(if (done) 1f else (moved.toFloat() / total).coerceIn(0f, 1f), spring(), label = "overall")
    val ok = files.count { it.state == ShareFileState.Done }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        UsageRing(overall, size = 150.dp, stroke = 12.dp, color = if (done) Green else Nd.Primary, label = if (done) "✓" else "${(overall * 100).toInt()}%")
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                !done -> if (sending) "Sending to $peer" else "Receiving from $peer"
                sending -> "Sent $ok file${if (ok == 1) "" else "s"} to $peer"
                else -> "Received $ok file${if (ok == 1) "" else "s"} from $peer"
            },
            fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Nd.Ink, textAlign = TextAlign.Center,
        )
        if (done && !sending) Text("Saved in ${app.settings.downloadDir.absolutePath}", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { files.forEach { FileProgressRow(it) } }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!done) SecondaryButton("Cancel", Modifier.widthIn(min = 160.dp)) { n.cancel() }
            else {
                val saved = files.mapNotNull { it.saved }
                if (!sending && saved.isNotEmpty()) {
                    SecondaryButton("Show in folder", icon = Lucide.FolderOpen) { Platform.reveal(saved.first()) }
                    SecondaryButton("Upload to my drive", icon = Lucide.CloudUpload) { uploadReceived(app, saved); n.finish() }
                }
                PrimaryButton("Done", Modifier.widthIn(min = 140.dp)) { n.finish() }
            }
        }
    }
}

@Composable
private fun FileProgressRow(f: ShareFile) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (f.state) { ShareFileState.Done -> Lucide.CircleCheck; ShareFileState.Failed -> Lucide.CircleAlert; else -> Lucide.File },
            null, tint = when (f.state) { ShareFileState.Done -> Green; ShareFileState.Failed -> Nd.Danger; else -> Nd.Iris }, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (f.state == ShareFileState.Moving || f.state == ShareFileState.Waiting)
                LinearProgressIndicator({ f.progress }, Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
            else Text(if (f.state == ShareFileState.Failed) "Failed" else formatBytes(f.size), style = MaterialTheme.typography.bodySmall,
                color = if (f.state == ShareFileState.Failed) Nd.Danger else Nd.Muted)
        }
        if (f.state == ShareFileState.Done && f.saved != null) ToolButton(Lucide.FolderOpen, null) { Platform.reveal(f.saved) }
    }
}

@Composable
private fun Failed(app: AppState, message: String) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Nd.DangerSoft), contentAlignment = Alignment.Center) {
            Icon(Lucide.X, null, tint = Nd.Danger, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("That didn't work", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Nd.Ink)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp).padding(top = 6.dp, bottom = 22.dp))
        PrimaryButton("Back", Modifier.widthIn(min = 160.dp)) { app.nearby.finish() }
    }
}

/** Expanding rings around a glowing circle. */
@Composable
private fun Pulse(icon: ImageVector, size: Dp) {
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val ring = Nd.Iris
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 0 until 3) {
                val f = (t + k / 3f) % 1f
                drawCircle(ring.copy(alpha = (1f - f) * 0.35f), radius = this.size.minDimension / 2f * (0.3f + 0.7f * f))
            }
        }
        Box(Modifier.size(size * 0.36f).clip(CircleShape).background(Brush.linearGradient(listOf(Nd.Lavender, Nd.Primary))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.16f))
        }
    }
}

// ---------------------------------------------------------------------------------------- incoming request

/** "X wants to send you files", shown anywhere in the app. */
@Composable
fun NearbyRequestDialog(app: AppState) {
    val r by app.nearby.request.collectAsState()
    val req = r ?: return
    NudgeDialog({ app.nearby.decline() }, width = 480) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Nd.Lavender, Nd.Primary))), contentAlignment = Alignment.Center) {
                Icon(if (req.kind == "computer") Lucide.Monitor else Lucide.Smartphone, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${req.peer.substringBefore(" · ")} wants to send you files", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Nd.Ink)
                Text("${req.files.size} file${if (req.files.size == 1) "" else "s"} · ${formatBytes(req.files.sumOf { it.size })} · from ${req.peer}",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nd.FieldSolid).padding(12.dp)) {
            req.files.take(5).forEach { f ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.File, null, tint = Nd.Iris, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatBytes(f.size), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                }
            }
            if (req.files.size > 5) Text("and ${req.files.size - 5} more", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CodeBox(req.code) }
        Text("Accept only if the phone shows the same code.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Decline", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(end = 18.dp).bounceClick { app.nearby.decline() })
            PrimaryButton("Accept", Modifier.widthIn(min = 150.dp), icon = Lucide.ArrowDownToLine) { app.nearby.accept(); app.go(Page.Nearby) }
        }
    }
}
