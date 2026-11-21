package com.nudge.drive.desktop.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.FolderSync
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.UserX
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Unplug
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.data.ImportStatus
import com.nudge.drive.data.RoutingPolicy
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.BackupFolder
import com.nudge.drive.desktop.data.BackupRange
import com.nudge.drive.desktop.data.NoticeRoute
import com.nudge.drive.desktop.data.NoticeType
import com.nudge.drive.desktop.data.BackupStatus
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.desktop.ui.DriveGlyph
import com.nudge.drive.desktop.ui.NudgeDialog
import com.nudge.drive.desktop.ui.PageHeader
import com.nudge.drive.desktop.ui.ToolButton
import com.nudge.drive.desktop.ui.pickFolder
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.GlassCard
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.friendlyDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ======================================================================================== Drives

/** "Connect Google Drive" happens in the browser; this page only waits for the drive to show up. */
internal sealed interface ConnectState {
    data object Idle : ConnectState
    data object Opening : ConnectState
    /** [known]: each drive's status when the browser was opened (null = couldn't be read; the first poll sets it). */
    data class Waiting(val known: Map<String, String>?) : ConnectState
}

internal fun List<ConnectedAccount>.statusById() = associate { it.id to it.status }

/** Opens Google's connect page in the browser and returns what to wait for. */
internal suspend fun startGoogleConnect(app: AppState, current: List<ConnectedAccount>?): ConnectState.Waiting {
    val known = current?.statusById()
        ?: try { app.repo.accounts().statusById() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    Platform.openInBrowser(app.repo.googleConnectUrl())
    return ConnectState.Waiting(known)
}

/**
 * Polls (up to 5 minutes) until a drive shows up that wasn't there before, or one that needed
 * reconnecting is connected again. Returns the new list, or null when nothing changed.
 */
internal suspend fun awaitConnectedDrive(app: AppState, waiting: ConnectState.Waiting): List<ConnectedAccount>? {
    var known = waiting.known
    repeat(100) {
        delay(3000)
        val now = try { app.repo.accounts() } catch (e: CancellationException) { throw e } catch (e: Exception) { return@repeat }
        val before = known ?: run { known = now.statusById(); return@repeat }
        if (now.any { a -> before[a.id].let { it == null || (it != "connected" && a.status == "connected") } }) return now
    }
    return null
}

@Composable
internal fun WaitingForGoogle(modifier: Modifier, onCancel: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Nd.soft(Nd.Iris)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Nd.Iris, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Finish connecting in your browser. The drive shows up here automatically.", style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Nd.Muted,
            modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(10.dp)).bounceClick(onClick = onCancel).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun DrivesPage(app: AppState) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<ConnectedAccount>>>(Load.Loading) }
    var reload by remember { mutableStateOf(0) }
    var connect by remember { mutableStateOf<ConnectState>(ConnectState.Idle) }
    var s3 by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<ConnectedAccount?>(null) }
    val imports = remember { mutableStateMapOf<String, ImportStatus>() }
    var policy by remember { mutableStateOf<RoutingPolicy?>(null) }
    var syncing by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        state = runCatching { app.repo.accounts() }.fold({ Load.Ready(it) }, { if (state is Load.Ready) state else Load.Failed("Couldn't load your drives.") })
        policy = runCatching { app.repo.policy() }.getOrNull() ?: policy
        (state as? Load.Ready)?.data?.filter { it.isGoogle }?.forEach { a -> runCatching { app.repo.importStatus(a.id) }.onSuccess { imports[a.id] = it } }
    }
    // After "Connect Google Drive", poll until the new drive shows up.
    LaunchedEffect(connect) {
        val w = connect as? ConnectState.Waiting ?: return@LaunchedEffect
        val now = awaitConnectedDrive(app, w)
        if (now != null) { state = Load.Ready(now); app.toast("Drive connected"); app.changed(); app.bringToFront() }
        connect = ConnectState.Idle
    }
    LaunchedEffect(imports.values.any { it.state == "running" }) {
        while (imports.values.any { it.state == "running" }) {
            delay(2500)
            // Imports started here are already watched app-wide (see onImport).
            imports.keys.filter { it !in app.importing }.forEach { id -> runCatching { app.repo.importStatus(id) }.onSuccess { imports[id] = it } }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Drives", "Every connected drive adds to one storage pool") {
            ToolButton(Lucide.RefreshCw, null) { reload++ }
            if ((state as? Load.Ready)?.data?.any { it.isGoogle } == true) ToolButton(Lucide.FolderSync, if (syncing) "Syncing…" else "Sync from Google Drive") {
                if (syncing) return@ToolButton
                syncing = true
                scope.launch {
                    runCatching { app.repo.syncGoogle() }
                        .onSuccess { r ->
                            val added = r.results.sumOf { it.created }; val updated = r.results.sumOf { it.updated }; val removed = r.results.sumOf { it.deleted }
                            app.toast(if (added + updated + removed == 0) "Everything is already in sync" else "Synced: $added new, $updated updated, $removed removed")
                            app.changed(); reload++
                        }
                        .onFailure { app.fail(it, "Couldn't sync with Google Drive") }
                    syncing = false
                }
            }
            ToolButton(Lucide.Database, "Add S3") { s3 = true }
            ToolButton(Lucide.Plus, "Connect Google Drive", primary = true, enabled = connect != ConnectState.Opening) {
                connect = ConnectState.Opening
                scope.launch {
                    connect = try { startGoogleConnect(app, (state as? Load.Ready)?.data) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { app.fail(e, "Couldn't start connecting"); ConnectState.Idle }
                }
            }
        }
        if (connect is ConnectState.Waiting) WaitingForGoogle(Modifier.padding(horizontal = 32.dp, vertical = 4.dp).fillMaxWidth()) { connect = ConnectState.Idle }
        when (val s = state) {
            Load.Loading -> LoadingGrid()
            is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
            is Load.Ready -> if (s.data.isEmpty()) EmptyState(Lucide.HardDrive, "No drives yet", "Connect a Google Drive to start. Each one adds 15 GB free.", Modifier.fillMaxWidth().padding(60.dp))
            else Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                s.data.forEach { a ->
                    DriveCard(a, imports[a.id], importing = a.id in app.importing,
                        onSync = { scope.launch { runCatching { app.repo.syncQuota(a.id) }.onSuccess { reload++ }.onFailure { app.fail(it, "Couldn't refresh") } } },
                        onImport = {
                            // App scope: keeps watching (and notifies when done) even if you leave this page.
                            // One watcher per drive: clicking again while it imports does nothing.
                            if (a.id in app.importing) return@DriveCard
                            app.importing += a.id
                            app.scope.launch {
                                try { runCatching { app.repo.importDrive(a.id) }.onSuccess { st ->
                                    imports[a.id] = st; app.toast("Importing everything from ${a.email}")
                                    var cur = st; var misses = 0
                                    while (cur.state == "running" && misses < 30) {
                                        delay(2500)
                                        runCatching { app.repo.importStatus(a.id) }.onSuccess { cur = it; imports[a.id] = it; misses = 0 }.onFailure { misses++ }
                                    }
                                    if (cur.state == "done" && cur.created + cur.updated + cur.folders == 0) {
                                        // Nothing in Drive itself: a full account like this is full of Google Photos / Gmail.
                                        app.inbox.post("${a.email} has no other Drive files", "Its space is used by Google Photos and Gmail. Open Free up space to copy your photos out.", NoticeRoute.Drives, type = NoticeType.Drive)
                                        app.toast("Nothing else on this Drive. Its space is Google Photos and Gmail.")
                                    } else if (cur.state == "done") {
                                        app.inbox.post("Imported ${a.email}","${cur.created} files and ${cur.folders} folders are now in My Files.", NoticeRoute.Drives, type = NoticeType.Drive)
                                        app.changed()
                                    } else if (cur.state == "failed") app.toast(cur.error ?: "Import failed")
                                }.onFailure { app.fail(it, "Couldn't start the import") } } finally { app.importing -= a.id }
                            }
                        },
                        onFreeUp = { app.go(com.nudge.drive.desktop.Page.FreeUp(a.id)) },
                        onDisconnect = { disconnect = a })
                }
                policy?.let { p ->
                    if (s.data.size > 1) UploadRules(s.data, p) { mode, order ->
                        val before = policy
                        policy = p.copy(mode = mode, priorityAccountIds = order)
                        scope.launch {
                            runCatching { app.repo.setPolicy(mode, order) }.onSuccess { policy = it }
                                .onFailure { policy = before; app.fail(it, "Couldn't save the upload rule") }
                        }
                    }
                }
            }
        }
    }
    if (s3) S3Dialog(app, onDismiss = { s3 = false }) { s3 = false; reload++; app.changed() }
    disconnect?.let { a ->
        ConfirmDialog("Disconnect ${a.email}?", "Its files stay on that drive but disappear from Nudge until you connect it again.", "Disconnect", onDismiss = { disconnect = null }) {
            disconnect = null
            scope.launch { runCatching { app.repo.disconnect(a.id) }.onSuccess { reload++; app.changed() }.onFailure { app.fail(it, "Couldn't disconnect") } }
        }
    }
}

