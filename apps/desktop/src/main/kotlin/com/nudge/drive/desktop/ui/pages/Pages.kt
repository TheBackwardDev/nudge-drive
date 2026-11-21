package com.nudge.drive.desktop.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.BellPlus
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.UserPlus
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.desktop.data.Notice
import com.nudge.drive.desktop.data.NoticeType
import com.nudge.drive.desktop.ui.DriveGlyph
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.util.FolderPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Link2Off
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.X
import com.nudge.drive.data.Breakdown
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.data.ShareLink
import com.nudge.drive.data.StorageSummary
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.Crumb
import com.nudge.drive.desktop.Page
import com.nudge.drive.desktop.route
import com.nudge.drive.desktop.ui.ContextMenuBox
import com.nudge.drive.desktop.ui.FileActionDialogs
import com.nudge.drive.desktop.ui.PageHeader
import com.nudge.drive.desktop.ui.ToolButton
import com.nudge.drive.desktop.ui.copyText
import com.nudge.drive.desktop.ui.rememberFileActions
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.FileGridCell
import com.nudge.drive.ui.components.FileRow
import com.nudge.drive.ui.components.FolderCard
import com.nudge.drive.ui.components.GlassCard
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.StripedBar
import com.nudge.drive.ui.components.TimelineScroller
import com.nudge.drive.ui.components.UsageRing
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.screens.files.MonthHeader
import com.nudge.drive.ui.screens.files.Timeline
import com.nudge.drive.ui.screens.files.TimelineCell
import com.nudge.drive.ui.screens.files.TimelineEntry
import com.nudge.drive.ui.screens.files.buildTimeline
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.friendlyDate
import com.nudge.drive.util.isMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================================== Home

private data class HomeData(
    val summary: StorageSummary, val breakdown: Breakdown?, val recent: List<FileItem>, val folders: List<Folder>,
    val accounts: List<ConnectedAccount>, val totalFiles: Int, val sharedCount: Int,
)

