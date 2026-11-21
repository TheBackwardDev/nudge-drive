package com.nudge.drive.ui.screens.home

import com.composables.icons.lucide.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.Breakdown
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.data.NoticeType
import com.nudge.drive.data.StorageSummary
import com.nudge.drive.ui.components.FileThumb
import com.nudge.drive.ui.components.FolderCard
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.StripedBar
import com.nudge.drive.ui.components.UsageRing
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.ui.nav.shared
import com.nudge.drive.ui.screens.auth.DriveGlyph
import com.nudge.drive.ui.screens.main.MainActions
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.friendlyDate
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    var summary by mutableStateOf<StorageSummary?>(null); private set
    var breakdown by mutableStateOf<Breakdown?>(null); private set
    var accounts by mutableStateOf<List<ConnectedAccount>>(emptyList()); private set
    var recent by mutableStateOf<List<FileItem>>(emptyList()); private set
    var totalFiles by mutableStateOf(0); private set
    var folders by mutableStateOf<List<Folder>>(emptyList()); private set
    var sharedCount by mutableStateOf(0); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var offline by mutableStateOf(false); private set
    val backupOn: Boolean get() = c.backup.config.value.enabled && c.backup.config.value.hasSources

    init {
        load()
        viewModelScope.launch { c.transfers.uploaded.collect { load() } }
        viewModelScope.launch { c.refreshBus.collect { load() } }
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    fun load(pull: Boolean = false) {
        if (!pull && loadJob?.isActive == true && !refreshing) { pendingReload = true; return } // coalesce bursts (20 uploads → 1 reload)
        loadJob?.cancel()
        if (pull) refreshing = true
        loadJob = viewModelScope.launch { doLoad(pull) }
    }
    private var pendingReload = false

    private suspend fun doLoad(pull: Boolean): Unit = kotlinx.coroutines.coroutineScope {
        try {
            val s = async { c.repo.summary() }
            val b = async { c.repo.breakdown() }
            val f = async { c.repo.files() }
            val r = async { c.repo.recentFolders() }
            val a = async { c.repo.accounts() }
            val sh = async { runCatching { c.repo.sharedLinks().size }.getOrDefault(0) }
            summary = s.await(); breakdown = b.await(); accounts = a.await()
            val all = f.await(); totalFiles = all.size; recent = all.take(10); all.forEach { c.fileCache[it.id] = it }
            folders = r.await(); sharedCount = sh.await()
            offline = false
            checkAlerts()
            checkInvites()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            offline = summary == null
            if (pull) c.toast((e as? ApiException)?.friendly ?: "Couldn't refresh. Check your connection.")
        }
        loading = false; refreshing = false
        if (pendingReload) { pendingReload = false; load() }
    }

    /** Raise a notice (once a day) when the pool or a single drive is nearly full. */
    private fun checkAlerts() {
        if (!c.prefs.storageAlerts.value) return
        val s = summary ?: return
        if (s.total > 0 && s.used.toFloat() / s.total > 0.85f) {
            c.notices.post(NoticeType.Storage, "Storage pool is ${(s.used * 100 / s.total)}% full", "Only ${formatBytes(s.available)} left. Connect another Google account to add 15 GB.", key = "pool-full", dedupeMs = DAY)
        }
        accounts.forEach { a ->
            val t = a.total ?: return@forEach
            if (t > 0 && a.used.toFloat() / t > 0.92f) c.notices.post(
                NoticeType.Storage, "${a.email} is almost full",
                if (a.isGoogle) "Tap to move its files to your other drives so Gmail and Google Photos keep working." else "New uploads will go to your other drives.",
                key = "full-${a.id}", dedupeMs = DAY, route = if (a.isGoogle) Routes.freeUp(a.id) else null,
            )
            if (a.lastError != null) c.notices.post(NoticeType.Drive, "${a.email} needs attention", "Its storage couldn't be refreshed. Try reconnecting it in Drives.", key = "err-${a.id}", dedupeMs = DAY)
        }
    }

    /** Turn newly received invites into notices. */
    private suspend fun checkInvites() {
        val received = runCatching { c.repo.invites().received }.getOrNull() ?: return
        val seen = c.prefs.seenInvites.value
        val fresh = if (seen == null) emptyList() else received.filter { it.id !in seen }
        fresh.forEach { inv ->
            c.notices.post(NoticeType.Invite, "${inv.user?.name ?: inv.email} shared “${inv.target?.name ?: "a file"}”", "You can ${if (inv.role == "editor") "view and edit" else "view"} it.", key = "inv-${inv.id}")
        }
        c.prefs.setSeenInvites(received.map { it.id }.toSet() + (seen ?: emptySet()))
    }

    fun createFolder(name: String, color: String) = viewModelScope.launch {
        runCatching { c.repo.createFolder(name, null, color) }
            .onSuccess { c.toast("Folder “$name” created"); load(); c.refreshBus.tryEmit(Unit) }
            .onFailure { c.toast((it as? ApiException)?.friendly ?: "Couldn't create folder") }
    }

    companion object { const val DAY = 24L * 60 * 60 * 1000 }
}

