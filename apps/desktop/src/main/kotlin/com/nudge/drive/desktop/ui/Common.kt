package com.nudge.drive.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Unlink
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.FileSpreadsheet
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.FolderUp
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PenLine
import com.composables.icons.lucide.Presentation
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.Crumb
import com.nudge.drive.desktop.Page
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.FileThumb
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.FolderPalette
import com.nudge.drive.util.folderColor
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.fullDate
import com.nudge.drive.util.kind
import com.nudge.drive.util.thumbUrl
import com.nudge.drive.util.ThumbSize
import com.nudge.drive.util.ViewerType
import com.nudge.drive.util.viewerType
import com.nudge.drive.util.hasThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------- page chrome

@Composable
fun PageHeader(title: String, sub: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 28.dp, top = 26.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.6).sp, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.padding(top = 2.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String?, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(40.dp).clip(shape).background(if (primary) Nd.Primary else Nd.Surface)
            .border(1.dp, if (primary) Color.Transparent else Nd.CardBorder, shape).bounceClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (label == null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (primary) Color.White else Nd.Ink, modifier = Modifier.size(18.dp))
        label?.let { Spacer(Modifier.width(8.dp)); Text(it, style = MaterialTheme.typography.labelMedium, color = if (primary) Color.White else Nd.Ink) }
    }
}

@Composable
fun Breadcrumbs(app: AppState, trail: List<Crumb>) {
    Row(Modifier.padding(start = 30.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Crumb("My files", Lucide.House, trail.isEmpty()) { app.openFolder(null, emptyList()) }
        trail.forEachIndexed { i, c ->
            Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(16.dp))
            Crumb(c.name, null, i == trail.lastIndex) { app.openFolder(c.id, trail.take(i + 1)) }
        }
    }
}

@Composable
private fun Crumb(label: String, icon: ImageVector?, current: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).bounceClick(enabled = !current, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = Nd.Muted, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (current) Nd.Ink else Nd.Muted, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------- right-click menu

data class MenuEntry(val label: String, val icon: ImageVector, val danger: Boolean = false, val divider: Boolean = false, val onClick: () -> Unit)

/** Open state of one [ContextMenuBox]; [show] opens it where the pointer last pressed (e.g. on a "⋮" button). */
class ContextMenuState {
    var open by mutableStateOf(false)
    var at by mutableStateOf(DpOffset.Zero)
    fun show() { open = true }
}

@Composable
fun rememberContextMenu() = remember { ContextMenuState() }

/** Wraps content so a right click (or [ContextMenuState.show]) opens [entries] at the pointer. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContextMenuBox(entries: () -> List<MenuEntry>, modifier: Modifier = Modifier, state: ContextMenuState = rememberContextMenu(), content: @Composable () -> Unit) {
    var height by remember { mutableStateOf(0) }
    Box(modifier.onSizeChanged { height = it.height }.onPointerEvent(PointerEventType.Press) { e ->
        val p = e.changes.first().position
        // DropdownMenu offsets are measured from the anchor's bottom-left corner.
        state.at = DpOffset((p.x / density).dp, ((p.y - height) / density).dp)
        if (e.buttons.isSecondaryPressed) state.open = true
    }) {
        content()
        if (state.open) {
            DropdownMenu(true, { state.open = false }, offset = state.at, modifier = Modifier.background(Nd.Surface)) {
                entries().forEach { e ->
                    if (e.divider) HorizontalDivider(color = Nd.Line)
                    DropdownMenuItem(
                        text = { Text(e.label, color = if (e.danger) Nd.Danger else Nd.Ink, style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(e.icon, null, tint = if (e.danger) Nd.Danger else Nd.Muted, modifier = Modifier.size(18.dp)) },
                        onClick = { state.open = false; e.onClick() },
                    )
                }
            }
        }
    }
}

/** Small round "⋮" button that opens the same menu as a right click. */
@Composable
fun MoreButton(state: ContextMenuState, modifier: Modifier = Modifier, size: Int = 34, background: Color = Color.Transparent) {
    Box(
        modifier.size(size.dp).clip(androidx.compose.foundation.shape.CircleShape).background(background).bounceClick { state.show() },
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.EllipsisVertical, "More", tint = Nd.Ink, modifier = Modifier.size(18.dp)) }
}

// ---------------------------------------------------------------------------------------- file actions

