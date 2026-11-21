package com.nudge.drive.ui.screens.main

import com.composables.icons.lucide.*
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nudge.drive.AppContainer
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.data.Transfer
import com.nudge.drive.data.TransferKind
import com.nudge.drive.data.TransferState
import com.nudge.drive.ui.components.GlassBottomBar
import com.nudge.drive.ui.components.NavItem
import com.nudge.drive.ui.components.NudgeSheet
import com.nudge.drive.ui.components.SheetAction
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.ui.screens.common.ColorPicker
import com.nudge.drive.ui.screens.drives.DrivesScreen
import com.nudge.drive.ui.screens.drives.DrivesViewModel
import com.nudge.drive.ui.screens.files.FilesScreen
import com.nudge.drive.ui.screens.files.FilesViewModel
import com.nudge.drive.ui.screens.home.HomeScreen
import com.nudge.drive.ui.screens.home.HomeViewModel
import com.nudge.drive.ui.screens.profile.ProfileScreen
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.FolderPalette
import com.nudge.drive.util.formatBytes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Navigation callbacks shared by all tabs. */
class MainActions(
    /** Opens a file in the viewer (photos/videos swipe through [siblings]). */
    val openFile: (FileItem, List<FileItem>) -> Unit,
    val openDetails: (FileItem) -> Unit,
    val openFolder: (Folder) -> Unit,
    val upload: (folderId: String?) -> Unit,
    val newFolder: () -> Unit,
    val navigate: (String) -> Unit,
    val goTab: (String) -> Unit,
    val search: (String) -> Unit,
)

private val tabs = listOf(
    NavItem("home", "Home", Lucide.House, Lucide.House),
    NavItem("files", "Files", Lucide.FileText, Lucide.FileText),
    NavItem("drives", "Drives", Lucide.Layers, Lucide.Layers),
    NavItem("profile", "Profile", Lucide.UserRound, Lucide.UserRound),
)

