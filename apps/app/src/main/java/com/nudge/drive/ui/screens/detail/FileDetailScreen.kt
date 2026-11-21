package com.nudge.drive.ui.screens.detail

import com.composables.icons.lucide.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nudge.drive.util.ThumbSize
import com.nudge.drive.util.ViewerType
import com.nudge.drive.util.hasThumbnail
import com.nudge.drive.util.thumbUrl
import com.nudge.drive.util.viewerType
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.data.Transfers
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.FileThumb
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.nav.shared
import com.nudge.drive.ui.screens.common.FolderPickerSheet
import com.nudge.drive.ui.screens.common.shareFileLink
import com.nudge.drive.ui.screens.more.copyText
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.FileKind
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.fullDate
import com.nudge.drive.util.kind
import com.nudge.drive.util.soft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    var file by mutableStateOf(c.fileCache[id]); private set
    var shareUrl by mutableStateOf<String?>(null); private set
    var local by mutableStateOf<File?>(null); private set
    var localProgress by mutableFloatStateOf(0f); private set
    var folders by mutableStateOf<List<Folder>>(emptyList()); private set
    var gone by mutableStateOf(false); private set

    private fun msg(e: Throwable) = (e as? ApiException)?.friendly ?: e.message ?: "Something went wrong"

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { c.repo.file(id) }.onSuccess { file = it; c.fileCache[id] = it }.onFailure { if (file == null) { c.toast(msg(it)); gone = true } }
        val f = file ?: return@launch
        shareUrl = runCatching { c.repo.sharedLinks().firstOrNull { it.file.id == id }?.url }.getOrNull()
        if (f.viewerType() == ViewerType.Text && f.size < 5L * 1024 * 1024) fetchLocal()
    }

    private suspend fun fetchLocal() {
        val f = file ?: return
        runCatching { c.transfers.toCache(f) { localProgress = it } }.onSuccess { local = it }
    }

    fun openWith(ctx: android.content.Context) = viewModelScope.launch {
        val f = file ?: return@launch
        c.toast("Opening ${f.name}…")
        runCatching { local ?: c.transfers.toCache(f) { localProgress = it } }
            .onSuccess { out ->
                local = out
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, Transfers.mimeFor(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { ctx.startActivity(Intent.createChooser(intent, "Open with")) }.onFailure { c.toast("No app on this phone can open this file") }
            }
            .onFailure { c.toast(msg(it)) }
    }

    fun share(ctx: android.content.Context) = viewModelScope.launch {
        file?.let { shareFileLink(c, ctx, it) }
        shareUrl = runCatching { c.repo.sharedLinks().firstOrNull { it.file.id == id }?.url }.getOrNull()
    }
    fun unshare() = viewModelScope.launch { runCatching { c.repo.unshare(id) }.onSuccess { shareUrl = null; c.toast("Public link turned off") }.onFailure { c.toast(msg(it)) } }
    fun makePublic(ctx: android.content.Context) = viewModelScope.launch {
        runCatching { c.repo.makePublicOnDrive(id) }
            .onSuccess { url -> if (url != null) { copyText(ctx, "Drive link", url); c.toast("Google Drive link copied") } }
            .onFailure { c.toast(msg(it)) }
    }
    fun download() { file?.let { c.transfers.download(it); c.toast("Downloading ${it.name}") } }
    fun rename(name: String) = viewModelScope.launch { runCatching { c.repo.renameFile(id, name) }.onSuccess { file = it; c.fileCache[id] = it; c.toast("Renamed"); c.refreshBus.tryEmit(Unit) }.onFailure { c.toast(msg(it)) } }
    fun loadFolders() = viewModelScope.launch { folders = runCatching { c.repo.allFolders() }.getOrDefault(emptyList()) }
    fun move(target: Folder?) = viewModelScope.launch { runCatching { c.repo.moveFile(id, target?.id) }.onSuccess { file = it; c.toast("Moved to ${target?.name ?: "My Files"}"); c.refreshBus.tryEmit(Unit) }.onFailure { c.toast(msg(it)) } }
    fun invite(email: String, role: String) = viewModelScope.launch {
        runCatching { c.repo.invite(email, role, "file", id) }.onSuccess { c.toast("Invited $email as $role") }.onFailure { c.toast(msg(it)) }
    }
    fun trash(done: () -> Unit) = viewModelScope.launch {
        runCatching { c.repo.trashFile(id) }
            .onSuccess {
                c.refreshBus.tryEmit(Unit); done()
                c.toast("Moved to trash", "Undo") {
                    c.scope.launch { runCatching { c.repo.restore(listOf(id)) }.onSuccess { c.toast("Restored"); c.refreshBus.tryEmit(Unit) } }
                }
            }
            .onFailure { c.toast(msg(it)) }
    }
}