/** Dialog state for file actions; render [FileActionDialogs] once per page. */
class FileActionsState(private val app: AppState) {
    var rename by mutableStateOf<FileItem?>(null)
    var move by mutableStateOf<List<FileItem>>(emptyList())
    var delete by mutableStateOf<List<FileItem>>(emptyList())
    var details by mutableStateOf<FileItem?>(null)
    /** The list the details dialog's file came from, so "Open" can step through it in the viewer. */
    var detailsList by mutableStateOf<List<FileItem>>(emptyList())
    var renameFolder by mutableStateOf<Folder?>(null)
    var recolorFolder by mutableStateOf<Folder?>(null)
    var moveFolder by mutableStateOf<Folder?>(null)
    var deleteFolder by mutableStateOf<Folder?>(null)
    var invite by mutableStateOf<FileItem?>(null)

    /** Moves files to Trash, then offers Undo. */
    fun trash(files: List<FileItem>) = app.scope.launch {
        val ids = files.map { it.id }
        runCatching { app.repo.trashFiles(ids) }
            .onSuccess {
                app.changed()
                app.toast(if (files.size == 1) "Moved “${files[0].name}” to trash" else "Moved ${files.size} to trash", "Undo") { undoTrash(ids) }
            }
            .onFailure { app.fail(it, "Couldn't delete") }
    }

    private fun undoTrash(ids: List<String>) = app.scope.launch {
        runCatching { app.repo.restore(ids) }.onSuccess { app.toast("Restored"); app.changed() }.onFailure { app.fail(it, "Couldn't restore") }
    }

    fun menu(file: FileItem, list: List<FileItem>): List<MenuEntry> = buildList {
        add(MenuEntry("Open", Lucide.FileText) { app.openFile(file, list) })
        if (canEdit(file)) add(MenuEntry("Edit", Lucide.SquarePen) { app.editorFor = file })
        add(MenuEntry("Download", Lucide.ArrowDownToLine) { app.transfers.download(listOf(file)); app.showTransfers = true })
        add(MenuEntry("Copy file", Lucide.Copy) { copyFile(file) })
        add(MenuEntry("Send to phone", Lucide.Radar) { app.sendToPhone(listOf(file)) })
        add(MenuEntry("Copy share link", Lucide.Link, divider = true) { share(file) })
        add(MenuEntry("Invite people…", Lucide.UserPlus) { invite = file })
        if (file.provider == "google_drive") add(MenuEntry("Copy Google Drive link", Lucide.Globe) { driveLink(file) })
        add(MenuEntry("Rename", Lucide.PenLine, divider = true) { rename = file })
        add(MenuEntry("Move to…", Lucide.FolderInput) { move = listOf(file) })
        add(MenuEntry("Details & preview", Lucide.Info) { detailsList = list; details = file })
        add(MenuEntry("Move to trash", Lucide.Trash2, danger = true, divider = true) { delete = listOf(file) })
    }

    fun folderMenu(folder: Folder, open: () -> Unit): List<MenuEntry> = listOf(
        MenuEntry("Open", Lucide.Folder, onClick = open),
        MenuEntry("Upload files here", Lucide.Upload) { app.pickAndUpload(folder.id) },
        MenuEntry("Rename", Lucide.PenLine, divider = true) { renameFolder = folder },
        MenuEntry("Change colour", Lucide.Palette) { recolorFolder = folder },
        MenuEntry("Move folder", Lucide.FolderInput) { moveFolder = folder },
        MenuEntry("Delete folder", Lucide.Trash2, danger = true, divider = true) { deleteFolder = folder },
    )

    fun share(file: FileItem) = app.scope.launch {
        runCatching { app.repo.share(file.id) }.onSuccess { copyText(it.url); app.toast("Link copied: anyone with it can open ${file.name}") }
            .onFailure { app.fail(it, "Couldn't create a link") }
    }

    /** The file's own Google Drive link (makes it viewable by anyone with the link on Google Drive). */
    fun driveLink(file: FileItem) = app.scope.launch {
        runCatching { app.repo.makePublicOnDrive(file.id) }
            .onSuccess { url -> if (url != null) { copyText(url); app.toast("Google Drive link copied") } else app.toast("Google Drive didn't return a link") }
            .onFailure { app.fail(it, "Couldn't get the Google Drive link") }
    }

    /** Downloads (if needed) and puts the file on the clipboard, ready to paste anywhere. */
    fun copyFile(file: FileItem) = app.scope.launch {
        app.toast("Copying ${file.name}…")
        runCatching { app.transfers.toCache(file) }
            .onSuccess { copyFiles(listOf(it)); app.toast("Copied. Paste it into a folder, chat or email.") }
            .onFailure { app.fail(it, "Couldn't copy the file") }
    }
}