@Composable
fun HomePage(app: AppState) {
    val user by app.session.user.collectAsState()
    val changes by app.changes.collectAsState()
    val settings by app.settings.state.collectAsState()
    val actions = rememberFileActions(app)
    var state by remember { mutableStateOf<Load<HomeData>>(Load.Loading) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(changes, reload) {
        state = runCatching {
            coroutineScope {
                val s = async { app.repo.summary() }
                val b = async { runCatching { app.repo.breakdown() }.getOrNull() }
                val f = async { app.repo.files() }
                val d = async { runCatching { app.repo.recentFolders() }.getOrDefault(emptyList()) }
                val a = async { runCatching { app.repo.accounts() }.getOrDefault(emptyList()) }
                val sh = async { runCatching { app.repo.sharedLinks().size }.getOrDefault(0) }
                val all = f.await()
                HomeData(s.await(), b.await(), all.sortedByDescending { it.createdAt }.take(12), d.await(), a.await(), all.size, sh.await())
            }
        }.onSuccess { app.storageAlerts(it.summary, it.accounts) }
            .fold({ Load.Ready(it) }, { if (state is Load.Ready) state else Load.Failed("Couldn't reach Nudge. Check your connection.") })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Hi, ${user?.name?.substringBefore(' ') ?: "there"}", "Everything across your drives") {
            ToolButton(Lucide.Search, "Search") { app.go(Page.Search("")) }
            ToolButton(Lucide.Upload, "Upload", primary = true) { app.pickAndUpload(null) }
        }
        when (val s = state) {
            Load.Loading -> LoadingGrid()
            is Load.Failed -> EmptyState(Lucide.RefreshCw, "Offline", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
            is Load.Ready -> {
                val d = s.data
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StorageCard(d.summary, Modifier.weight(1.4f)) { app.go(Page.Drives) }
                    BreakdownCard(d.breakdown, d.summary, Modifier.weight(1f))
                }
                // Get started
                val backupOn = settings.backupFolders.any { it.enabled } && !settings.backupPaused
                if (!settings.checklistDismissed && (d.accounts.size < 2 || d.totalFiles == 0 || d.sharedCount == 0 || !backupOn)) {
                    Checklist(app, d, backupOn, Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp)) { app.settings.update { it.copy(checklistDismissed = true) } }
                }
                // Alerts
                d.accounts.filter { a -> a.lastError != null || (a.total?.let { t -> t > 0 && a.used.toFloat() / t > 0.92f } ?: false) }.forEach { a ->
                    AlertCard(
                        if (a.lastError != null) "${a.email} needs attention" else "${a.email} is almost full",
                        if (a.lastError != null) "Click to open Drives and refresh or reconnect it."
                        else if (a.isGoogle) "Click to free up space: move its files or copy its Google Photos to your other drives."
                        else "New uploads will go to your other drives automatically.",
                        Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp),
                    ) { app.go(if (a.lastError == null && a.isGoogle) Page.FreeUp(a.id) else Page.Drives) }
                }
                if (d.folders.isNotEmpty()) {
                    SectionTitle("Recent folders", "All folders") { app.go(Page.Folders) }
                    Row(Modifier.padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        d.folders.take(4).forEach { f -> FolderCard(f, Modifier.weight(1f), onClick = { app.openFolder(f.id, listOf(Crumb(f.id, f.name))) }) }
                        repeat(4 - d.folders.take(4).size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                SectionTitle("Recently added", "See all") { app.go(Page.Recent) }
                if (d.recent.isEmpty()) EmptyState(Lucide.Upload, "No files yet", "Drop files anywhere in this window to upload them.", Modifier.fillMaxWidth().padding(40.dp))
                else Column(Modifier.padding(horizontal = 28.dp)) {
                    d.recent.forEach { f ->
                        ContextMenuBox({ actions.menu(f, d.recent) }) { FileRow(f, onClick = { app.openFile(f, d.recent) }) }
                    }
                }
                if (d.accounts.isNotEmpty()) {
                    SectionTitle("Your drives", "Manage") { app.go(Page.Drives) }
                    DrivesStrip(d.accounts, d.breakdown, Modifier.padding(horizontal = 28.dp)) { app.go(Page.Drives) }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    FileActionDialogs(app, actions)
}

@Composable
private fun Checklist(app: AppState, d: HomeData, backupOn: Boolean, modifier: Modifier, onDismiss: () -> Unit) {
    val steps = listOf<Triple<String, Boolean, () -> Unit>>(
        Triple("Connect a Google Drive", d.accounts.isNotEmpty()) { app.go(Page.Drives) },
        Triple("Add a second drive for more space", d.accounts.size >= 2) { app.go(Page.Drives) },
        Triple("Upload your first file", d.totalFiles > 0) { app.pickAndUpload(null) },
        Triple("Turn on PC backup", backupOn) { app.go(Page.Backup) },
        Triple("Share a file with a link", d.sharedCount > 0) { app.go(Page.Files()) },
    )
    val done = steps.count { it.second }
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Sparkles, null, tint = Nd.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get started", style = MaterialTheme.typography.titleMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
            Text("$done of ${steps.size}", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
            Box(Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).bounceClick(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Lucide.X, "Hide checklist", tint = Nd.Muted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        steps.forEach { (label, ok, go) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick(enabled = !ok, onClick = go).padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ok) Lucide.CircleCheckBig else Lucide.Circle, null, tint = if (ok) Nd.Success else Nd.Subtle, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (ok) Nd.Muted else Nd.Ink, modifier = Modifier.weight(1f))
                if (!ok) Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AlertCard(title: String, body: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
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

/** Each connected drive with its own usage ring, plus what's taking the space. */
@Composable
private fun DrivesStrip(accounts: List<ConnectedAccount>, b: Breakdown?, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        val shown = accounts.take(4)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            shown.forEach { a ->
                val t = a.total
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UsageRing(if (t == null || t <= 0) 0f else a.used.toFloat() / t, size = 46.dp, stroke = 4.dp, label = if (t == null) "∞" else null)
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Nd.Surface), contentAlignment = Alignment.Center) {
                            if (a.isGoogle) DriveGlyph(Modifier.size(11.dp)) else Icon(Lucide.Database, null, tint = Nd.Accent, modifier = Modifier.size(11.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.displayName?.takeIf { it.isNotBlank() } ?: a.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (t == null) "${formatBytes(a.used)} used" else "${formatBytes((t - a.used).coerceAtLeast(0))} free of ${formatBytes(t)}",
                            style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            repeat(4 - shown.size) { Spacer(Modifier.weight(1f)) }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            b?.let {
                Legend("Photos", it.photo.toLongOrNull() ?: 0, Nd.Image)
                Legend("Videos", it.video.toLongOrNull() ?: 0, Nd.Video)
                Legend("Docs", it.document.toLongOrNull() ?: 0, Nd.Doc)
            }
            Spacer(Modifier.weight(1f))
            if (accounts.size > 4) Text("+${accounts.size - 4} more", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep)
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
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
        action?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, modifier = Modifier.bounceClick(onClick = onAction)) }
    }
}

@Composable
private fun StorageCard(s: StorageSummary, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.height(190.dp), strong = true, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UsageRing(if (s.total > 0) s.used.toFloat() / s.total else 0f, size = 120.dp, stroke = 12.dp, label = if (s.total > 0) "${(s.used * 100 / s.total)}%" else "–")
            Spacer(Modifier.width(26.dp))
            Column {
                Text("Pooled storage", style = MaterialTheme.typography.labelLarge, color = Nd.Muted)
                Text(formatBytes(s.available) + " free", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.8).sp, color = Nd.Ink)
                Text("${formatBytes(s.used)} used of ${formatBytes(s.total)}", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
                Spacer(Modifier.height(10.dp))
                Text("${s.accounts.size} drive${if (s.accounts.size == 1) "" else "s"} connected", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep)
            }
        }
    }
}

@Composable
private fun BreakdownCard(b: Breakdown?, s: StorageSummary, modifier: Modifier) {
    GlassCard(modifier.height(190.dp)) {
        Text("What's taking space", style = MaterialTheme.typography.labelLarge, color = Nd.Muted)
        Spacer(Modifier.height(12.dp))
        val rows = listOf(
            Triple("Photos", b?.photo?.toLongOrNull() ?: 0L, Lucide.Image to Nd.Image),
            Triple("Videos", b?.video?.toLongOrNull() ?: 0L, Lucide.Video to Nd.Video),
            Triple("Documents", b?.document?.toLongOrNull() ?: 0L, Lucide.FileText to Nd.Doc),
        )
        val max = rows.maxOf { it.second }.coerceAtLeast(1)
        rows.forEach { (label, bytes, ic) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Nd.soft(ic.second)), contentAlignment = Alignment.Center) {
                    Icon(ic.first, null, tint = ic.second, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row { Text(label, style = MaterialTheme.typography.labelMedium, color = Nd.Ink, modifier = Modifier.weight(1f)); Text(formatBytes(bytes), style = MaterialTheme.typography.labelMedium, color = Nd.Muted) }
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Nd.Mist)) {
                        Box(Modifier.fillMaxWidth(bytes.toFloat() / max).fillMaxHeight().clip(CircleShape).background(ic.second))
                    }
                }
            }
        }
    }
}

// ======================================================================================== Photos

@Composable
fun PhotosPage(app: AppState) {
    val changes by app.changes.collectAsState()
    val actions = rememberFileActions(app)
    var state by remember { mutableStateOf<Load<Timeline>>(Load.Loading) }
    var reload by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(changes, reload) {
        state = runCatching {
            val all = app.repo.files()
            withContext(Dispatchers.Default) { buildTimeline(all.filter { it.isMedia }, oldestFirst = false) }
        }.fold({ Load.Ready(it) }, { if (state is Load.Ready) state else Load.Failed("Couldn't load your photos.") })
    }
    Column(Modifier.fillMaxSize()) {
        val t = (state as? Load.Ready)?.data
        PageHeader("Photos", t?.let { "${it.files.size} photos and videos" }) {
            ToolButton(Lucide.RefreshCw, null) { reload++ }
            ToolButton(Lucide.Upload, "Upload", primary = true) { app.pickAndUpload(null) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                Load.Loading -> LoadingGrid()
                is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.align(Alignment.Center)) { SecondaryButton("Try again") { reload++ } }
                is Load.Ready -> if (s.data.files.isEmpty()) EmptyState(Lucide.Image, "No photos yet", "Turn on PC backup or drop photos here to upload them.", Modifier.align(Alignment.Center))
                else {
                    val tl = s.data
                    val grid = rememberLazyGridState()
                    LazyVerticalGrid(GridCells.Adaptive(150.dp), state = grid, contentPadding = PaddingValues(start = 28.dp, end = 60.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(tl.entries, key = { it.key }, span = { if (it is TimelineEntry.Month) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { e ->
                            when (e) {
                                is TimelineEntry.Month -> MonthHeader(e)
                                is TimelineEntry.Photo -> ContextMenuBox({ actions.menu(e.file, tl.files) }) {
                                    TimelineCell(e.file, e.file.id in selected, selected.isNotEmpty(), Modifier, onClick = {
                                        val m = windowInfo.keyboardModifiers
                                        if (m.isCtrlPressed || m.isMetaPressed || selected.isNotEmpty()) { if (e.file.id in selected) selected.remove(e.file.id) else selected.add(e.file.id) }
                                        else app.openFile(e.file, tl.files)
                                    }, onLongClick = { selected.add(e.file.id) })
                                }
                            }
                        }
                    }
                    TimelineScroller(grid, { 0 }, tl.entries.size, { tl.labels.getOrElse(it) { "" } }, tl.yearMarks, Modifier.align(Alignment.CenterEnd))
                }
            }
            val all = t?.files.orEmpty()
            PhotoSelectionBar(app, all.filter { it.id in selected }, allSelected = all.isNotEmpty() && selected.size >= all.size, actions = actions,
                onSelectAll = { selected.clear(); selected.addAll(all.map { it.id }) }, onClear = { selected.clear() },
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    // Clear the selection once a move / delete from the bar goes through.
    LaunchedEffect(changes) { selected.clear() }
    FileActionDialogs(app, actions)
}

/** Same actions as the Files selection bar (same functions), plus Select all. */
@Composable
private fun PhotoSelectionBar(
    app: AppState, selection: List<FileItem>, allSelected: Boolean, actions: com.nudge.drive.desktop.ui.FileActionsState,
    onSelectAll: () -> Unit, onClear: () -> Unit, modifier: Modifier,
) {
    AnimatedVisibility(selection.isNotEmpty(), modifier.padding(bottom = 22.dp), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Nd.Inverse).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Lucide.X, "Clear selection", tint = Nd.OnInverse, modifier = Modifier.size(20.dp).bounceClick(onClick = onClear))
            Text("${selection.size} selected", color = Nd.OnInverse, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp))
            if (!allSelected) BarAction(Lucide.ListChecks, "Select all", onSelectAll)
            BarAction(Lucide.ArrowDownToLine, if (selection.size > 1) "Download zip" else "Download") {
                if (selection.size > 1) app.transfers.downloadZip(selection.map { it.id }) else app.transfers.download(selection)
                app.showTransfers = true; onClear()
            }
            if (selection.size == 1) BarAction(Lucide.Link, "Link") { actions.share(selection[0]); onClear() }
            BarAction(Lucide.Radar, "Send to phone") { app.sendToPhone(selection); onClear() }
            BarAction(Lucide.FolderInput, "Move") { actions.move = selection }
            BarAction(Lucide.Trash2, "Delete") { actions.delete = selection }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Nd.OnInverse, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Nd.OnInverse, style = MaterialTheme.typography.labelMedium)
    }
}

// ======================================================================================== Folders

private enum class FolderSort(val label: String) { NameAZ("Name A–Z"), NameZA("Name Z–A"), Newest("Newest"), Oldest("Oldest"), Updated("Recently changed") }

@Composable
fun FoldersPage(app: AppState) {
    val changes by app.changes.collectAsState()
    val actions = rememberFileActions(app)
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<Folder>>>(Load.Loading) }
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(FolderSort.NameAZ) }
    var newFolder by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(changes, reload) {
        state = runCatching { app.repo.allFolders() }.fold({ Load.Ready(it) }, { Load.Failed("Couldn't load folders.") })
    }
    Column(Modifier.fillMaxSize()) {
        val all = (state as? Load.Ready)?.data.orEmpty()
        PageHeader("All folders", "${all.size} folder${if (all.size == 1) "" else "s"} across your drives") {
            PillField(q, { q = it }, "Filter folders", Modifier.width(260.dp), leading = Lucide.Search)
            ToolButton(Lucide.FolderPlus, "New folder", primary = true) { newFolder = true }
        }
        Row(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderSort.entries.forEach { s -> Chip(s.label, sort == s) { sort = s } }
        }
        when (val s = state) {
            Load.Loading -> LoadingGrid()
            is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
            is Load.Ready -> {
                val byId = s.data.associateBy { it.id }
                fun trail(f: Folder): List<Crumb> {
                    val out = ArrayList<Crumb>(); var cur: Folder? = f; var guard = 0
                    while (cur != null && guard++ < 30) { out.add(0, Crumb(cur.id, cur.name)); cur = cur.parentId?.let { byId[it] } }
                    return out
                }
                val filtered = s.data.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
                val shown = when (sort) {
                    FolderSort.NameAZ -> filtered.sortedBy { it.name.lowercase() }
                    FolderSort.NameZA -> filtered.sortedByDescending { it.name.lowercase() }
                    FolderSort.Newest -> filtered.sortedByDescending { it.createdAt }
                    FolderSort.Oldest -> filtered.sortedBy { it.createdAt }
                    FolderSort.Updated -> filtered.sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }
                }
                if (shown.isEmpty()) EmptyState(Lucide.Folder, if (q.isBlank()) "No folders yet" else "No folders match", if (q.isBlank()) "Create one to keep things tidy." else "No folder matches \"$q\".", Modifier.fillMaxWidth().padding(60.dp))
                else LazyVerticalGrid(GridCells.Adaptive(200.dp), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(shown, key = { it.id }) { f ->
                        val path = trail(f)
                        ContextMenuBox({ actions.folderMenu(f) { app.openFolder(f.id, path) } }) {
                            FolderCard(f, subtitle = path.dropLast(1).joinToString(" › ") { it.name }.ifBlank { "My files" }, onClick = { app.openFolder(f.id, path) })
                        }
                    }
                }
            }
        }
    }
    if (newFolder) TextInputDialog("New folder", placeholder = "Folder name", confirm = "Create", onDismiss = { newFolder = false }) { name ->
        newFolder = false
        scope.launch {
            runCatching { app.repo.createFolder(name, null, FolderPalette.random()) }
                .onSuccess { app.toast("Folder created"); app.changed() }
                .onFailure { app.fail(it, "Couldn't create the folder") }
        }
    }
    FileActionDialogs(app, actions)
}

