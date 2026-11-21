package com.nudge.drive.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ArrowUpFromLine
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.Page
import com.nudge.drive.desktop.data.BackupStatus
import com.nudge.drive.desktop.data.Transfer
import com.nudge.drive.desktop.data.TransferKind
import com.nudge.drive.desktop.data.TransferState
import com.nudge.drive.desktop.ui.pages.BackupPage
import com.nudge.drive.desktop.ui.pages.DrivesPage
import com.nudge.drive.desktop.ui.pages.FilesPage
import com.nudge.drive.desktop.ui.pages.FoldersPage
import com.nudge.drive.desktop.ui.pages.HomePage
import com.nudge.drive.desktop.ui.pages.InboxPage
import com.nudge.drive.desktop.ui.pages.PhotosPage
import com.nudge.drive.desktop.ui.pages.SettingsPage
import com.nudge.drive.desktop.ui.pages.SharedPage
import com.nudge.drive.desktop.ui.pages.TrashPage
import com.nudge.drive.desktop.ui.viewer.EditorWindow
import com.nudge.drive.desktop.ui.viewer.Viewer
import com.nudge.drive.ui.brand.LogoTile
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import java.io.File
import java.net.URI

/** Signed-in window: sidebar, page, transfers panel, viewer and drop zone. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Shell(app: AppState) {
    val user by app.session.user.collectAsState()
    if (user?.emailVerified == false) { VerifyEmail(app); return }
    if (needsOnboarding(app)) { Onboarding(app); return }

    var dragging by remember { mutableStateOf(false) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) { dragging = true }
            override fun onEnded(event: DragAndDropEvent) { dragging = false }
            override fun onExited(event: DragAndDropEvent) { dragging = false }
            override fun onEntered(event: DragAndDropEvent) { dragging = true }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val data = event.dragData() as? DragData.FilesList ?: return false
                val files = data.readFiles().mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                if (files.isEmpty()) return false
                app.transfers.upload(files, app.currentFolderId)
                app.showTransfers = true
                return true
            }
        }
    }

    Box(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(app)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    app.page,
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 40 }) togetherWith fadeOut(tween(120)) },
                    contentKey = { it::class },
                    label = "page",
                ) { p ->
                    when (p) {
                        Page.Home -> HomePage(app)
                        is Page.Files -> FilesPage(app, p)
                        is Page.Search -> FilesPage(app, Page.Files(), search = p.query)
                        Page.Recent -> FilesPage(app, Page.Files(), recent = true)
                        Page.Photos -> PhotosPage(app)
                        Page.Folders -> FoldersPage(app)
                        Page.Shared -> SharedPage(app)
                        Page.Nearby -> com.nudge.drive.desktop.ui.pages.NearbyPage(app)
                        Page.Activity -> com.nudge.drive.desktop.ui.pages.ActivityPage(app)
                        Page.ApiKeys -> com.nudge.drive.desktop.ui.pages.ApiKeysPage(app)
                        Page.Drives -> DrivesPage(app)
                        Page.Backup -> BackupPage(app)
                        Page.Trash -> TrashPage(app)
                        Page.Inbox -> InboxPage(app)
                        Page.Settings -> SettingsPage(app)
                        is Page.FreeUp -> com.nudge.drive.desktop.ui.pages.FreeUpPage(app, p.accountId)
                    }
                }
                TransfersPanel(app, Modifier.align(Alignment.BottomEnd))
            }
        }
        AnimatedVisibility(dragging, enter = fadeIn(), exit = fadeOut()) { DropOverlay() }
        app.viewer?.let { Viewer(app, it) }
        app.editorFor?.let { EditorWindow(app, it) }
        Toasts(app, Modifier.align(Alignment.BottomCenter))
        IncomingFilesDialog(app)
        com.nudge.drive.desktop.ui.pages.NearbyRequestDialog(app)
    }
}

// ---------------------------------------------------------------------------------------- sidebar

private data class NavItem(val label: String, val icon: ImageVector, val page: Page, val match: (Page) -> Boolean = { it == page })

@Composable
private fun Sidebar(app: AppState) {
    val items = remember {
        listOf(
            NavItem("Home", Lucide.House, Page.Home),
            NavItem("My files", Lucide.FolderOpen, Page.Files(), { it is Page.Files || it is Page.Search }),
            NavItem("Photos", Lucide.Image, Page.Photos),
            NavItem("Recent", Lucide.Clock, Page.Recent),
            NavItem("All folders", Lucide.Folder, Page.Folders),
            NavItem("Shared", Lucide.Link, Page.Shared),
            NavItem("Nearby share", Lucide.Radar, Page.Nearby),
        )
    }
    val lower = remember {
        listOf(
            NavItem("Drives", Lucide.HardDrive, Page.Drives),
            NavItem("PC backup", Lucide.CloudUpload, Page.Backup),
            NavItem("Activity", Lucide.History, Page.Activity),
            NavItem("Trash", Lucide.Trash2, Page.Trash),
        )
    }
    val inbox by app.inbox.items.collectAsState()
    val unread = inbox.count { !it.read }
    val backup by app.backup.status.collectAsState()
    Column(Modifier.width(232.dp).fillMaxHeight().background(Nd.Sheet).padding(horizontal = 14.dp, vertical = 18.dp)) {
        Row(Modifier.padding(start = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoTile(34.dp)
            Spacer(Modifier.width(10.dp))
            Text("Nudge Drive", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-0.4).sp, color = Nd.Ink)
        }
        NewButton(app)
        Spacer(Modifier.height(14.dp))
        items.forEach { SideLink(it, app) }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Nd.Line))
        Spacer(Modifier.height(10.dp))
        lower.forEach { SideLink(it, app, badge = if (it.page == Page.Backup) backupBadge(backup) else null) }
        Spacer(Modifier.weight(1f))
        StorageMini(app)
        Spacer(Modifier.height(8.dp))
        SideLink(NavItem("Notifications", Lucide.Bell, Page.Inbox), app, count = unread)
        SideLink(NavItem("Settings", Lucide.Settings, Page.Settings), app)
    }
}

private fun backupBadge(s: BackupStatus): Color? = when (s) {
    is BackupStatus.Uploading, is BackupStatus.Scanning -> Color(0xFF8B72F5)
    is BackupStatus.Error -> Color(0xFFE5484D)
    else -> null
}

@Composable
private fun SideLink(item: NavItem, app: AppState, count: Int = 0, badge: Color? = null) {
    val selected = item.match(app.page)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(shape).background(if (selected) Nd.soft(Nd.Iris) else Color.Transparent)
            .bounceClick { app.go(item.page) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = if (selected) Nd.IrisDeep else Nd.Muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(item.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = if (selected) Nd.Ink else Nd.Muted, modifier = Modifier.weight(1f))
        if (count > 0) Box(Modifier.clip(CircleShape).background(Nd.Primary).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        badge?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
    }
}

@Composable
private fun NewButton(app: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(18.dp)).background(Nd.Primary).bounceClick { open = true }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Plus, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("New", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        NewMenu(app, open) { open = false }
    }
}

@Composable
private fun StorageMini(app: AppState) {
    var used by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    val changes by app.changes.collectAsState()
    LaunchedEffect(changes) { runCatching { app.repo.summary() }.onSuccess { used = it.used; total = it.total } }
    if (total <= 0) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nd.Mist).bounceClick { app.go(Page.Drives) }.padding(12.dp)) {
        Text("Storage", style = MaterialTheme.typography.labelMedium, color = Nd.Muted)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator({ (used.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Lilac)
        Spacer(Modifier.height(6.dp))
        Text("${formatBytes(used)} of ${formatBytes(total)}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
    }
}

// ---------------------------------------------------------------------------------------- transfers

@Composable
private fun TransfersPanel(app: AppState, modifier: Modifier) {
    val items by app.transfers.items.collectAsState()
    LaunchedEffect(items.any { it.active }) { if (items.any { it.active }) app.showTransfers = true }
    AnimatedVisibility(app.showTransfers && items.isNotEmpty(), modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val active = items.count { it.active }
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.padding(20.dp).width(380.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape),
        ) {
            Row(Modifier.fillMaxWidth().background(Nd.Inverse).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (active > 0) "$active transfer${if (active == 1) "" else "s"} in progress" else "Transfers complete",
                    color = Nd.OnInverse, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (active == 0) Text("Clear", color = Nd.OnInverse.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 12.dp).bounceClick { app.transfers.clearFinished(); app.showTransfers = false })
                Icon(Lucide.X, "Close", tint = Nd.OnInverse, modifier = Modifier.size(18.dp).bounceClick { app.showTransfers = false })
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(items.reversed(), key = { it.id }) { TransferRow(app, it) }
            }
        }
    }
}


@Composable
private fun TransferRow(app: AppState, t: Transfer) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val (icon, tint) = when (t.state) {
            TransferState.Done -> Lucide.CircleCheck to Nd.Success
            TransferState.Failed, TransferState.Cancelled -> Lucide.TriangleAlert to Nd.Danger
            else -> (if (t.kind == TransferKind.Upload) Lucide.ArrowUpFromLine else Lucide.ArrowDownToLine) to Nd.Iris
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (t.state) {
                TransferState.Running, TransferState.Queued -> {
                    LinearProgressIndicator({ t.progress }, Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist)
                    Text(
                        when {
                            t.state == TransferState.Queued -> "Waiting…"
                            t.size > 0 -> "${formatBytes((t.size * t.progress).toLong())} of ${formatBytes(t.size)}"
                            else -> "Working…"
                        },
                        style = MaterialTheme.typography.labelSmall, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TransferState.Failed -> Text(t.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = Nd.Danger, maxLines = 2)
                TransferState.Cancelled -> Text("Cancelled", style = MaterialTheme.typography.bodySmall, color = Nd.Subtle)
                TransferState.Done -> Text(if (t.kind == TransferKind.Download) "Saved · click to show" else formatBytes(t.size), style = MaterialTheme.typography.bodySmall, color = Nd.Muted,
                    modifier = Modifier.bounceClick(enabled = t.localPath != null) { t.localPath?.let { com.nudge.drive.desktop.data.Platform.reveal(File(it)) } })
            }
        }
        if (t.active) Icon(Lucide.X, "Cancel", tint = Nd.Subtle, modifier = Modifier.padding(start = 8.dp).size(16.dp).bounceClick { app.transfers.cancel(t.id) })
    }
}

// ---------------------------------------------------------------------------------------- overlays

@Composable
private fun DropOverlay() {
    Box(Modifier.fillMaxSize().background(Nd.Primary.copy(alpha = 0.12f)).padding(18.dp)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).border(3.dp, Nd.Primary, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Nd.Surface).padding(horizontal = 36.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Lucide.CloudUpload, null, tint = Nd.Primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("Drop to upload", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Nd.Ink)
                Text("Files and folders go into the folder you're in", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
            }
        }
    }
}

@Composable
private fun Toasts(app: AppState, modifier: Modifier) {
    var msg by remember { mutableStateOf<com.nudge.drive.desktop.ToastMsg?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        app.toasts.collectLatest {
            msg = it; visible = true
            // Toasts with an action (Undo) stay a little longer so there's time to click.
            delay(if (it.action != null) 6000 else 3200); visible = false
        }
    }
    AnimatedVisibility(visible, modifier.padding(bottom = 26.dp), enter = slideInVertically { it } + fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp)).background(Nd.Inverse)
                .padding(start = 20.dp, end = if (msg?.actionLabel != null) 8.dp else 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(msg?.text.orEmpty(), color = Nd.OnInverse, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp).weight(1f, fill = false))
            msg?.actionLabel?.let { label ->
                Spacer(Modifier.width(14.dp))
                Text(
                    label, color = Nd.Iris, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Nd.OnInverse.copy(alpha = 0.1f))
                        .bounceClick { msg?.action?.invoke(); visible = false }.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Files that arrived from Explorer's "Send to" (or a drop before sign-in): choose where they go. */
@Composable
private fun IncomingFilesDialog(app: AppState) {
    if (app.incoming.isEmpty()) return
    val files = app.incoming.toList()
    FolderChooserDialog(
        app,
        title = "Upload ${files.size} item${if (files.size == 1) "" else "s"} to…",
        confirm = "Upload here",
        onDismiss = { app.incoming.clear() },
    ) { folderId ->
        app.incoming.clear()
        app.transfers.upload(files, folderId)
        app.showTransfers = true
    }
}
