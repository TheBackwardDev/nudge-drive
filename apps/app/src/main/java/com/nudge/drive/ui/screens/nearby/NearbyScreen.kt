package com.nudge.drive.ui.screens.nearby

import android.app.Activity
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.nudge.drive.data.NearbyFix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ArrowUpFromLine
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.X
import com.nudge.drive.AppContainer
import com.nudge.drive.data.NearbyFile
import com.nudge.drive.data.NearbyFileState
import com.nudge.drive.data.NearbyPhase
import com.nudge.drive.data.NearbyShare
import com.nudge.drive.data.OutgoingItem
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.common.SubTopBar
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Phone-to-phone sharing between Nudge users (no internet, Bluetooth + Wi-Fi Direct). */
@Composable
fun NearbyScreen(c: AppContainer, nav: NavController) {
    val n = c.nearby
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val phase by n.phase.collectAsState()
    val outgoing by n.outgoing.collectAsState()
    val request by n.request.collectAsState()
    var wantsTo by remember { mutableStateOf<String?>(null) } // "send" | "receive" waiting for permission
    var confirmLeave by remember { mutableStateOf(false) }

    // Start sequence: Wi-Fi sharing starts at once (it needs no permission); Bluetooth, for phones that
    // aren't on this Wi-Fi, is added after permissions -> Location switched on (it retries timing failures itself).
    val note by n.bluetoothNote.collectAsState()
    fun start(@Suppress("UNUSED_PARAMETER") what: String) = n.addNearby()
    val locationOff = "Turn on Location to also find phones that aren't on this Wi-Fi."
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val what = wantsTo ?: return@rememberLauncherForActivityResult
        wantsTo = null
        if (r.resultCode == Activity.RESULT_OK) start(what)
        else n.nearbyUnavailable(locationOff, NearbyFix.TurnOnLocation)
    }
    /** Shows Google's one-tap "Turn on location" dialog when needed (it also fixes old "device only" location modes). */
    fun ensureLocation(what: String, force: Boolean) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (!force && (lm == null || LocationManagerCompat.isLocationEnabled(lm))) { start(what); return }
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build())
            .setNeedBle(true).setAlwaysShow(true).build()
        LocationServices.getSettingsClient(ctx).checkLocationSettings(request)
            .addOnSuccessListener { start(what) }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    wantsTo = what
                    runCatching { locationLauncher.launch(IntentSenderRequest.Builder(e.resolution).build()) }
                        .onFailure { wantsTo = null; runCatching { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
                } else if (lm != null && LocationManagerCompat.isLocationEnabled(lm)) start(what)
                else { n.nearbyUnavailable(locationOff, NearbyFix.TurnOnLocation); runCatching { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
            }
    }
    fun afterPermissions(what: String) {
        if (NearbyShare.needsLocationOn(what == "send")) ensureLocation(what, force = false) else start(what)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val what = wantsTo ?: return@rememberLauncherForActivityResult
        wantsTo = null
        val missing = NearbyShare.missing(ctx, what == "send")
        if (missing.isEmpty()) afterPermissions(what)
        else n.nearbyUnavailable(NearbyShare.explain(missing), NearbyFix.AppSettings)
    }
    fun go(what: String) {
        if (what == "send") n.startSending(withNearby = false) else n.startReceiving(withNearby = false)
        if (NearbyShare.missing(ctx, what == "send").isEmpty()) afterPermissions(what)
        else { wantsTo = what; permLauncher.launch(NearbyShare.permissions(what == "send")) }
    }
    fun fixNote(fix: NearbyFix) {
        val what = if (n.lastRole == false) "receive" else "send"
        when (fix) {
            NearbyFix.AppSettings -> runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))) }
            NearbyFix.TurnOnLocation -> ensureLocation(what, force = true)
            NearbyFix.None -> n.addNearby()
        }
    }
    // Back from app settings with the permission now allowed: add Bluetooth without making them tap again.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        val what = if (n.lastRole == false) "receive" else "send"
        if (note?.second == NearbyFix.AppSettings && wantsTo == null && NearbyShare.missing(ctx, what == "send").isEmpty()) afterPermissions(what)
    }
    fun retry() {
        val what = if (n.lastRole == true) "send" else if (n.lastRole == false) "receive" else null
        n.stop()
        if (what == "receive" || (what == "send" && outgoing.isNotEmpty())) go(what)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            n.outgoing.value = n.outgoing.value + withContext(Dispatchers.IO) { uris.map { NearbyShare.describe(ctx, it) } }
        }
    }

    // Arriving with files already queued (from the gallery, a file, or another app's share sheet): start looking right away.
    LaunchedEffect(Unit) { if (outgoing.isNotEmpty() && phase == NearbyPhase.Idle) go("send") }
    DisposableEffect(Unit) { onDispose { if (!n.busy) { n.stop(); n.clearOutgoing() } } }

    val leave = { if (n.busy) confirmLeave = true else nav.popBackStack() }
    BackHandler { if (phase != NearbyPhase.Idle && !n.busy) n.stop() else leave() }

    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("Nearby share", onBack = { leave() })
            AnimatedContent(
                phase::class, label = "phase", modifier = Modifier.weight(1f),
                transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
            ) { _ ->
                when (val p = phase) {
                    NearbyPhase.Idle -> Start(outgoing, onSend = { if (outgoing.isEmpty()) pickFiles.launch(arrayOf("*/*")) else go("send") },
                        onReceive = { go("receive") }, onAdd = { pickFiles.launch(arrayOf("*/*")) }, onClear = { n.clearOutgoing() })
                    NearbyPhase.Searching -> Searching(c, outgoing, note, ::fixNote)
                    NearbyPhase.Visible -> Radar(
                        title = "Ready to receive",
                        body = (if (note == null) "Visible to nearby phones, and to computers and phones on this Wi-Fi, as\n" else "Visible to computers and phones on this Wi-Fi as\n") +
                            "${c.session.user.value?.name?.split(' ')?.firstOrNull() ?: "you"} · ${android.os.Build.MODEL}" +
                            n.wifiAddresses().takeIf { it.isNotEmpty() }?.let { "\n\nWi-Fi address: ${it.joinToString(", ")}" }.orEmpty(),
                        icon = Lucide.ArrowDownToLine,
                        note = note, onFix = ::fixNote,
                    )
                    is NearbyPhase.Connecting -> Radar(
                        title = if (p.code == null) "Asking ${p.peer}…" else "Connecting to ${p.peer}",
                        body = if (p.code != null) "Code ${p.code}\nMake sure the same code shows on both screens." else "They need to tap Accept on their device.",
                        icon = Lucide.Wifi,
                    )
                    is NearbyPhase.Transferring -> Transfer(c, p.peer, done = false)
                    is NearbyPhase.Done -> Transfer(c, p.peer, done = true, received = p.received, onFinish = { n.stop(); n.clearOutgoing() })
                    is NearbyPhase.Failed -> Failed(p.message, p.fix, onRetry = { retry() }, onFix = {
                        when (p.fix) {
                            NearbyFix.AppSettings -> runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))) }
                            NearbyFix.TurnOnLocation -> retry()
                            NearbyFix.None -> {}
                        }
                    })
                }
            }
        }
    }

    request?.let { r ->
        ConfirmDialog(
            "${r.name} wants to send you files",
            "Check that both screens show the code ${r.code}, then accept.",
            "Accept", danger = false,
            onDismiss = { n.decline() },
        ) { n.accept() }
    }
    if (confirmLeave) ConfirmDialog(
        "Stop the transfer?", "Files that haven't finished will be cancelled.", "Stop",
        onDismiss = { confirmLeave = false },
    ) { confirmLeave = false; n.stop(); n.clearOutgoing(); nav.popBackStack() }
}