fun canEdit(f: FileItem): Boolean {
    val m = f.mimeType; val ext = f.name.substringAfterLast('.', "").lowercase()
    return f.status == "active" && (m in setOf("application/vnd.google-apps.document", "application/vnd.google-apps.spreadsheet", "application/vnd.google-apps.presentation") ||
        ext in setOf("docx", "doc", "odt", "rtf", "xlsx", "xls", "ods", "csv", "pptx", "ppt", "odp", "txt"))
}

@Composable
fun rememberFileActions(app: AppState) = remember { FileActionsState(app) }

@Composable
fun FileActionDialogs(app: AppState, s: FileActionsState) {
    val scope = rememberCoroutineScope()
    s.rename?.let { f ->
        TextInputDialog("Rename", initial = f.name, confirm = "Rename", onDismiss = { s.rename = null }) { name ->
            s.rename = null
            scope.launch { runCatching { app.repo.renameFile(f.id, name) }.onSuccess { app.changed() }.onFailure { app.fail(it, "Couldn't rename") } }
        }
    }
    if (s.move.isNotEmpty()) {
        val files = s.move
        FolderChooserDialog(app, "Move ${if (files.size == 1) files[0].name else "${files.size} files"} to…", "Move here", onDismiss = { s.move = emptyList() }) { target ->
            s.move = emptyList()
            scope.launch {
                runCatching { app.repo.moveFiles(files.map { it.id }, target) }.onSuccess { app.toast("Moved"); app.changed() }.onFailure { app.fail(it, "Couldn't move") }
            }
        }
    }
    if (s.delete.isNotEmpty()) {
        val files = s.delete
        ConfirmDialog(
            "Move to trash?", if (files.size == 1) "\"${files[0].name}\" goes to Trash. You can restore it from there." else "${files.size} files go to Trash. You can restore them from there.",
            "Move to trash", onDismiss = { s.delete = emptyList() },
        ) {
            s.delete = emptyList()
            s.trash(files)
        }
    }
    s.details?.let { f -> DetailsDialog(app, s, f) { s.details = null } }
    s.recolorFolder?.let { f ->
        NudgeDialog({ s.recolorFolder = null }, width = 420) {
            Text("Folder colour", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Nd.Ink)
            Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            ColorPicker(f.color) { hex ->
                s.recolorFolder = null
                scope.launch { runCatching { app.repo.updateFolder(f.id, color = hex) }.onSuccess { app.changed() }.onFailure { app.fail(it, "Couldn't change the colour") } }
            }
        }
    }
    s.moveFolder?.let { f ->
        FolderChooserDialog(app, "Move “${f.name}”", "Move here", exclude = f.id, onDismiss = { s.moveFolder = null }) { target ->
            s.moveFolder = null
            scope.launch {
                runCatching { app.repo.updateFolder(f.id, moveTo = true to target) }.onSuccess { app.toast("Folder moved"); app.changed() }.onFailure { app.fail(it, "Couldn't move the folder") }
            }
        }
    }
    s.invite?.let { f -> com.nudge.drive.desktop.ui.pages.InviteDialog(app, f) { s.invite = null } }
    s.renameFolder?.let { f ->
        TextInputDialog("Rename folder", initial = f.name, confirm = "Rename", onDismiss = { s.renameFolder = null }) { name ->
            s.renameFolder = null
            scope.launch { runCatching { app.repo.updateFolder(f.id, name = name) }.onSuccess { app.changed() }.onFailure { app.fail(it, "Couldn't rename") } }
        }
    }
    s.deleteFolder?.let { f ->
        ConfirmDialog("Delete \"${f.name}\"?", "The folder and everything in it goes to Trash.", "Delete", onDismiss = { s.deleteFolder = null }) {
            s.deleteFolder = null
            scope.launch { runCatching { app.repo.deleteFolder(f.id) }.onSuccess { app.toast("Folder deleted"); app.changed() }.onFailure { app.fail(it, "Couldn't delete the folder") } }
        }
    }
}

