package com.nudge.drive.ui.screens.drives

import com.composables.icons.lucide.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.googleSignIn
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.data.RoutingPolicy
import com.nudge.drive.data.StorageSummary
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.NudgeSheet
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SheetAction
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.UsageRing
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.auth.DriveGlyph
import com.nudge.drive.ui.screens.auth.openCustomTab
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

val RoutingModes = listOf(
    Triple("most_available", "Most free space", "Each upload goes to the drive with the most room left."),
    Triple("round_robin", "Take turns", "Uploads rotate across your drives one by one."),
    Triple("priority", "My order", "Fill drives in the order you choose."),
)

/** Which "connect a drive" is in progress; each button only shows its own. */
enum class Connecting { Google, S3 }

class DrivesViewModel(private val c: AppContainer) : ViewModel() {
    var accounts by mutableStateOf<List<ConnectedAccount>>(emptyList()); private set
    var summary by mutableStateOf<StorageSummary?>(null); private set
    var policy by mutableStateOf(RoutingPolicy()); private set
    var loading by mutableStateOf(true); private set
    var connecting by mutableStateOf<Connecting?>(null); private set
    var syncing by mutableStateOf<String?>(null); private set
    /** Running/finished full-Drive imports by account id. */
    val imports = androidx.compose.runtime.mutableStateMapOf<String, com.nudge.drive.data.ImportStatus>()

    /** Accounts with an import being watched: one watcher each, so a second tap doesn't double the notices. */
    private val importWatched = mutableSetOf<String>()

    fun importDrive(a: ConnectedAccount) {
        if (!importWatched.add(a.id)) return
        viewModelScope.launch {
            try { importAndWatch(a) } finally { importWatched.remove(a.id) }
        }
    }

    private suspend fun importAndWatch(a: ConnectedAccount) {
        runCatching { c.repo.importDrive(a.id) }
            .onSuccess { st ->
                imports[a.id] = st
                c.toast("Importing everything from ${a.email}…")
                // Gives up after ~1 minute of errors in a row (offline, server gone), like the desktop app.
                var misses = 0
                while (imports[a.id]?.state == "running" && misses < 30) {
                    kotlinx.coroutines.delay(2000)
                    runCatching { c.repo.importStatus(a.id) }.onSuccess { imports[a.id] = it; misses = 0 }.onFailure { misses++ }
                }
                val done = imports[a.id]
                if (done?.state == "done") {
                    if (done.created + done.updated + done.folders == 0) {
                        // Nothing in Drive itself: a full account like this is full of Gmail / Google Photos.
                        c.notices.post(com.nudge.drive.data.NoticeType.Drive, "${a.email} has no other Drive files",
                            "Its space is used by Google Photos and Gmail. Open Free up space to copy your photos out.", system = false,
                            route = com.nudge.drive.ui.nav.Routes.freeUp(a.id))
                        c.toast("Nothing else on this Drive. Its space is Google Photos and Gmail.")
                    } else c.notices.post(com.nudge.drive.data.NoticeType.Drive, "Imported ${a.email}", "${done.created} files and ${done.folders} folders are now in My Files.")
                    c.refreshBus.tryEmit(Unit)
                } else if (done?.state == "failed") c.toast(done.error ?: "Import failed")
            }
            .onFailure { c.toast(msg(it)) }
    }

    init { load(); viewModelScope.launch { c.refreshBus.collect { load() } } }

    fun load() = viewModelScope.launch {
        runCatching {
            val a = async { c.repo.accounts() }
            val s = async { c.repo.summary() }
            val p = async { c.repo.policy() }
            accounts = a.await(); summary = s.await(); policy = p.await()
        }.onFailure { c.toast(msg(it)) }
        loading = false
    }

    private fun msg(e: Throwable) = (e as? ApiException)?.friendly ?: e.message ?: "Something went wrong"

    fun connectGoogle(open: (String) -> Unit) {
        if (connecting != null) return
        connecting = Connecting.Google
        viewModelScope.launch {
            runCatching { if (c.googleSignIn(connect = true)) load() else open(c.repo.googleConnectUrl()) }.onFailure { c.toast(msg(it)) }
            connecting = null
        }
    }

    fun connectS3(name: String, bucket: String, region: String, endpoint: String, key: String, secret: String, pathStyle: Boolean, quotaGb: String, done: () -> Unit) {
        if (connecting != null) return
        connecting = Connecting.S3
        viewModelScope.launch {
            val quota = quotaGb.trim().toDoubleOrNull()?.let { (it * 1024 * 1024 * 1024).toLong().toString() }
            runCatching { c.repo.connectS3(name, bucket, region, endpoint, key, secret, if (endpoint.isBlank()) null else pathStyle, quota) }
                .onSuccess { c.toast("Bucket connected"); done(); load(); c.refreshBus.tryEmit(Unit) }
                .onFailure { c.toast(msg(it)) }
            connecting = null
        }
    }