// ======================================================================================== Trash

@Composable
fun TrashPage(app: AppState) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<FileItem>>>(Load.Loading) }
    var reload by remember { mutableStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var deleteOne by remember { mutableStateOf<FileItem?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var confirmSelected by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(reload) { state = runCatching { app.repo.trash() }.fold({ Load.Ready(it) }, { Load.Failed("Couldn't load the trash.") }) }
    fun restore(ids: List<String>) = scope.launch {
        runCatching { app.repo.restore(ids) }.onSuccess { app.toast("Restored ${it.restored ?: ids.size}"); selected.clear(); reload++; app.changed() }.onFailure { app.fail(it, "Couldn't restore") }
    }
    fun purge(ids: List<String>) = scope.launch {
        runCatching { app.repo.deleteForever(ids) }.onSuccess { app.toast("Deleted ${it.deleted ?: ids.size} forever"); selected.clear(); reload++; app.changed() }.onFailure { app.fail(it, "Couldn't delete") }
    }
    Column(Modifier.fillMaxSize()) {
        val items = (state as? Load.Ready)?.data.orEmpty()
        PageHeader("Trash", if (selected.isNotEmpty()) "${selected.size} selected" else "Click files to select them. Restore puts files back where they were.") {
            if (selected.isNotEmpty()) {
                ToolButton(Lucide.X, "Clear") { selected.clear() }
                if (selected.size < items.size) ToolButton(Lucide.ListChecks, "Select all") { selected.clear(); selected.addAll(items.map { it.id }) }
                ToolButton(Lucide.ArchiveRestore, "Restore selected") { restore(selected.toList()) }
                ToolButton(Lucide.Trash2, "Delete forever") { confirmSelected = selected.toList() }
            } else if (items.isNotEmpty()) {
                ToolButton(Lucide.RotateCcw, "Restore all") { restore(items.map { it.id }) }
                ToolButton(Lucide.Trash2, "Empty trash") { confirmEmpty = true }
            }
        }
        when (val s = state) {
            Load.Loading -> LoadingGrid()
            is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
            is Load.Ready -> if (s.data.isEmpty()) EmptyState(Lucide.Trash2, "Trash is empty", "Deleted files show up here.", Modifier.fillMaxWidth().padding(60.dp))
            else LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp)) {
                items(s.data, key = { it.id }) { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            FileRow(f, selected = f.id in selected, selectionMode = true, subtitle = "${formatBytes(f.size)} · deleted ${friendlyDate(f.deletedAt)}",
                                onClick = { if (f.id in selected) selected.remove(f.id) else selected.add(f.id) })
                        }
                        ToolButton(Lucide.RotateCcw, "Restore") { restore(listOf(f.id)) }
                        Spacer(Modifier.width(8.dp))
                        ToolButton(Lucide.X, null) { deleteOne = f }
                    }
                }
            }
        }
    }
    if (confirmEmpty) ConfirmDialog("Empty trash?", "Every file in Trash is deleted from your drives for good. This can't be undone.", "Delete forever", onDismiss = { confirmEmpty = false }) {
        confirmEmpty = false; purge((state as? Load.Ready)?.data.orEmpty().map { it.id })
    }
    confirmSelected?.let { ids ->
        ConfirmDialog("Delete ${ids.size} file${if (ids.size > 1) "s" else ""} forever?", "They'll be removed from Google Drive / S3 too. This can't be undone.", "Delete forever",
            onDismiss = { confirmSelected = null }) { confirmSelected = null; purge(ids) }
    }
    deleteOne?.let { f ->
        ConfirmDialog("Delete forever?", "\"${f.name}\" is deleted from your drive. This can't be undone.", "Delete forever", onDismiss = { deleteOne = null }) { deleteOne = null; purge(listOf(f.id)) }
    }
}