/** Fade + rise each section in, one after another. */
private fun Modifier.stagger(index: Int): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(55L * index); p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
    graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 50f }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(c: AppContainer, vm: HomeViewModel, actions: MainActions) {
    val user by c.session.user.collectAsState()
    val notices by c.notices.items.collectAsState()
    val unread = notices.count { !it.read }
    val checklistDismissed by c.prefs.checklistDismissed.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    PullToRefreshBox(vm.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Greeting + bell
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp).stagger(0), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).softShadow(CircleShape, 12.dp).clip(CircleShape).background(Nd.Surface).bounceClick { actions.goTab("profile") },
                        contentAlignment = Alignment.Center,
                    ) { Text(user?.name?.firstOrNull()?.uppercase() ?: "N", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Nd.Ink) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
                        Text(user?.name?.substringBefore(' ') ?: "there", style = MaterialTheme.typography.headlineSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BellButton(unread) { actions.navigate(Routes.Notifications) }
                }
            }
            // 2. Search
            item {
                PillField(
                    query, { query = it }, "Search all your files", Modifier.padding(horizontal = 20.dp).stagger(1),
                    leading = Lucide.Search, glass = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) actions.search(query) }),
                    trailing = {
                        AnimatedVisibility(query.isNotBlank(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Nd.Primary).bounceClick { actions.search(query) }, contentAlignment = Alignment.Center) {
                                Icon(Lucide.ArrowRight, "Search", tint = Nd.OnPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                )
            }
            if (vm.offline) item { OfflineCard(Modifier.padding(horizontal = 20.dp)) { vm.load(pull = true) } }
            // 3. Storage hero
            item { StorageHero(vm, Modifier.padding(horizontal = 20.dp).stagger(2)) { actions.goTab("drives") } }
            // 4. Quick actions
            item { QuickActions(actions, Modifier.padding(horizontal = 20.dp).stagger(3)) }
            // 5. Getting started
            if (!vm.loading && !checklistDismissed && (vm.accounts.size < 2 || vm.totalFiles == 0 || vm.sharedCount == 0 || !vm.backupOn)) item {
                Checklist(vm, actions, Modifier.padding(horizontal = 20.dp).stagger(4)) { c.prefs.dismissChecklist() }
            }
            // 6. Alerts
            val alerts = vm.accounts.filter { a -> a.lastError != null || (a.total?.let { t -> t > 0 && a.used.toFloat() / t > 0.92f } ?: false) }
            items(alerts, key = { "alert-" + it.id }) { a ->
                AlertCard(
                    if (a.lastError != null) "${a.email} needs attention" else "${a.email} is almost full",
                    if (a.lastError != null) "Tap to open Drives and refresh or reconnect it."
                    else if (a.isGoogle) "Tap to free up space: move its files to your other drives so Gmail keeps working."
                    else "New uploads will go to your other drives automatically.",
                    Modifier.padding(horizontal = 20.dp).animateItem(),
                ) { if (a.lastError == null && a.isGoogle) actions.navigate(Routes.freeUp(a.id)) else actions.goTab("drives") }
            }
            // 7. Recent files
            item {
                Column(Modifier.stagger(5)) {
                    SectionTitle("Recent files", "See all", Modifier.padding(horizontal = 20.dp)) { actions.goTab("files") }
                    Spacer(Modifier.height(10.dp))
                    when {
                        vm.loading -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(3) { Shimmer(Modifier.size(150.dp, 186.dp), corner = 24.dp) }
                        }
                        vm.recent.isEmpty() -> EmptyRecent(Modifier.padding(horizontal = 20.dp)) { actions.upload(null) }
                        else -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(vm.recent, key = { it.id }) { f -> RecentFileCard(f, Modifier.animateItem()) { actions.openFile(f, vm.recent) } }
                        }
                    }
                }
            }
            // 8. Folders
            if (vm.folders.isNotEmpty()) item {
                Column(Modifier.stagger(6)) {
                    SectionTitle("Folders", "All folders", Modifier.padding(horizontal = 20.dp)) { actions.navigate(Routes.Folders) }
                    LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(vm.folders, key = { it.id }) { f -> FolderCard(f, onClick = { actions.openFolder(f) }) }
                    }
                }
            }
            // 9. Drives
            if (vm.accounts.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 20.dp).stagger(7)) {
                    SectionTitle("Your drives", "Manage") { actions.goTab("drives") }
                    Spacer(Modifier.height(10.dp))
                    DrivesStrip(vm.accounts) { actions.goTab("drives") }
                }
            }
        }
    }
}