private val RoutingModes = listOf(
    Triple("most_available", "Most free space", "Each upload goes to the drive with the most room left."),
    Triple("round_robin", "Take turns", "Uploads rotate across your drives one by one."),
    Triple("priority", "My order", "Fill drives in the order you choose."),
)

/** Which drive new uploads land on. */
@Composable
private fun UploadRules(accounts: List<ConnectedAccount>, policy: RoutingPolicy, onChange: (mode: String, order: List<String>) -> Unit) {
    val byId = accounts.associateBy { it.id }
    val ordered = policy.priorityAccountIds.mapNotNull { byId[it] } + accounts.filter { it.id !in policy.priorityAccountIds }.sortedBy { it.createdAt }
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Text("New uploads go to", style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
        Text("Choose how Nudge spreads new files across your drives.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoutingModes.forEach { (key, title, body) ->
                val on = policy.mode == key
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.weight(1f).clip(shape).background(if (on) Nd.soft(Nd.Iris) else Nd.FieldSolid).border(1.5.dp, if (on) Nd.Primary else Color.Transparent, shape)
                        .bounceClick { if (!on) onChange(key, ordered.map { it.id }) }.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(if (on) Lucide.CircleDot else Lucide.Circle, null, tint = if (on) Nd.Iris else Nd.Subtle, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                        Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                    }
                }
            }
        }
        if (policy.mode == "priority") {
            Spacer(Modifier.height(14.dp))
            Text("Fill in this order", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
            Spacer(Modifier.height(6.dp))
            ordered.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Nd.FieldSolid).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = Nd.Iris, modifier = Modifier.width(26.dp))
                    Text(a.displayName?.takeIf { it.isNotBlank() }?.let { "$it · ${a.email}" } ?: a.email, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    fun move(d: Int) {
                        val l = ordered.toMutableList(); val j = i + d
                        if (j !in l.indices) return
                        l[i] = l[j].also { l[j] = l[i] }
                        onChange("priority", l.map { it.id })
                    }
                    Icon(Lucide.ChevronUp, "Move up", tint = if (i > 0) Nd.Ink else Nd.Line, modifier = Modifier.size(30.dp).clip(CircleShape).bounceClick(enabled = i > 0) { move(-1) }.padding(5.dp))
                    Icon(Lucide.ChevronDown, "Move down", tint = if (i < ordered.lastIndex) Nd.Ink else Nd.Line,
                        modifier = Modifier.size(30.dp).clip(CircleShape).bounceClick(enabled = i < ordered.lastIndex) { move(1) }.padding(5.dp))
                }
            }
        }
    }
}