@Composable
fun MainScreen(c: AppContainer, nav: NavController) {
    val haze = remember { HazeState() }
    var tab by rememberSaveable { mutableStateOf("home") }
    val filesVm: FilesViewModel = viewModel { FilesViewModel(c) }
    val homeVm: HomeViewModel = viewModel { HomeViewModel(c) }
    val drivesVm: DrivesViewModel = viewModel { DrivesViewModel(c) }

    var uploadSheet by remember { mutableStateOf(false) }
    var uploadTarget by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var transfersOpen by remember { mutableStateOf(false) }
    var newDoc by remember { mutableStateOf<NewDocKind?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var newFolderColor by remember { mutableStateOf(FolderPalette.first()) }

    val pickDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { c.transfers.upload(uris, uploadTarget); transfersOpen = false }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) c.transfers.upload(uris, uploadTarget)
    }

    val actions = remember(nav) {
        MainActions(
            openFile = { f, siblings -> nav.navigate(c.openFileRoute(f, siblings)) },
            openDetails = { c.fileCache[it.id] = it; nav.navigate(Routes.file(it.id)) },
            openFolder = { f -> tab = "files"; filesVm.openPath(listOf(f)) },
            upload = { uploadTarget = it; uploadSheet = true },
            newFolder = { newFolder = true },
            navigate = { nav.navigate(it) },
            goTab = { tab = it },
            search = { q -> filesVm.query = q; filesVm.load(); tab = "files" },
        )
    }

    // Notification taps: switch tab or open the transfers sheet.
    val wantTab by c.pendingTab.collectAsState()
    LaunchedEffect(wantTab) {
        wantTab?.let { t -> if (t == "transfers") transfersOpen = true else tab = t; c.pendingTab.value = null }
    }
    // "All folders" picked a folder: show it in the Files tab with its full path.
    val trail by c.openFolderTrail.collectAsState()
    LaunchedEffect(trail) { trail?.let { t -> tab = "files"; filesVm.openPath(t); c.openFolderTrail.value = null } }

    BackHandler(enabled = tab != "home") { tab = "home" }

    // Ask once for notification permission (Android 13+), after the user is signed in.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        c.prefs.setSystemNotifications(ok)
        if (ok) c.toast("Notifications on. We'll tell you when uploads finish.")
    }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !c.prefs.askedNotifications.value) {
            kotlinx.coroutines.delay(1200)
            c.prefs.markAskedNotifications()
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze).background(Nd.Background)) {
            AnimatedContent(
                tab,
                transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith fadeOut(tween(140)) },
                label = "tab",
            ) { t ->
                when (t) {
                    "home" -> HomeScreen(c, homeVm, actions)
                    "files" -> FilesScreen(c, filesVm, actions)
                    "drives" -> DrivesScreen(c, drivesVm)
                    else -> ProfileScreen(c, actions)
                }
            }
        }
        // Keeps the clock and icons readable when lists scroll under the status bar.
        val scrim = if (Nd.isDark) androidx.compose.ui.graphics.Color(0xFF16121F) else androidx.compose.ui.graphics.Color(0xFFD9CEFF)
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(scrim.copy(alpha = 0.96f), scrim.copy(alpha = 0.8f)))),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TransfersPill(c) { transfersOpen = true }
            GlassBottomBar(tabs, tab, haze, onSelect = { tab = it })
        }
    }

    if (uploadSheet) {
        NudgeSheet(onDismiss = { uploadSheet = false }) {
            Text("Add to ${if (uploadTarget == null) "My Files" else "this folder"}", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
            Text("Files go to whichever drive your routing rule picks.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            SheetAction(Lucide.Images, "Photos & videos", sub = "Pick from your gallery") {
                uploadSheet = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            SheetAction(Lucide.CloudUpload, "Any file", sub = "Documents, PDFs, zips…") { uploadSheet = false; pickDocs.launch(arrayOf("*/*")) }
            SheetAction(Lucide.FolderPlus, "New folder") { uploadSheet = false; newFolder = true }
            Text("Create new", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NewDocKinds.forEach { k -> NewDocTile(k, Modifier.weight(1f)) { uploadSheet = false; newDoc = k } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    newDoc?.let { k ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        TextInputDialog("New ${k.label}", initial = "Untitled ${k.label.lowercase()}", confirm = "Create", onDismiss = { newDoc = null }) { name ->
            newDoc = null
            val target = uploadTarget
            c.scope.launch {
                runCatching { c.repo.createDocument(k.kind, name, target) }
                    .onSuccess { r ->
                        c.toast("Created “${r.file.name}”"); c.refreshBus.tryEmit(Unit); c.fileCache[r.file.id] = r.file
                        // Straight into the in-app editor.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.navigate(com.nudge.drive.ui.nav.Routes.edit(r.file.id)) }
                    }
                    .onFailure { c.toast((it as? com.nudge.drive.data.ApiException)?.friendly ?: "Couldn't create the file") }
            }
        }
    }
    if (newFolder) {
        TextInputDialog(
            "New folder", placeholder = "Folder name", confirm = "Create",
            extra = { Spacer(Modifier.height(16.dp)); ColorPicker(newFolderColor) { newFolderColor = it } },
            onDismiss = { newFolder = false },
        ) { name ->
            newFolder = false
            if (tab == "files") filesVm.createFolder(name, newFolderColor)
            else homeVm.createFolder(name, newFolderColor)
        }
    }
    if (transfersOpen) TransfersSheet(c) { transfersOpen = false }
}

/** Floating glass pill above the nav bar while anything is uploading/downloading. */
@Composable
private fun TransfersPill(c: AppContainer, onClick: () -> Unit) {
    val items by c.transfers.items.collectAsState()
    val active = items.filter { it.state == TransferState.Running || it.state == TransferState.Queued }
    AnimatedVisibility(active.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val shape = RoundedCornerShape(24.dp)
        val progress = if (active.isEmpty()) 0f else active.map { it.progress }.average().toFloat()
        val uploads = active.count { it.kind == TransferKind.Upload }
        Row(
            Modifier.padding(bottom = 10.dp).softShadow(shape, 16.dp, 0.18f).clip(shape).background(Nd.GlassStrong)
                .border(1.dp, Nd.GlassBorder, shape).bounceClick(onClick = onClick).padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator({ progress }, Modifier.size(32.dp), color = Nd.Iris, trackColor = Nd.Mist, strokeWidth = 3.dp, strokeCap = StrokeCap.Round)
                Icon(if (uploads > 0) Lucide.CloudUpload else Lucide.CloudDownload, null, tint = Nd.IrisDeep, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                (if (uploads > 0) "Uploading" else "Downloading") + " ${active.size} file${if (active.size > 1) "s" else ""} · ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium, color = Nd.Ink,
            )
        }
    }
}

@Composable
private fun TransfersSheet(c: AppContainer, onDismiss: () -> Unit) {
    val items by c.transfers.items.collectAsState()
    NudgeSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transfers", style = MaterialTheme.typography.titleLarge, color = Nd.Ink, modifier = Modifier.weight(1f))
            if (items.any { it.state == TransferState.Done || it.state == TransferState.Failed })
                Text("Clear finished", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick { c.transfers.clearFinished() }.padding(6.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) Text("Nothing transferring right now.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items.reversed(), key = { it.id }) { t -> TransferRow(t, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun TransferRow(t: Transfer, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val (icon, tint) = when (t.state) {
            TransferState.Done -> Lucide.CircleCheck to Nd.Success
            TransferState.Failed -> Lucide.CircleAlert to Nd.Danger
            else -> (if (t.kind == TransferKind.Upload) Lucide.CloudUpload else Lucide.CloudDownload) to Nd.Iris
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (t.state) {
                TransferState.Failed -> Text(t.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = Nd.Danger, maxLines = 2)
                TransferState.Done -> Text(if (t.kind == TransferKind.Upload) "Uploaded" else "Saved to Downloads/Nudge Drive", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                else -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator({ t.progress }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Nd.Iris, trackColor = Nd.Mist, strokeCap = StrokeCap.Round)
                    Text(if (t.size > 0) "${formatBytes((t.size * t.progress).toLong())} of ${formatBytes(t.size)}" else "Working…",
                        style = MaterialTheme.typography.labelSmall, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Google file types the app can create. */
class NewDocKind(val kind: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: androidx.compose.ui.graphics.Color)

private val NewDocKinds = listOf(
    NewDocKind("document", "Document", Lucide.FileText, androidx.compose.ui.graphics.Color(0xFF2E64E0)),
    NewDocKind("spreadsheet", "Sheet", Lucide.Sheet, androidx.compose.ui.graphics.Color(0xFF1E9E57)),
    NewDocKind("presentation", "Slides", Lucide.Presentation, androidx.compose.ui.graphics.Color(0xFFF59E0B)),
)

@Composable
private fun NewDocTile(k: NewDocKind, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).bounceClick(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Nd.soft(k.color)), contentAlignment = Alignment.Center) {
            Icon(k.icon, null, tint = k.color, modifier = Modifier.size(22.dp))
        }
        Text(k.label, style = MaterialTheme.typography.labelSmall, color = Nd.Ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}