@Composable
private fun BellButton(unread: Int, onClick: () -> Unit) {
    Box {
        GlassIconButton(if (unread > 0) Lucide.BellRing else Lucide.Bell, if (unread > 0) "Notifications, $unread unread" else "Notifications", onClick = onClick)
        AnimatedVisibility(unread > 0, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp), enter = scaleIn(spring(dampingRatio = 0.5f)), exit = scaleOut()) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Nd.Danger).border(2.dp, Nd.Surface, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (unread > 9) "9+" else "$unread", color = if (Nd.isDark) Color.Black else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, modifier = Modifier.weight(1f))
        if (action != null) Row(
            Modifier.clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep)
            Icon(Lucide.ChevronRight, null, tint = Nd.IrisDeep, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StorageHero(vm: HomeViewModel, modifier: Modifier, onClick: () -> Unit) {
    val s = vm.summary
    val frac = if (s == null || s.total <= 0) 0f else (s.used.toFloat() / s.total)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().softShadow(shape, 20.dp, 0.14f).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape)
            .bounceClick(onClick = onClick).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
                Icon(Lucide.HardDrive, null, tint = Nd.Accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Storage pool", style = MaterialTheme.typography.titleSmall, color = Nd.Muted, modifier = Modifier.weight(1f))
            Text("${vm.accounts.size} drive${if (vm.accounts.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = Nd.Ink,
                modifier = Modifier.clip(CircleShape).background(Nd.Mist).padding(horizontal = 10.dp, vertical = 5.dp))
        }
        Spacer(Modifier.height(14.dp))
        if (s == null) {
            Shimmer(Modifier.fillMaxWidth(0.6f).height(38.dp)); Spacer(Modifier.height(12.dp)); Shimmer(Modifier.fillMaxWidth().height(20.dp))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedContent(formatBytes(s.available), label = "free", transitionSpec = { fadeIn() togetherWith fadeOut() }) {
                    Text(it, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp, color = Nd.Ink)
                }
                Text(" free", style = MaterialTheme.typography.titleMedium, color = Nd.Muted, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Text("${(frac * 100).toInt()}% used", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(10.dp))
            StripedBar(frac, Modifier.fillMaxWidth(), height = 16.dp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.breakdown?.let { b ->
                    Legend("Photos", b.photo.toLongOrNull() ?: 0, Nd.Image)
                    Legend("Videos", b.video.toLongOrNull() ?: 0, Nd.Video)
                    Legend("Docs", b.document.toLongOrNull() ?: 0, Nd.Doc)
                }
            }
            Text("${formatBytes(s.used)} of ${formatBytes(s.total)} used · tap to manage drives", style = MaterialTheme.typography.bodySmall, color = Nd.Subtle, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun Legend(label: String, bytes: Long, color: Color) {
    Row(Modifier.clip(CircleShape).background(Nd.soft(color)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp))
        Text("$label ${formatBytes(bytes)}", style = MaterialTheme.typography.labelSmall, color = Nd.Ink)
    }
}

@Composable
private fun QuickActions(actions: MainActions, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Quick(Lucide.CloudUpload, "Upload", Modifier.weight(1f), primary = true) { actions.upload(null) }
        Quick(Lucide.Radar, "Nearby", Modifier.weight(1f)) { actions.navigate(Routes.Nearby) }
        Quick(Lucide.FolderPlus, "Folder", Modifier.weight(1f)) { actions.newFolder() }
        Quick(Lucide.HardDriveUpload, "Add drive", Modifier.weight(1f)) { actions.goTab("drives") }
        Quick(Lucide.ShieldCheck, "Backup", Modifier.weight(1f)) { actions.navigate(Routes.Backup) }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).bounceClick(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier.size(58.dp).softShadow(shape, 12.dp, if (primary) 0.22f else 0.08f).clip(shape)
                .background(if (primary) Nd.Primary else Nd.Glass).border(1.dp, if (primary) Nd.Primary else Nd.GlassBorder, shape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (primary) Nd.OnPrimary else Nd.Ink, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Nd.Ink, maxLines = 1)
    }
}

@Composable
private fun Checklist(vm: HomeViewModel, actions: MainActions, modifier: Modifier, onDismiss: () -> Unit) {
    val steps = listOf(
        Triple("Connect a Google Drive", vm.accounts.isNotEmpty()) { actions.goTab("drives") },
        Triple("Add a second drive for more space", vm.accounts.size >= 2) { actions.goTab("drives") },
        Triple("Upload your first file", vm.totalFiles > 0) { actions.upload(null) },
        Triple("Turn on auto backup", vm.backupOn) { actions.navigate(Routes.Backup) },
        Triple("Share a file with a link", vm.sharedCount > 0) { actions.goTab("files") },
    )
    val done = steps.count { it.second }
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.fillMaxWidth().softShadow(shape, 14.dp, 0.1f).clip(shape).background(Nd.Glass).border(1.dp, Nd.GlassBorder, shape).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Sparkles, null, tint = Nd.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get started", style = MaterialTheme.typography.titleMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
            Text("$done of ${steps.size}", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
            Box(Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).bounceClick(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Lucide.X, "Hide checklist", tint = Nd.Muted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        steps.forEach { (label, ok, go) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).bounceClick(enabled = !ok, onClick = go).padding(vertical = 9.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ok) Lucide.CircleCheckBig else Lucide.Circle, null, tint = if (ok) Nd.Success else Nd.Subtle, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (ok) Nd.Muted else Nd.Ink, modifier = Modifier.weight(1f))
                if (!ok) Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AlertCard(title: String, body: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Nd.WarnSoft).bounceClick(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.TriangleAlert, null, tint = Nd.Warn, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        Icon(Lucide.ChevronRight, null, tint = Nd.Muted)
    }
}

@Composable
private fun OfflineCard(modifier: Modifier, onRetry: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Nd.DangerSoft).bounceClick(onClick = onRetry).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.CloudOff, null, tint = Nd.Danger)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Can't reach your server", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Text("Check your internet connection. Tap to try again.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        Icon(Lucide.RefreshCw, "Retry", tint = Nd.Danger, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RecentFileCard(f: FileItem, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.width(150.dp).softShadow(shape, 12.dp, 0.1f).clip(shape).background(Nd.Glass).border(1.dp, Nd.GlassBorder, shape)
            .bounceClick(onClick = onClick).padding(8.dp),
    ) {
        FileThumb(f, Modifier.shared("thumb-${f.id}").fillMaxWidth().height(110.dp), size = 134.dp, corner = 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(f.name, style = MaterialTheme.typography.labelLarge, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text("${formatBytes(f.size)} · ${friendlyDate(f.createdAt)}", style = MaterialTheme.typography.labelSmall, color = Nd.Muted, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
private fun EmptyRecent(modifier: Modifier, onUpload: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Nd.GlassSoft).border(1.5.dp, Nd.Lilac, shape).bounceClick(onClick = onUpload).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Nd.Primary), contentAlignment = Alignment.Center) {
            Icon(Lucide.ImagePlus, null, tint = Nd.OnPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Nothing here yet", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Text("Tap to upload photos, videos or documents.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
    }
}

@Composable
private fun DrivesStrip(accounts: List<ConnectedAccount>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().softShadow(shape, 12.dp, 0.08f).clip(shape).background(Nd.Glass).border(1.dp, Nd.GlassBorder, shape).bounceClick(onClick = onClick).padding(8.dp)) {
        accounts.take(4).forEach { a ->
            val t = a.total
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UsageRing(if (t == null || t <= 0) 0f else a.used.toFloat() / t, size = 42.dp, stroke = 4.dp, label = if (t == null) "∞" else null)
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Nd.Surface), contentAlignment = Alignment.Center) {
                        if (a.isGoogle) DriveGlyph(Modifier.size(11.dp)) else Icon(Lucide.Database, null, tint = Nd.Accent, modifier = Modifier.size(11.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.displayName ?: a.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (t == null) "${formatBytes(a.used)} used" else "${formatBytes((t - a.used).coerceAtLeast(0))} free of ${formatBytes(t)}",
                        style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                }
            }
        }
        if (accounts.size > 4) Text("+${accounts.size - 4} more", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
    }
}