@Composable
private fun DriveCard(a: ConnectedAccount, import: ImportStatus?, importing: Boolean, onSync: () -> Unit, onImport: () -> Unit, onFreeUp: () -> Unit, onDisconnect: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (a.isGoogle) Color.White else Nd.soft(Nd.Zip)), contentAlignment = Alignment.Center) {
                if (a.isGoogle) DriveGlyph(Modifier.size(30.dp)) else Icon(Lucide.Database, null, tint = Nd.Zip, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(a.displayName?.takeIf { it.isNotBlank() } ?: a.email, style = MaterialTheme.typography.titleMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (a.isGoogle) a.email else "S3 · ${a.email}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
            if (a.status != "connected") Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                Icon(Lucide.TriangleAlert, null, tint = Nd.Warn, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(a.lastError ?: "Needs attention", style = MaterialTheme.typography.labelMedium, color = Nd.Warn, maxLines = 1)
            }
            ToolButton(Lucide.RefreshCw, null, onClick = onSync)
            Spacer(Modifier.width(8.dp))
            if (a.isGoogle) {
                // Highlighted once the account is nearly full (Gmail / Google Photos stop working at 100 %).
                val full = a.total?.let { t -> t > 0 && a.used.toFloat() / t >= 0.85f } ?: false
                ToolButton(Lucide.Sparkles, "Free up space", primary = full, onClick = onFreeUp); Spacer(Modifier.width(8.dp))
                ToolButton(Lucide.CloudDownload, if (importing) "Importing…" else "Import all", enabled = !importing, onClick = onImport); Spacer(Modifier.width(8.dp))
            }
            ToolButton(Lucide.Unplug, null, onClick = onDisconnect)
        }
        Spacer(Modifier.height(14.dp))
        val total = a.total
        if (total != null && total > 0) {
            LinearProgressIndicator({ (a.used.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
            Spacer(Modifier.height(6.dp))
            Text("${formatBytes(a.used)} used of ${formatBytes(total)} · ${formatBytes(total - a.used)} free", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        } else Text("${formatBytes(a.used)} used", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        import?.takeIf { it.state != "idle" }?.let { st ->
            Spacer(Modifier.height(8.dp))
            Text(
                when (st.state) {
                    "running" -> "Importing… ${st.created} files, ${st.folders} folders so far"
                    "done" -> "Imported ${st.created} files and ${st.folders} folders"
                    "failed" -> "Import failed: ${st.error ?: "unknown error"}"
                    else -> st.state
                },
                style = MaterialTheme.typography.labelMedium, color = if (st.state == "failed") Nd.Danger else Nd.IrisDeep,
            )
        }
    }
}

@Composable
private fun S3Dialog(app: AppState, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var endpoint by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("") }
    var pathStyle by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    NudgeDialog(onDismiss, width = 500) {
        Text("Add S3 storage", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Nd.Ink)
        Text("Any S3-compatible bucket (AWS, Cloudflare R2, Backblaze B2, MinIO…)", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillField(name, { name = it }, "Name (e.g. My R2)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillField(bucket, { bucket = it }, "Bucket", Modifier.weight(1f))
                PillField(region, { region = it }, "Region", Modifier.weight(1f))
            }
            PillField(endpoint, { endpoint = it }, "Endpoint URL (leave empty for AWS)")
            PillField(key, { key = it }, "Access key ID")
            PillField(secret, { secret = it }, "Secret access key", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            PillField(quota, { v -> quota = v.filter { c -> c.isDigit() || c == '.' } }, "Size limit in GB (optional)")
            if (endpoint.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text("Path-style URLs (MinIO, most S3-compatible)", style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
                Switch(pathStyle) { pathStyle = it }
            }
        }
        com.nudge.drive.desktop.ui.ErrorLine(error)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(end = 16.dp).bounceClick(onClick = onDismiss))
            PrimaryButton("Connect", Modifier.width(140.dp), loading = busy) {
                if (name.isBlank() || bucket.isBlank() || key.isBlank() || secret.isBlank()) { error = "Fill in name, bucket, access key and secret."; return@PrimaryButton }
                busy = true; error = null
                scope.launch {
                    runCatching {
                        app.repo.connectS3(name, bucket, region, endpoint, key, secret, if (endpoint.isBlank()) null else pathStyle,
                            quota.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1024 * 1024 * 1024).toLong().toString() })
                    }.onSuccess { onDone() }.onFailure { error = (it as? com.nudge.drive.data.ApiException)?.friendly ?: it.message; busy = false }
                }
            }
        }
    }
}

// ======================================================================================== PC backup

@Composable
fun BackupPage(app: AppState) {
    val settings by app.settings.state.collectAsState()
    val status by app.backup.status.collectAsState()
    var remove by remember { mutableStateOf<BackupFolder?>(null) }
    fun addFolder(dir: File?) {
        if (dir == null) return
        if (settings.backupFolders.any { File(it.path) == dir }) { app.toast("That folder is already backed up"); return }
        app.settings.update { it.copy(backupFolders = it.backupFolders + BackupFolder(dir.absolutePath)) }
        app.backup.start(); app.backup.restartWatcher(); app.backup.trigger()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("PC backup", "Keep folders on this computer backed up to your drives, automatically") {
            if (status is BackupStatus.Full) ToolButton(Lucide.HardDrive, "Add a drive") { app.go(com.nudge.drive.desktop.Page.Drives) }
            if (settings.backupFolders.isNotEmpty()) {
                ToolButton(if (settings.backupPaused) Lucide.Play else Lucide.Pause, if (settings.backupPaused) "Resume" else "Pause") {
                    app.settings.update { it.copy(backupPaused = !it.backupPaused) }; if (!app.settings.value.backupPaused) app.backup.trigger()
                }
                ToolButton(Lucide.RefreshCw, "Back up now") { app.backup.trigger() }
            }
            ToolButton(Lucide.FolderPlus, "Add folder", primary = true) { addFolder(pickFolder(app.awtWindow, "Choose a folder to back up")) }
        }
        StatusCard(status, settings.backupPaused)
        // Older files: how far back the backup goes for files already in the folders.
        Text("Older files", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 32.dp, top = 18.dp))
        Text("New files are always backed up. Choose what to do with the ones you already have.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 2.dp, bottom = 8.dp))
        Row(Modifier.padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BackupRange.entries.forEach { r ->
                val on = settings.backupSinceLabel == r.label
                val sub = when (r) {
                    BackupRange.NewOnly -> "Skip what's already there"
                    BackupRange.Week -> "Recent files"
                    BackupRange.Month -> "About a month of files"
                    BackupRange.All -> "Can take a while and use lots of data"
                }
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.weight(1f).clip(shape).background(if (on) Nd.soft(Nd.Iris) else Nd.FieldSolid).border(1.5.dp, if (on) Nd.Primary else Color.Transparent, shape)
                        .bounceClick {
                            if (!on) {
                                app.settings.update { it.copy(backupSinceMs = r.sinceMs(), backupSinceLabel = r.label) }
                                app.backup.trigger()
                            }
                        }.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(if (on) Lucide.CircleDot else Lucide.Circle, null, tint = if (on) Nd.Iris else Nd.Subtle, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(r.label, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                    }
                }
            }
        }
        // Quick picks for the usual folders.
        val home = File(System.getProperty("user.home"))
        val suggestions = listOf("Desktop", "Documents", "Pictures", "Videos", "Downloads").map { File(home, it) }
            .filter { it.isDirectory && settings.backupFolders.none { b -> File(b.path) == it } }
        if (suggestions.isNotEmpty()) {
            Text("Suggested", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 8.dp))
            Row(Modifier.padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                suggestions.forEach { d -> ToolButton(Lucide.Plus, d.name) { addFolder(d) } }
            }
        }
        Text("Backed-up folders", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 8.dp))
        if (settings.backupFolders.isEmpty()) EmptyState(Lucide.CloudUpload, "Nothing backed up yet", "Add a folder: new and changed files upload in the background, into \"PC backup\" on your drives.", Modifier.fillMaxWidth().padding(40.dp))
        else Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            settings.backupFolders.forEach { bf ->
                val shape = RoundedCornerShape(18.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Nd.soft(Nd.Iris)), contentAlignment = Alignment.Center) {
                        Icon(Lucide.FolderOpen, null, tint = Nd.Iris, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(File(bf.path).name.ifBlank { bf.path }, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                        Text(bf.path, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (!File(bf.path).isDirectory) "Folder not found" else if (bf.lastRunAt == 0L) "Waiting for first backup"
                            else "${bf.uploaded} files backed up · checked ${friendlyDate(java.time.Instant.ofEpochMilli(bf.lastRunAt).toString())}",
                            style = MaterialTheme.typography.labelSmall, color = if (!File(bf.path).isDirectory) Nd.Danger else Nd.IrisDeep,
                        )
                    }
                    Switch(bf.enabled) { on ->
                        app.settings.update { s -> s.copy(backupFolders = s.backupFolders.map { if (it.path == bf.path) it.copy(enabled = on) else it }) }
                        app.backup.restartWatcher(); if (on) app.backup.trigger()
                    }
                    Spacer(Modifier.width(12.dp))
                    ToolButton(Lucide.FolderOpen, null) { Platform.openFile(File(bf.path)) }
                    Spacer(Modifier.width(8.dp))
                    ToolButton(Lucide.Trash2, null) { remove = bf }
                }
            }
        }
    }
    remove?.let { bf ->
        ConfirmDialog("Stop backing up ${File(bf.path).name}?", "Files already backed up stay on your drives.", "Stop", onDismiss = { remove = null }) {
            remove = null
            app.settings.update { s -> s.copy(backupFolders = s.backupFolders.filter { it.path != bf.path }) }
            app.backup.forget(bf.path); app.backup.restartWatcher()
        }
    }
}