/** Details & preview: a large preview, the file's facts, its sharing state and every action, like the phone's file page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsDialog(app: AppState, s: FileActionsState, f: FileItem, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var shareUrl by remember(f.id) { mutableStateOf<String?>(null) }
    suspend fun refreshShare() { shareUrl = runCatching { app.repo.sharedLinks().firstOrNull { it.file.id == f.id }?.url }.getOrNull() }
    LaunchedEffect(f.id) { refreshShare() }
    val canView = f.viewerType() != ViewerType.None
    fun open() { onDismiss(); app.openFile(f, s.detailsList.takeIf { l -> l.any { it.id == f.id } } ?: listOf(f)) }
    fun then(block: () -> Unit) { onDismiss(); block() }

    NudgeDialog(onDismiss, width = 860) {
        Row(Modifier.fillMaxWidth()) {
            DetailsPreview(app, f, Modifier.weight(1f).height(400.dp), onOpen = if (canView) ::open else null)
            Spacer(Modifier.width(22.dp))
            Column(Modifier.width(330.dp)) {
                Text(f.name, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Nd.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                listOfNotNull(
                    "File size" to (formatBytes(f.size).takeIf { f.size > 0 } ?: "Google Docs file"),
                    "Stored in" to (f.connectedAccount?.email?.ifBlank { null } ?: if (f.provider == "s3") "S3 storage" else "Google Drive"),
                    "Folder" to (f.folder?.name ?: "My files"),
                    f.takenAt?.let { "Taken" to fullDate(it) },
                    "Added to drive" to fullDate(f.createdAt),
                    "Modified" to fullDate(f.updatedAt),
                    "Type" to f.mimeType.substringAfterLast('/').substringAfterLast('.').take(24),
                ).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.width(118.dp))
                        Text(v, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sharing", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
                    Text(
                        if (shareUrl != null) "Public link on" else "Private", style = MaterialTheme.typography.labelSmall,
                        color = if (shareUrl != null) Nd.IrisDeep else Nd.Muted,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Nd.Mist).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val link = shareUrl
                    if (link == null) DetailAction("Share link", Lucide.Link) {
                        scope.launch {
                            runCatching { app.repo.share(f.id) }
                                .onSuccess { copyText(it.url); shareUrl = it.url; app.toast("Link copied: anyone with it can open ${f.name}"); refreshShare() }
                                .onFailure { app.fail(it, "Couldn't create a link") }
                        }
                    } else {
                        DetailAction("Copy link", Lucide.Copy) { copyText(link); app.toast("Link copied") }
                        DetailAction("Turn off link", Lucide.Unlink) {
                            scope.launch {
                                runCatching { app.repo.unshare(f.id) }.onSuccess { shareUrl = null; app.toast("Public link turned off") }
                                    .onFailure { app.fail(it, "Couldn't turn off the link") }
                            }
                        }
                    }
                    if (f.provider == "google_drive") DetailAction("Drive link", Lucide.Globe) { s.driveLink(f) }
                    DetailAction("Invite", Lucide.UserPlus) { then { s.invite = f } }
                    DetailAction("Send to phone", Lucide.Radar) { then { app.sendToPhone(listOf(f)) } }
                    DetailAction("Copy file", Lucide.Copy) { s.copyFile(f) }
                    DetailAction("Rename", Lucide.PenLine) { then { s.rename = f } }
                    DetailAction("Move", Lucide.FolderInput) { then { s.move = listOf(f) } }
                    DetailAction("Move to trash", Lucide.Trash2, danger = true) { then { s.delete = listOf(f) } }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(if (canView) "Open" else "Open with…", Modifier.widthIn(min = 150.dp), icon = if (canView) Lucide.Eye else Lucide.ExternalLink) {
                if (canView) open() else then { com.nudge.drive.desktop.ui.viewer.openExternally(app, f) }
            }
            if (canEdit(f)) SecondaryButton("Edit", icon = Lucide.SquarePen) { then { app.editorFor = f } }
            SecondaryButton("Download", icon = Lucide.ArrowDownToLine) { app.transfers.download(listOf(f)); app.showTransfers = true }
            Spacer(Modifier.weight(1f))
            Text("Close", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(horizontal = 12.dp).bounceClick(onClick = onDismiss))
        }
    }
}

@Composable
private fun DetailAction(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (danger) Nd.Danger else Nd.Ink
    Row(
        Modifier.height(36.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).bounceClick(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) Nd.Danger else Nd.Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Large preview: the server thumbnail for photos, videos and documents, the first lines of small text files, else a big icon. */