    fun sync(a: ConnectedAccount) = viewModelScope.launch {
        syncing = a.id
        runCatching { c.repo.syncQuota(a.id) }.onSuccess { c.toast("Storage updated for ${a.email}"); load() }.onFailure { c.toast(msg(it)) }
        syncing = null
    }

    fun disconnect(a: ConnectedAccount) = viewModelScope.launch {
        runCatching { c.repo.disconnect(a.id) }.onSuccess { c.toast("Disconnected ${a.email}"); load(); c.refreshBus.tryEmit(Unit) }.onFailure { c.toast(msg(it)) }
    }

    /** Priority order = saved order first, then any accounts not yet listed (oldest first, like the server). */
    val orderedForPriority: List<ConnectedAccount>
        get() {
            val byId = accounts.associateBy { it.id }
            val listed = policy.priorityAccountIds.mapNotNull { byId[it] }
            return listed + accounts.filter { it.id !in policy.priorityAccountIds }.sortedBy { it.createdAt }
        }

    fun setMode(mode: String) = save(mode, orderedForPriority.map { it.id })
    fun move(a: ConnectedAccount, delta: Int) {
        val list = orderedForPriority.toMutableList()
        val i = list.indexOfFirst { it.id == a.id }; val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        policy = policy.copy(priorityAccountIds = list.map { it.id })
        save(policy.mode, list.map { it.id }, quiet = true)
    }

    private fun save(mode: String, order: List<String>, quiet: Boolean = false) = viewModelScope.launch {
        val before = policy
        policy = policy.copy(mode = mode, priorityAccountIds = order)
        runCatching { c.repo.setPolicy(mode, order) }
            .onSuccess { policy = it; if (!quiet) c.toast("Uploads: ${RoutingModes.first { m -> m.first == mode }.second}") }
            .onFailure { policy = before; c.toast(msg(it)) }
    }
}