@Composable
fun FileDetailScreen(c: AppContainer, id: String, nav: NavController) {
    val vm: FileDetailViewModel = viewModel(key = id) { FileDetailViewModel(c, id) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rename by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf(false) }
    var trash by remember { mutableStateOf(false) }
    LaunchedEffect(vm.gone) { if (vm.gone) nav.popBackStack() }
    val f = vm.file

    ScreenBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 120.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Lucide.ArrowLeft, "Back") { nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Lucide.Trash2, "Move to trash", tint = Nd.Danger) { trash = true }
            }
            if (f == null) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Nd.Iris) }
                return@Column
            }
            Spacer(Modifier.height(6.dp))
            Preview(f, vm) { openViewer(nav, c, f) }
            Spacer(Modifier.height(30.dp))
            InfoCard(f, vm.shareUrl)
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item { Chip("Open with…", false, icon = Lucide.ExternalLink) { vm.openWith(ctx) } }
                if (com.nudge.drive.ui.screens.viewer.canEditInApp(f)) item {
                    Chip("Edit", false, icon = Lucide.SquarePen) { c.fileCache[f.id] = f; nav.navigate(com.nudge.drive.ui.nav.Routes.edit(f.id)) }
                }
                item { Chip("Send nearby", false, icon = Lucide.Radar) { com.nudge.drive.ui.screens.common.sendNearby(c, ctx, listOf(f)) } }
                if (vm.shareUrl == null) item { Chip("Share link", false, icon = Lucide.Link) { vm.share(ctx) } }
                item { Chip("Rename", false, icon = Lucide.PencilLine) { rename = true } }
                item { Chip("Move", false, icon = Lucide.FolderInput) { vm.loadFolders(); move = true } }
                item { Chip("Invite", false, icon = Lucide.UserPlus) { invite = true } }
                if (vm.shareUrl != null) item {
                    Chip("Copy link", false, icon = Lucide.Link) { copyText(ctx, "Link", vm.shareUrl!!); c.toast("Link copied") }
                }
                if (vm.shareUrl != null) item { Chip("Turn off link", false, icon = Lucide.Unlink) { vm.unshare() } }
                if (f.provider == "google_drive") item { Chip("Drive link", false, icon = Lucide.Globe) { vm.makePublic(ctx) } }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val canView = f?.viewerType()?.let { it != ViewerType.None } == true
            PrimaryButton(if (canView) "Open" else "Open with…", Modifier.weight(1f), icon = if (canView) Lucide.Eye else Lucide.ExternalLink) {
                if (f != null) { if (canView) openViewer(nav, c, f) else vm.openWith(ctx) }
            }
            GlassIconButton(Lucide.Share2, "Share", size = 56.dp, container = Nd.GlassStrong) { (ctx as? android.app.Activity)?.let { a -> f?.let { c.shareOut.share(a, listOf(it)) } } }
            GlassIconButton(Lucide.Download, "Download", size = 56.dp, container = Nd.GlassStrong) { vm.download() }
        }
    }

    if (f != null) {
        if (rename) TextInputDialog("Rename file", initial = f.name, onDismiss = { rename = false }) { vm.rename(it); rename = false }
        if (move) FolderPickerSheet("Move “${f.name}”", vm.folders, currentId = f.folderId, onDismiss = { move = false }) { vm.move(it); move = false }
        if (trash) ConfirmDialog("Move to trash?", "“${f.name}” will go to the trash. You can restore it later.", "Move to trash",
            onDismiss = { trash = false }) { trash = false; vm.trash { nav.popBackStack() } }
        if (invite) InviteDialog(onDismiss = { invite = false }) { email, role -> vm.invite(email, role); invite = false }
    }
}

/** Back to the viewer if we came from it, otherwise open it on top. */
private fun openViewer(nav: NavController, c: AppContainer, f: FileItem) {
    val prev = nav.previousBackStackEntry
    if (prev?.destination?.route == com.nudge.drive.ui.nav.Routes.View && prev.arguments?.getString("id") == f.id) nav.popBackStack()
    else nav.navigate(c.openFileRoute(f, c.viewerList.takeIf { l -> l.any { it.id == f.id } } ?: listOf(f)))
}