@Composable
private fun StatusCard(s: BackupStatus, paused: Boolean) {
    val (title, sub, color) = when {
        paused -> Triple("Backup paused", "Resume to keep your folders safe.", Nd.Warn)
        s is BackupStatus.Full -> Triple("Backup paused: storage is full", "Connect another Google account to keep backing up.", Nd.Warn)
        s is BackupStatus.Uploading -> Triple("Backing up ${s.folder}", "${s.done} of ${s.total} · ${s.current}", Nd.Iris)
        s is BackupStatus.Scanning -> Triple("Checking ${s.folder}", "Looking for new and changed files…", Nd.Iris)
        s is BackupStatus.Error -> Triple("Backup hit a problem", s.message, Nd.Danger)
        s is BackupStatus.Idle -> Triple("Everything is backed up", "New files upload automatically.", Nd.Success)
        else -> Triple("Backup is off", "Add a folder to start.", Nd.Subtle)
    }
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 28.dp), strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Nd.soft(color)), contentAlignment = Alignment.Center) {
                if (s is BackupStatus.Uploading || s is BackupStatus.Scanning) CircularProgressIndicator(Modifier.size(22.dp), color = color, strokeWidth = 2.5.dp)
                else Icon(if (s is BackupStatus.Idle && !paused) Lucide.CircleCheck else Lucide.CloudUpload, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s is BackupStatus.Uploading && s.total > 0) LinearProgressIndicator({ s.done.toFloat() / s.total }, Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
            }
        }
    }
}