@Composable
private fun Start(outgoing: List<OutgoingItem>, onSend: () -> Unit, onReceive: () -> Unit, onAdd: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Send photos, videos and files straight to a friend's phone or your computer. No internet needed: Nudge finds phones over Bluetooth and computers on your Wi-Fi, and moves files over a direct, encrypted link.",
            style = MaterialTheme.typography.bodyMedium, color = Nd.Muted,
        )
        if (outgoing.isNotEmpty()) {
            val shape = RoundedCornerShape(22.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ready to send", style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f))
                    Text("Clear", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick(onClick = onClear).padding(6.dp))
                }
                Text("${outgoing.size} file${if (outgoing.size == 1) "" else "s"} · ${formatBytes(outgoing.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                outgoing.take(4).forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (outgoing.size > 4) Text("and ${outgoing.size - 4} more", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onAdd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Plus, null, tint = Nd.IrisDeep, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("Add more", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep)
                }
            }
        }
        BigChoice(Lucide.ArrowUpFromLine, "Send", if (outgoing.isEmpty()) "Pick files from this phone" else "Find a phone to send to", Nd.Primary, onSend)
        BigChoice(Lucide.ArrowDownToLine, "Receive", "Let a friend send files to you", Color(0xFF1E9E57), onReceive)
        Spacer(Modifier.weight(1f))
        Text("The other phone needs Nudge Drive open on this screen. A computer just needs Nudge Drive running on the same Wi-Fi.", style = MaterialTheme.typography.bodySmall,
            color = Nd.Subtle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp))
    }
}