@Composable
private fun Preview(f: FileItem, vm: FileDetailViewModel, onExpand: () -> Unit) {
    val k = f.kind()
    val vt = f.viewerType()
    val shape = RoundedCornerShape(30.dp)
    val frame = Modifier.fillMaxWidth().shared("thumb-${f.id}").softShadow(shape, 26.dp, 0.2f).clip(shape).background(Nd.Surface)
    val ctx = LocalPlatformContext.current
    when {
        vt == ViewerType.Image || vt == ViewerType.Video || (vt == ViewerType.Document && f.hasThumbnail()) -> {
            var failed by remember(f.id) { mutableStateOf(false) }
            val req = remember(f.id) {
                ImageRequest.Builder(ctx).data(thumbUrl(f.id, ThumbSize.Large)).placeholderMemoryCacheKey(thumbUrl(f.id, ThumbSize.Grid)).crossfade(200).build()
            }
            Box(
                frame.aspectRatio(if (vt == ViewerType.Document) 0.9f else 1.1f).background(if (vt == ViewerType.Document) Nd.soft(k.color) else Color.Black)
                    .bounceClick(onClick = onExpand),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) BigTile(f)
                else AsyncImage(
                    req, f.name,
                    contentScale = if (vt == ViewerType.Document) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (vt == ViewerType.Document) Modifier.padding(18.dp).clip(RoundedCornerShape(12.dp)) else Modifier),
                    onState = { if (it is coil3.compose.AsyncImagePainter.State.Error) failed = true },
                )
                if (vt == ViewerType.Video) Box(Modifier.size(70.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Play, "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    when (vt) { ViewerType.Video -> "Tap to play"; ViewerType.Document -> "Tap to read"; else -> "Tap to view full screen" },
                    style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        vt == ViewerType.Text -> Box(frame.height(320.dp).background(Nd.Surface).bounceClick(onClick = onExpand).padding(18.dp)) {
            val local = vm.local
            var text by remember(local) { mutableStateOf("") }
            LaunchedEffect(local) { if (local != null) text = withContext(Dispatchers.IO) { local.bufferedReader().use { r -> CharArray(6_000).let { b -> val n = r.read(b); if (n > 0) String(b, 0, n) else "" } } } }
            if (local == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Nd.Iris)
            else Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Nd.Ink, overflow = TextOverflow.Clip)
        }
        else -> Box(frame.aspectRatio(1.4f).background(k.soft).then(if (vt != ViewerType.None) Modifier.bounceClick(onClick = onExpand) else Modifier), contentAlignment = Alignment.Center) { BigTile(f) }
    }
}

@Composable
private fun BigTile(f: FileItem) {
    Box(Modifier.fillMaxSize().background(f.kind().soft), contentAlignment = Alignment.Center) {
        FileThumb(f, size = 128.dp, corner = 32.dp, showThumbnail = false)
    }
}

@Composable
private fun InfoCard(f: FileItem, shareUrl: String?) {
    Box {
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-30).dp).padding(horizontal = 16.dp).fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Nd.GlassButton))
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).padding(horizontal = 4.dp).fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Nd.Surface), contentAlignment = Alignment.TopEnd) {
            Text("Uploaded · ${fullDate(f.createdAt).substringBefore(" ·")}", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(top = 8.dp, end = 18.dp))
        }
        val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 26.dp)
        Column(Modifier.padding(top = 14.dp).fillMaxWidth().softShadow(shape, 18.dp, 0.12f).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(20.dp)) {
            Text(f.name, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            InfoLine("File size", formatBytes(f.size).takeIf { f.size > 0 } ?: "Google Docs file")
            InfoLine("Stored in", f.connectedAccount?.email ?: "—")
            InfoLine("Folder", f.folder?.name ?: "My Files")
            f.takenAt?.let { InfoLine("Taken", fullDate(it)) }
            InfoLine("Added to drive", fullDate(f.createdAt))
            InfoLine("Type", f.mimeType.substringAfterLast('/').substringAfterLast('.').take(24))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sharing", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
                Text(if (shareUrl != null) "Public link on" else "Private", style = MaterialTheme.typography.labelSmall,
                    color = if (shareUrl != null) Nd.IrisDeep else Nd.Muted,
                    modifier = Modifier.clip(CircleShape).background(Nd.Mist).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, String) -> Unit) {
    var role by remember { mutableStateOf("viewer") }
    TextInputDialog(
        "Invite by email", placeholder = "friend@gmail.com", confirm = "Invite",
        extra = {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Can view", role == "viewer") { role = "viewer" }
                Chip("Can edit", role == "editor") { role = "editor" }
            }
        },
        onDismiss = onDismiss,
    ) { onInvite(it, role) }
}