@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(on, onChange, colors = androidx.compose.material3.SwitchDefaults.colors(
        checkedTrackColor = Nd.Primary, checkedThumbColor = Color.White, uncheckedTrackColor = Nd.Mist, uncheckedBorderColor = Nd.Line, uncheckedThumbColor = Nd.Subtle,
    ))
}

// ======================================================================================== Settings

@Composable
fun SettingsPage(app: AppState) {
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val user by app.session.user.collectAsState()
    var startup by remember { mutableStateOf(Platform.startWithSystem) }
    var logout by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<com.nudge.drive.data.StorageSummary?>(null) }
    LaunchedEffect(Unit) { summary = runCatching { app.repo.summary() }.getOrNull() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        PageHeader("Settings")
        Section("Account") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Nd.Primary), contentAlignment = Alignment.Center) {
                    Text(user?.name?.take(1)?.uppercase() ?: "?", color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(user?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
                    Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                }
                ToolButton(Lucide.LogOut, "Sign out") { logout = true }
                Spacer(Modifier.width(8.dp))
                ToolButton(Lucide.UserX, "Delete account") { deleteAccount = true }
            }
        }
        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("system", "Match Windows", Lucide.Monitor), Triple("light", "Light", Lucide.Sun), Triple("dark", "Dark", Lucide.Moon)).forEach { (key, label, icon) ->
                    val sel = settings.theme == key
                    val shape = RoundedCornerShape(16.dp)
                    Row(Modifier.clip(shape).background(if (sel) Nd.soft(Nd.Iris) else Nd.FieldSolid).border(1.5.dp, if (sel) Nd.Primary else Color.Transparent, shape)
                        .bounceClick { app.settings.update { it.copy(theme = key) } }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = if (sel) Nd.IrisDeep else Nd.Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (key == "system" && Platform.isMac) "Match macOS" else label, style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
                    }
                }
            }
        }
        Section("Notifications") {
            SettingRow("${if (Platform.isMac) "macOS" else if (Platform.isWindows) "Windows" else "Desktop"} notifications", "Uploads, downloads, shares and alerts") {
                Switch(settings.systemNotifications) { on -> app.settings.update { it.copy(systemNotifications = on) } }
            }
            SettingRow("Storage alerts", "Warn me when a drive is almost full") {
                Switch(settings.storageAlerts) { on -> app.settings.update { it.copy(storageAlerts = on) } }
            }
        }
        Section("This computer") {
            SettingRow("Downloads folder", settings.downloadDir ?: Platform.defaultDownloads.absolutePath) {
                ToolButton(Lucide.FolderOpen, "Change") { pickFolder(app.awtWindow, "Save downloads to")?.let { d -> app.settings.update { it.copy(downloadDir = d.absolutePath) } } }
            }
            if (Platform.isWindows) SettingRow("Start with Windows", "Keeps backup running quietly in the tray") {
                Switch(startup) { Platform.startWithSystem = it; startup = Platform.startWithSystem }
            }
            SettingRow("Keep running in the tray", "Closing the window keeps uploads and backup going") {
                Switch(settings.closeToTray) { on -> app.settings.update { it.copy(closeToTray = on) } }
            }
            if (Platform.isWindows) SettingRow("Right-click › Send to › Nudge Drive", "Upload from File Explorer") {
                ToolButton(Lucide.RefreshCw, "Repair") { app.settings.update { it.copy(sendToInstalled = false) }; Platform.installSendTo(); app.toast("Added to Send to") }
            }
            SettingRow("Cached previews", "Thumbnails and opened files kept for speed") {
                ToolButton(Lucide.Trash2, "Clear") {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { File(Platform.cacheDir, "files").deleteRecursively(); File(Platform.cacheDir, "previews").deleteRecursively(); app.toast("Cache cleared") }
                }
            }
        }
        Section("Sharing") {
            SettingRow("Let phones send files to this computer", "Nearby share over your Wi-Fi as \"${app.nearby.deviceName()}\". You're always asked first.") {
                Switch(settings.nearbyVisible) { on -> app.settings.update { it.copy(nearbyVisible = on) } }
            }
            SettingRow("Nearby share", "Send files to a phone on the same Wi-Fi") {
                ToolButton(Lucide.Radar, "Open") { app.go(com.nudge.drive.desktop.Page.Nearby) }
            }
            SettingRow("API keys", "Upload from scripts, CI jobs and other apps") {
                ToolButton(Lucide.KeyRound, "Manage") { app.go(com.nudge.drive.desktop.Page.ApiKeys) }
            }
        }
        Section("About") {
            Text("Nudge Drive for ${if (Platform.isWindows) "Windows" else if (Platform.isMac) "macOS" else "desktop"} · version ${Platform.version}", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
            Spacer(Modifier.height(6.dp))
            InfoRow("Server", com.nudge.drive.Endpoints.WEB_BASE)
            InfoRow("Connected drives", summary?.accounts?.size?.toString() ?: "…")
            InfoRow("Pool size", summary?.let { formatBytes(it.total) } ?: "…")
            InfoRow("Free space", summary?.let { formatBytes(it.available) } ?: "…")
            Text("Your files stream straight between this computer and your own Google Drive / S3 accounts through your private server.",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (logout) ConfirmDialog("Sign out?", "Backup stops until you sign in again. Your files stay on your drives.", "Sign out", onDismiss = { logout = false }) {
        logout = false; scope.launch { app.logout() }
    }
    if (deleteAccount) com.nudge.drive.ui.components.DeleteAccountDialog(app.repo, user?.email.orEmpty(), onDismiss = { deleteAccount = false }) {
        deleteAccount = false
        // The server already removed the account; this just stops backup and clears local caches.
        scope.launch { app.logout(); app.toast("Your account was deleted") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 8.dp))
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 28.dp).widthIn(max = 900.dp), strong = true) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
    }
}

@Composable
private fun SettingRow(title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