@Composable
private fun DetailsPreview(app: AppState, f: FileItem, modifier: Modifier, onOpen: (() -> Unit)?) {
    val vt = f.viewerType()
    val k = f.kind()
    val shape = RoundedCornerShape(22.dp)
    val frame = modifier.clip(shape).border(1.dp, Nd.CardBorder, shape).then(if (onOpen != null) Modifier.bounceClick(onClick = onOpen) else Modifier)
    val hint = when (vt) { ViewerType.Video -> "Click to play"; ViewerType.Document -> "Click to read"; ViewerType.Text -> "Click to open"; else -> "Click to view full size" }
    when {
        vt == ViewerType.Image || vt == ViewerType.Video || (vt == ViewerType.Document && f.hasThumbnail()) -> {
            var failed by remember(f.id) { mutableStateOf(false) }
            Box(frame.background(if (vt == ViewerType.Document) Nd.soft(k.color) else Color.Black), contentAlignment = Alignment.Center) {
                if (failed) BigFileTile(f)
                else AsyncImage(
                    model = thumbUrl(f.id, ThumbSize.Large), contentDescription = f.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().then(if (vt == ViewerType.Document) Modifier.padding(18.dp).clip(RoundedCornerShape(12.dp)) else Modifier),
                    onState = { if (it is AsyncImagePainter.State.Error) failed = true },
                )
                if (vt == ViewerType.Video) Box(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Play, "Play", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                PreviewHint(hint, Modifier.align(Alignment.BottomCenter))
            }
        }
        vt == ViewerType.Text && f.size < 5L * 1024 * 1024 -> {
            var text by remember(f.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(f.id) {
                text = runCatching {
                    val local = app.transfers.toCache(f)
                    withContext(Dispatchers.IO) { local.bufferedReader().use { r -> CharArray(6_000).let { b -> val n = r.read(b); if (n > 0) String(b, 0, n) else "" } } }
                }.getOrDefault("")
            }
            Box(frame.background(Nd.FieldSolid).padding(18.dp)) {
                val t = text
                if (t == null) androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = Nd.Iris, strokeWidth = 2.5.dp)
                else if (t.isBlank()) Text("This file is empty", style = MaterialTheme.typography.bodyMedium, color = Nd.Subtle, modifier = Modifier.align(Alignment.Center))
                else Text(t, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Nd.Ink, overflow = TextOverflow.Clip)
            }
        }
        else -> Box(frame.background(Nd.soft(k.color)), contentAlignment = Alignment.Center) {
            BigFileTile(f)
            if (onOpen != null) PreviewHint(hint, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BigFileTile(f: FileItem) {
    Box(Modifier.fillMaxSize().background(Nd.soft(f.kind().color)), contentAlignment = Alignment.Center) {
        FileThumb(f, size = 128.dp, corner = 32.dp, showThumbnail = false)
    }
}

@Composable
private fun PreviewHint(text: String, modifier: Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = Color.White,
        modifier = modifier.padding(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The folder colours as a row of swatches; the chosen one gets a ring and a tick. */
@Composable
fun ColorPicker(selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        FolderPalette.forEach { hex ->
            val on = hex.equals(selected, ignoreCase = true)
            val shape = androidx.compose.foundation.shape.CircleShape
            Box(
                Modifier.size(38.dp).clip(shape).border(2.dp, if (on) Nd.Ink else Color.Transparent, shape).padding(4.dp).clip(shape)
                    .background(folderColor(hex)).bounceClick { onPick(hex) },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Lucide.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Plain centred dialog card in the app's style. */
@Composable
fun NudgeDialog(onDismiss: () -> Unit, width: Int = 460, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(width.dp).clip(RoundedCornerShape(26.dp)).background(Nd.Surface).border(1.dp, Nd.CardBorder, RoundedCornerShape(26.dp)).padding(24.dp), content = content)
    }
}

/** Browse folders and pick one (null = My files). */
@Composable
fun FolderChooserDialog(app: AppState, title: String, confirm: String, exclude: String? = null, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var trail by remember { mutableStateOf(listOf<Crumb>()) }
    var folders by remember { mutableStateOf<List<Folder>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newColor by remember { mutableStateOf(FolderPalette.first()) }
    val scope = rememberCoroutineScope()
    val current = trail.lastOrNull()?.id
    // [exclude]: the folder being moved can't go inside itself.
    LaunchedEffect(current) { folders = null; folders = runCatching { app.repo.folders(current) }.getOrDefault(emptyList()).filter { it.id != exclude } }
    NudgeDialog(onDismiss, width = 500) {
        Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My files", style = MaterialTheme.typography.labelMedium, color = if (trail.isEmpty()) Nd.Ink else Nd.IrisDeep, modifier = Modifier.bounceClick { trail = emptyList() })
            trail.forEachIndexed { i, c ->
                Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(14.dp))
                Text(c.name, style = MaterialTheme.typography.labelMedium, color = if (i == trail.lastIndex) Nd.Ink else Nd.IrisDeep, maxLines = 1,
                    modifier = Modifier.bounceClick { trail = trail.take(i + 1) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)).background(Nd.FieldSolid)) {
            val list = folders
            if (list == null) androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = Nd.Iris, strokeWidth = 2.5.dp)
            else if (list.isEmpty()) Text("No folders here", style = MaterialTheme.typography.bodyMedium, color = Nd.Subtle, modifier = Modifier.align(Alignment.Center))
            else LazyColumn(contentPadding = PaddingValues(6.dp)) {
                items(list, key = { it.id }) { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick { trail = trail + Crumb(f.id, f.name) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Folder, null, tint = folderColor(f.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Lucide.ChevronRight, null, tint = Nd.Subtle, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("New folder", icon = Lucide.FolderPlus) { creating = true }
            Spacer(Modifier.weight(1f))
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(end = 16.dp).bounceClick(onClick = onDismiss))
            PrimaryButton(confirm, Modifier.widthIn(min = 140.dp)) { onPick(current) }
        }
    }
    if (creating) TextInputDialog(
        "New folder", placeholder = "Folder name", confirm = "Create",
        extra = { Spacer(Modifier.height(16.dp)); ColorPicker(newColor) { newColor = it } },
        onDismiss = { creating = false },
    ) { name ->
        creating = false
        scope.launch {
            runCatching { app.repo.createFolder(name, current, newColor) }.onSuccess { trail = trail + Crumb(it.id, it.name); app.changed() }.onFailure { app.fail(it, "Couldn't create the folder") }
        }
    }
}

// ---------------------------------------------------------------------------------------- "New" menu

@Composable
private fun MenuRow(label: String, icon: ImageVector, tint: Color, onDismiss: () -> Unit, action: () -> Unit) {
    DropdownMenuItem({ Text(label, color = Nd.Ink, style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = { onDismiss(); action() })
}

@Composable
fun NewMenu(app: AppState, open: Boolean, onDismiss: () -> Unit) {
    var newFolder by remember { mutableStateOf(false) }
    var newDoc by remember { mutableStateOf<Pair<String, String>?>(null) }
    var newFolderColor by remember { mutableStateOf(FolderPalette.first()) }
    val scope = rememberCoroutineScope()
    DropdownMenu(open, onDismiss, modifier = Modifier.background(Nd.Surface).widthIn(min = 220.dp)) {
        MenuRow("New folder", Lucide.FolderPlus, Nd.Muted, onDismiss) { newFolder = true }
        HorizontalDivider(color = Nd.Line)
        MenuRow("Upload files", Lucide.Upload, Nd.Muted, onDismiss) { app.pickAndUpload() }
        MenuRow("Upload a folder", Lucide.FolderUp, Nd.Muted, onDismiss) { app.pickFolderAndUpload() }
        HorizontalDivider(color = Nd.Line)
        MenuRow("Document", Lucide.FileText, Nd.Doc, onDismiss) { newDoc = "document" to "Untitled document" }
        MenuRow("Spreadsheet", Lucide.FileSpreadsheet, Nd.Image, onDismiss) { newDoc = "spreadsheet" to "Untitled spreadsheet" }
        MenuRow("Presentation", Lucide.Presentation, Nd.Video, onDismiss) { newDoc = "presentation" to "Untitled presentation" }
    }
    if (newFolder) TextInputDialog(
        "New folder", placeholder = "Folder name", confirm = "Create",
        extra = { Spacer(Modifier.height(16.dp)); ColorPicker(newFolderColor) { newFolderColor = it } },
        onDismiss = { newFolder = false },
    ) { name ->
        newFolder = false
        scope.launch {
            runCatching { app.repo.createFolder(name, app.currentFolderId, newFolderColor) }.onSuccess { app.changed(); app.toast("Folder created") }
                .onFailure { app.fail(it, "Couldn't create the folder") }
        }
    }
    newDoc?.let { (kind, default) ->
        TextInputDialog("New ${kind}", initial = default, confirm = "Create", onDismiss = { newDoc = null }) { name ->
            newDoc = null
            scope.launch {
                runCatching { app.repo.createDocument(kind, name, app.currentFolderId) }
                    .onSuccess { app.changed(); app.editorFor = it.file }
                    .onFailure { app.fail(it, "Couldn't create the document") }
            }
        }
    }
}