@Composable
private fun BigChoice(icon: ImageVector, title: String, sub: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().height(96.dp).softShadow(shape, 14.dp, 0.1f).clip(shape).background(Nd.GlassStrong)
            .border(1.dp, Nd.GlassBorder, shape).bounceClick(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Nd.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
    }
}

@Composable
private fun Searching(c: AppContainer, outgoing: List<OutgoingItem>, note: Pair<String, NearbyFix>?, onFix: (NearbyFix) -> Unit) {
    val peers by c.nearby.peers.collectAsState()
    Column(Modifier.fillMaxSize()) {
        note?.let { BluetoothNote(it, onFix) }
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { Pulse(Lucide.ArrowUpFromLine) }
        Text(
            if (peers.isEmpty()) "Looking for phones and computers…" else "Tap where to send",
            style = MaterialTheme.typography.titleLarge, color = Nd.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Sending ${outgoing.size} file${if (outgoing.size == 1) "" else "s"} (${formatBytes(outgoing.sumOf { it.size })}). Your friend opens Nudge › Nearby share › Receive. Computers with Nudge Drive open on the same Wi-Fi show up too.",
            style = MaterialTheme.typography.bodySmall, color = Nd.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp),
        )
        // Still nothing after a few seconds: offer to look for a computer by the address it shows.
        var slow by remember { mutableStateOf(false) }
        var askAddress by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(8_000); slow = true }
        if (slow && peers.isEmpty()) Text(
            "Computer not showing? Enter its address",
            style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).bounceClick { askAddress = true }.padding(8.dp),
        )
        if (askAddress) AddressDialog(c, onDismiss = { askAddress = false })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(peers, key = { it.id }) { peer ->
                val shape = RoundedCornerShape(22.dp)
                Row(
                    Modifier.fillMaxWidth().animateItem().softShadow(shape, 10.dp, 0.1f).clip(shape).background(Nd.GlassStrong)
                        .border(1.dp, Nd.GlassBorder, shape).bounceClick { c.nearby.sendTo(peer) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Nd.Lavender, Nd.Primary))), contentAlignment = Alignment.Center) {
                        Text(peer.name.take(1).uppercase(), color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(peer.name.substringBefore(" · "), style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                        Text(peer.name.substringAfter(" · ", ""), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                    }
                    Icon(if (peer.kind == "computer") Lucide.Monitor else Lucide.Smartphone, null, tint = Nd.Iris)
                }
            }
        }
    }
}

@Composable
private fun Radar(title: String, body: String, icon: ImageVector, note: Pair<String, NearbyFix>? = null, onFix: (NearbyFix) -> Unit = {}) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { Pulse(icon) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        Spacer(Modifier.weight(1f))
        note?.let { Box(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { BluetoothNote(it, onFix) } }
    }
}

@Composable
private fun AddressDialog(c: AppContainer, onDismiss: () -> Unit) {
    com.nudge.drive.ui.components.TextInputDialog(
        "Computer's Wi-Fi address", placeholder = "e.g. 192.168.1.2", confirm = "Find",
        extra = {
            Text("On the computer, open Nudge Drive › Nearby share. The address is shown under \"Visible as\".",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 10.dp))
        },
        onDismiss = onDismiss,
    ) { text ->
        if (c.nearby.addAddress(text)) { c.toast("Looking at $text…"); onDismiss() } else c.toast("Type the address like 192.168.1.2")
    }
}