// ======================================================================================== Inbox

@Composable
private fun NoticeType.visual(): Pair<ImageVector, Color> = when (this) {
    NoticeType.Upload -> Lucide.CloudUpload to Nd.Iris
    NoticeType.UploadFailed -> Lucide.CircleAlert to Nd.Pdf
    NoticeType.Download -> Lucide.Download to Nd.Image
    NoticeType.Drive -> Lucide.HardDrive to Nd.Doc
    NoticeType.Storage -> Lucide.TriangleAlert to Nd.Video
    NoticeType.Share -> Lucide.Link to Nd.Zip
    NoticeType.Invite -> Lucide.UserPlus to Nd.Image
    NoticeType.Backup -> Lucide.ShieldCheck to Nd.Iris
    NoticeType.Nearby -> Lucide.Radar to Nd.Doc
    NoticeType.Info -> Lucide.Info to Nd.Iris
}

private val noticeTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val noticeDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun noticeDayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "Earlier" }
}

private fun noticeStamp(ms: Long): String {
    val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (z.toLocalDate() == LocalDate.now()) noticeTime.format(z) else noticeDay.format(z) + " · " + noticeTime.format(z)
}

@Composable
fun InboxPage(app: AppState) {
    val items by app.inbox.items.collectAsState()
    val settings by app.settings.state.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Notifications", items.count { !it.read }.takeIf { it > 0 }?.let { "$it unread" }) {
            if (items.any { !it.read }) ToolButton(Lucide.CheckCheck, "Mark all read") { app.inbox.markAllRead() }
            if (items.isNotEmpty()) ToolButton(Lucide.Trash2, "Clear all") { confirmClear = true }
        }
        LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!settings.systemNotifications) item(key = "sys-off") {
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(Nd.Mist).bounceClick { app.settings.update { it.copy(systemNotifications = true) } }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.BellPlus, null, tint = Nd.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Turn on desktop notifications", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                        Text("Get told when uploads finish, friends share files or a drive fills up.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                    }
                }
            }
            if (items.isEmpty()) item(key = "empty") {
                EmptyState(Lucide.BellOff, "You're all caught up", "Uploads, shares and storage alerts will show up here.", Modifier.fillMaxWidth().padding(60.dp))
            }
            var lastLabel = ""
            items.forEach { n ->
                val label = noticeDayLabel(n.time)
                if (label != lastLabel) {
                    lastLabel = label
                    item(key = "h-$label") { Text(label, style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) }
                }
                item(key = n.id) { NoticeRow(n, onRemove = { app.inbox.remove(n.id) }) { app.inbox.markRead(n.id); app.route(n.route) } }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Clear all notifications?", "This only clears the list in the app.", "Clear", onDismiss = { confirmClear = false }) {
        confirmClear = false; app.inbox.clear()
    }
}

@Composable
private fun NoticeRow(n: Notice, onRemove: () -> Unit, onClick: () -> Unit) {
    val (icon, color) = n.type.visual()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (n.read) Nd.Surface else Nd.soft(Nd.Iris)).border(1.dp, if (n.read) Nd.CardBorder else Nd.Lilac, shape)
            .bounceClick(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Nd.soft(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!n.read) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(Nd.Accent))
            }
            Text(n.body, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(noticeStamp(n.time), style = MaterialTheme.typography.bodySmall, color = Nd.Subtle)
        Spacer(Modifier.width(10.dp))
        Icon(Lucide.X, "Remove", tint = Nd.Subtle, modifier = Modifier.size(16.dp).bounceClick(onClick = onRemove))
    }
}