@Composable
fun DrivesScreen(c: AppContainer, vm: DrivesViewModel) {
    val ctx = LocalContext.current
    var provider by remember { mutableStateOf("google") }
    var s3Open by remember { mutableStateOf(false) }
    var routingOpen by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<ConnectedAccount?>(null) }
    var confirmDisconnect by remember { mutableStateOf<ConnectedAccount?>(null) }

    // Coming back from the Google consent tab → refresh the list.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Connect a drive", style = MaterialTheme.typography.headlineMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
                GlassIconButton(Lucide.RefreshCw, "Refresh") { vm.load() }
            }
        }
        item { ProviderPicker(provider) { provider = it } }
        item {
            AnimatedContent(provider, label = "provTitle", transitionSpec = { fadeIn() togetherWith fadeOut() }) { p ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (p == "google") "Google Drive" else "S3 bucket", style = MaterialTheme.typography.headlineMedium, color = Nd.Ink)
                    Text(
                        if (p == "google") "Every account adds 15 GB of free space" else "AWS, Cloudflare R2, Backblaze B2, MinIO, Wasabi…",
                        style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item { AccountsCard(vm, onOptions = { options = it }, onRouting = { routingOpen = true }, onFreeUp = { c.pendingRoute.value = com.nudge.drive.ui.nav.Routes.freeUp(it.id) }) }
        item {
            PrimaryButton(
                if (provider == "google") "Connect Google Drive" else "Add S3 bucket",
                Modifier.fillMaxWidth(), loading = vm.connecting == Connecting.Google, icon = Lucide.Plus,
            ) { if (provider == "google") vm.connectGoogle { openCustomTab(ctx, it) } else s3Open = true }
        }
        item {
            Text(
                if (provider == "google") "You'll pick an account on Google's page and come straight back here."
                else "Keys are encrypted on your server. Leave the endpoint empty for AWS.",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    options?.let { a ->
        NudgeSheet(onDismiss = { options = null }) {
            Text(a.displayName ?: a.email, style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
            Text(a.email, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(bottom = 12.dp))
            if (a.isGoogle) SheetAction(Lucide.Sparkles, "Free up space", sub = "Move its files to your other drives so it isn't full") {
                options = null; c.pendingRoute.value = com.nudge.drive.ui.nav.Routes.freeUp(a.id)
            }
            if (a.isGoogle) SheetAction(Lucide.HardDriveDownload, "Import everything from this Drive", sub = "Show all its existing files and folders here") { options = null; vm.importDrive(a) }
            SheetAction(Lucide.RefreshCw, "Refresh storage numbers") { options = null; vm.sync(a) }
            SheetAction(Lucide.Unlink, "Disconnect", tint = Nd.Danger, sub = "Files stay listed but can't be opened") { options = null; confirmDisconnect = a }
        }
    }
    confirmDisconnect?.let { a ->
        ConfirmDialog("Disconnect ${a.email}?", "New uploads won't go to this drive any more. You can connect it again later.", "Disconnect",
            onDismiss = { confirmDisconnect = null }) { vm.disconnect(a); confirmDisconnect = null }
    }
    if (routingOpen) RoutingSheet(vm) { routingOpen = false }
    if (s3Open) S3Sheet(vm) { s3Open = false }
}

@Composable
private fun ProviderPicker(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.height(150.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProviderTile(selected == "s3", -8f, { onSelect("s3") }) {
                Icon(Lucide.Database, "S3 bucket", tint = Nd.Iris, modifier = Modifier.size(34.dp))
            }
            ProviderTile(selected == "google", 0f, { onSelect("google") }) { DriveGlyph(Modifier.size(64.dp)) }
            Box(
                Modifier.size(84.dp).rotate(8f).clip(RoundedCornerShape(24.dp)).background(Nd.GlassSoft)
                    .border(1.5.dp, Nd.Lavender, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("More soon", style = MaterialTheme.typography.labelSmall, color = Nd.Muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("s3", "google").forEach { k ->
                val w by animateDpAsState(if (k == selected) 26.dp else 6.dp, spring(dampingRatio = 0.7f), label = "dot")
                val col by animateColorAsState(if (k == selected) Nd.Iris else Nd.Lilac, label = "dotc")
                Box(Modifier.size(w, 6.dp).clip(CircleShape).background(col))
            }
        }
    }
}

@Composable
private fun ProviderTile(active: Boolean, tilt: Float, onClick: () -> Unit, content: @Composable () -> Unit) {
    val size by animateDpAsState(if (active) 128.dp else 86.dp, spring(dampingRatio = 0.7f, stiffness = 300f), label = "size")
    val rot by animateFloatAsState(if (active) 0f else tilt, spring(dampingRatio = 0.6f), label = "rot")
    val bg by animateColorAsState(if (active) Nd.Surface else Nd.GlassSoft, label = "bg")
    val shape = RoundedCornerShape(if (active) 32.dp else 24.dp)
    Box(
        Modifier.size(size).graphicsLayer { rotationZ = rot }.softShadow(shape, if (active) 26.dp else 8.dp, if (active) 0.26f else 0.08f)
            .clip(shape).background(bg).border(1.dp, Nd.GlassBorder, shape).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AccountsCard(vm: DrivesViewModel, onOptions: (ConnectedAccount) -> Unit, onRouting: () -> Unit, onFreeUp: (ConnectedAccount) -> Unit) {
    val s = vm.summary
    Box(Modifier.padding(top = 26.dp)) {
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-26).dp).padding(horizontal = 16.dp).fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Nd.GlassSoft))
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-13).dp).padding(horizontal = 6.dp).fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Nd.Glass))
        val shape = RoundedCornerShape(26.dp)
        Column(Modifier.fillMaxWidth().softShadow(shape, 18.dp, 0.12f).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your accounts", style = MaterialTheme.typography.titleLarge, color = Nd.Ink, modifier = Modifier.weight(1f))
                Text(
                    if (s == null) "…" else "${vm.accounts.size} connected · ${formatBytes(s.total)}",
                    style = MaterialTheme.typography.labelMedium, color = Nd.OnInverse,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Nd.Inverse).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            when {
                vm.loading -> repeat(2) { Shimmer(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)) }
                vm.accounts.isEmpty() -> Text("No drives yet. Connect a Google account to start your pool.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
                else -> vm.accounts.forEach { a -> AccountRow(a, vm.syncing == a.id, vm.imports[a.id]?.takeIf { it.state == "running" }, onFreeUp = { onFreeUp(a) }) { onOptions(a) } }
            }
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Nd.Line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New uploads go to", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Nd.Mist).bounceClick(onClick = onRouting).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(RoutingModes.firstOrNull { it.first == vm.policy.mode }?.second ?: "Most free space", label = "mode") {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = Nd.Ink)
                    }
                    Icon(Lucide.ChevronDown, null, tint = Nd.Ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountRow(a: ConnectedAccount, syncing: Boolean, importing: com.nudge.drive.data.ImportStatus? = null, onFreeUp: () -> Unit = {}, onOptions: () -> Unit) {
    val total = a.total
    val frac = if (total == null || total <= 0) 0f else a.used.toFloat() / total
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).bounceClick(onClick = onOptions).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.BottomEnd) {
            UsageRing(frac, size = 50.dp, stroke = 5.dp, label = if (total == null) "∞" else null)
            Box(Modifier.size(20.dp).clip(CircleShape).background(Nd.Surface), contentAlignment = Alignment.Center) {
                if (a.isGoogle) DriveGlyph(Modifier.size(13.dp)) else Icon(Lucide.Database, null, tint = Nd.Iris, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.displayName ?: a.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (importing != null) "Importing… ${importing.created} files" else if (syncing) "Refreshing…" else "${formatBytes(a.used)} of ${formatBytes(total)}" + (if (a.lastError != null) " · needs attention" else ""),
                style = MaterialTheme.typography.bodySmall, color = if (a.lastError != null) Nd.Danger else Nd.Muted, maxLines = 1,
            )
        }
        // A nearly full Google Drive gets a one-tap way out.
        if (a.isGoogle && frac >= 0.85f) Text(
            "Free up", style = MaterialTheme.typography.labelMedium, color = if (frac >= 0.95f) Nd.Danger else Nd.Warn,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background((if (frac >= 0.95f) Nd.Danger else Nd.Warn).copy(alpha = 0.14f))
                .bounceClick(onClick = onFreeUp).padding(horizontal = 10.dp, vertical = 6.dp),
        ) else if (a.avatarUrl != null) AsyncImage(a.avatarUrl, null, Modifier.size(28.dp).clip(CircleShape))
        Icon(Lucide.EllipsisVertical, "Options", tint = Nd.Muted, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RoutingSheet(vm: DrivesViewModel, onDismiss: () -> Unit) {
    NudgeSheet(onDismiss) {
        Text("Where should uploads go?", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Spacer(Modifier.height(12.dp))
        RoutingModes.forEach { (key, title, body) ->
            val on = vm.policy.mode == key
            val bg by animateColorAsState(if (on) Nd.Mist else Nd.Surface, label = "rbg")
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(bg).border(1.dp, if (on) Nd.Lavender else Nd.Line, shape)
                    .bounceClick { vm.setMode(key) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (on) Lucide.CircleDot else Lucide.Circle, null, tint = if (on) Nd.Iris else Nd.Subtle)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                }
            }
        }
        AnimatedVisibility(vm.policy.mode == "priority", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp)) {
                Text("Fill in this order", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(bottom = 8.dp))
                val list = vm.orderedForPriority
                list.forEachIndexed { i, a ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Nd.Surface).border(1.dp, Nd.Line, RoundedCornerShape(16.dp))
                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = Nd.Iris, modifier = Modifier.width(24.dp))
                        Text(a.email, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ArrowBtn(Lucide.ChevronUp, "Move up", i > 0) { vm.move(a, -1) }
                        ArrowBtn(Lucide.ChevronDown, "Move down", i < list.lastIndex) { vm.move(a, 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrowBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).bounceClick(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) Nd.Ink else Nd.Line)
    }
}

@Composable
private fun S3Sheet(vm: DrivesViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("auto") }
    var endpoint by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("") }
    var pathStyle by remember { mutableStateOf(true) }
    NudgeSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add an S3 bucket", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
            PillField(name, { name = it }, "Display name (e.g. R2 backups)")
            PillField(bucket, { bucket = it }, "Bucket name")
            PillField(region, { region = it }, "Region (e.g. us-east-1, auto)")
            PillField(endpoint, { endpoint = it }, "Endpoint URL (empty for AWS)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            PillField(key, { key = it }, "Access key ID")
            PillField(secret, { secret = it }, "Secret access key", visualTransformation = PasswordVisualTransformation())
            PillField(quota, { quota = it }, "Size limit in GB (optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            AnimatedVisibility(endpoint.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text("Path-style URLs (MinIO, most S3-compatible)", style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
                    Switch(pathStyle, { pathStyle = it }, colors = SwitchDefaults.colors(checkedTrackColor = Nd.Iris))
                }
            }
            PrimaryButton(
                "Test & connect", Modifier.fillMaxWidth().padding(top = 6.dp), loading = vm.connecting == Connecting.S3,
                enabled = name.isNotBlank() && bucket.isNotBlank() && region.isNotBlank() && key.isNotBlank() && secret.isNotBlank(),
            ) { vm.connectS3(name, bucket, region, endpoint, key, secret, pathStyle, quota, onDismiss) }
        }
    }
}