/** Bluetooth isn't running (Wi-Fi still is): say why and offer the fix. */
@Composable
private fun BluetoothNote(note: Pair<String, NearbyFix>, onFix: (NearbyFix) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(shape).background(Nd.GlassStrong)
            .border(1.dp, Nd.GlassBorder, shape).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Wifi, null, tint = Nd.Iris, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Working over Wi-Fi only", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
        }
        Text("Computers and phones on this Wi-Fi can share with you. For phones that aren't on this Wi-Fi: ${note.first}", style = MaterialTheme.typography.bodySmall,
            color = Nd.Muted, modifier = Modifier.padding(top = 4.dp))
        Text(
            when (note.second) { NearbyFix.AppSettings -> "Open app settings"; NearbyFix.TurnOnLocation -> "Turn on location"; NearbyFix.None -> "Try Bluetooth again" },
            style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).bounceClick { onFix(note.second) }.padding(vertical = 6.dp),
        )
    }
}

/** Expanding rings around a glowing button. */
@Composable
private fun Pulse(icon: ImageVector) {
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val ring = Nd.Iris
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(280.dp)) {
            for (k in 0 until 3) {
                val f = (t + k / 3f) % 1f
                drawCircle(ring.copy(alpha = (1f - f) * 0.35f), radius = size.minDimension / 2f * (0.3f + 0.7f * f))
            }
        }
        Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Nd.Lavender, Nd.Primary))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun Transfer(c: AppContainer, peer: String, done: Boolean, received: Boolean = false, onFinish: () -> Unit = {}) {
    val files by c.nearby.files.collectAsState()
    val total = files.sumOf { it.size }.coerceAtLeast(1)
    val moved = files.sumOf { if (it.state == NearbyFileState.Done) it.size else it.bytes }
    val overall by animateFloatAsState(if (done) 1f else (moved.toFloat() / total).coerceIn(0f, 1f), spring(), label = "overall")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            com.nudge.drive.ui.components.UsageRing(overall, size = 150.dp, stroke = 12.dp, color = if (done) Color(0xFF1E9E57) else Nd.Primary,
                label = if (done) "✓" else "${(overall * 100).toInt()}%")
        }
        Text(
            when {
                !done && files.isEmpty() -> "Connected to $peer"
                !done -> if (c.nearby.isSending) "Sending to $peer" else "Receiving from $peer"
                received -> "Received ${files.count { it.state == NearbyFileState.Done }} file${if (files.size == 1) "" else "s"}"
                else -> "Sent to $peer"
            },
            style = MaterialTheme.typography.titleLarge, color = Nd.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (done && received) Text("Saved in Downloads › Nudge Drive", style = MaterialTheme.typography.bodySmall, color = Nd.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        LazyColumn(Modifier.weight(1f).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.payloadId }) { f -> FileProgressRow(f) }
        }
        if (done) Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val saved = files.mapNotNull { it.savedUri }
            if (received && saved.isNotEmpty()) PrimaryButton("Also save to my Nudge Drive", Modifier.fillMaxWidth(), icon = Lucide.CloudUpload) {
                c.transfers.upload(saved, null); c.toast("Uploading ${saved.size} file${if (saved.size == 1) "" else "s"} to your drive…"); onFinish()
            }
            PrimaryButton("Done", Modifier.fillMaxWidth(), container = Nd.GlassStrong, content = Nd.Ink, onClick = onFinish)
        }
    }
}

@Composable
private fun FileProgressRow(f: NearbyFile) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
            Icon(
                when (f.state) { NearbyFileState.Done -> Lucide.CircleCheck; NearbyFileState.Failed -> Lucide.CircleAlert; else -> Lucide.File },
                null, tint = when (f.state) { NearbyFileState.Done -> Color(0xFF1E9E57); NearbyFileState.Failed -> Nd.Danger; else -> Nd.Iris },
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (f.state == NearbyFileState.Moving || f.state == NearbyFileState.Waiting) {
                LinearProgressIndicator({ f.progress }, Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
            } else Text(if (f.state == NearbyFileState.Failed) "Failed" else formatBytes(f.size), style = MaterialTheme.typography.bodySmall,
                color = if (f.state == NearbyFileState.Failed) Nd.Danger else Nd.Muted)
        }
    }
}

@Composable
private fun Failed(message: String, fix: NearbyFix, onRetry: () -> Unit, onFix: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Nd.DangerSoft), contentAlignment = Alignment.Center) {
            Icon(Lucide.X, null, tint = Nd.Danger, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("That didn't work", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        PrimaryButton("Try again", Modifier.fillMaxWidth(), onClick = onRetry)
        if (fix != NearbyFix.None) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (fix == NearbyFix.TurnOnLocation) "Turn on location" else "Open app settings", Modifier.fillMaxWidth(),
                container = Nd.GlassStrong, content = Nd.Ink, onClick = onFix)
        }
    }
}

