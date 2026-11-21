package com.nudge.drive.ui.screens.common

import com.composables.icons.lucide.*
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Folder
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.FileThumb
import com.nudge.drive.ui.components.FolderRow
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.NudgeSheet
import com.nudge.drive.ui.components.SheetAction
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.FolderPalette
import com.nudge.drive.util.folderColor
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.launch

/** Top bar used by pushed screens: glass back button + title + optional actions. */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Lucide.ArrowLeft, "Back", onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Nd.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Creates (or reuses) a public link and opens the Android share sheet. */
suspend fun shareFileLink(c: AppContainer, context: Context, file: FileItem) {
    runCatching { c.repo.share(file.id).url }
        .onSuccess { url ->
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url); putExtra(Intent.EXTRA_SUBJECT, file.name) }
            context.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        .onFailure { c.toast((it as? ApiException)?.friendly ?: "Couldn't create a share link") }
}

/** Holds which file/folder dialog is open. */
class ItemDialogs {
    var fileOptions by mutableStateOf<FileItem?>(null)
    var renameFile by mutableStateOf<FileItem?>(null)
    var moveFile by mutableStateOf<FileItem?>(null)
    var trashFile by mutableStateOf<FileItem?>(null)
    var folderOptions by mutableStateOf<Folder?>(null)
    var renameFolder by mutableStateOf<Folder?>(null)
    var moveFolder by mutableStateOf<Folder?>(null)
    var deleteFolder by mutableStateOf<Folder?>(null)
    var recolorFolder by mutableStateOf<Folder?>(null)
}

/** All file + folder sheets/dialogs, wired to the given callbacks. */
@Composable
fun ItemDialogsHost(
    c: AppContainer,
    d: ItemDialogs,
    allFolders: List<Folder>,
    onNeedFolders: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onRenameFile: (FileItem, String) -> Unit,
    onMoveFile: (FileItem, Folder?) -> Unit,
    onTrashFile: (FileItem) -> Unit,
    onDownload: (FileItem) -> Unit,
    onOpenFolder: (Folder) -> Unit = {},
    onRenameFolder: (Folder, String) -> Unit = { _, _ -> },
    onMoveFolder: (Folder, Folder?) -> Unit = { _, _ -> },
    onRecolorFolder: (Folder, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Folder) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    d.fileOptions?.let { f ->
        NudgeSheet(onDismiss = { d.fileOptions = null }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                FileThumb(f, size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${formatBytes(f.size)} · ${f.connectedAccount?.email ?: ""}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1)
                }
            }
            SheetAction(Lucide.Info, "Details & preview") { d.fileOptions = null; onOpenFile(f) }
            SheetAction(Lucide.Share2, "Share", sub = "Send the file to any app") { d.fileOptions = null; (ctx as? android.app.Activity)?.let { c.shareOut.share(it, listOf(f)) } }
            SheetAction(Lucide.Radar, "Send to a nearby phone", sub = "Nudge to Nudge, no internet needed") { d.fileOptions = null; sendNearby(c, ctx, listOf(f)) }
            SheetAction(Lucide.Link, "Share link", sub = "Anyone with the link can view") { d.fileOptions = null; scope.launch { shareFileLink(c, ctx, f) } }
            SheetAction(Lucide.Download, "Download") { d.fileOptions = null; onDownload(f) }
            SheetAction(Lucide.PencilLine, "Rename") { d.fileOptions = null; d.renameFile = f }
            SheetAction(Lucide.FolderInput, "Move to folder") { d.fileOptions = null; onNeedFolders(); d.moveFile = f }
            SheetAction(Lucide.Trash2, "Move to trash", tint = Nd.Danger) { d.fileOptions = null; d.trashFile = f }
        }
    }
    d.renameFile?.let { f ->
        TextInputDialog("Rename file", initial = f.name, onDismiss = { d.renameFile = null }) { onRenameFile(f, it); d.renameFile = null }
    }
    d.moveFile?.let { f ->
        FolderPickerSheet("Move “${f.name}”", allFolders, currentId = f.folderId, onDismiss = { d.moveFile = null }) { onMoveFile(f, it); d.moveFile = null }
    }
    d.trashFile?.let { f ->
        ConfirmDialog("Move to trash?", "“${f.name}” will go to the trash. You can restore it from there.", "Move to trash",
            onDismiss = { d.trashFile = null }) { onTrashFile(f); d.trashFile = null }
    }

    d.folderOptions?.let { f ->
        NudgeSheet(onDismiss = { d.folderOptions = null }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(folderColor(f.color).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Folder, null, tint = folderColor(f.color))
                }
                Spacer(Modifier.width(12.dp))
                Text(f.name, style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
            }
            SheetAction(Lucide.Folder, "Open") { d.folderOptions = null; onOpenFolder(f) }
            SheetAction(Lucide.PencilLine, "Rename") { d.folderOptions = null; d.renameFolder = f }
            SheetAction(Lucide.Palette, "Change colour") { d.folderOptions = null; d.recolorFolder = f }
            SheetAction(Lucide.FolderInput, "Move folder") { d.folderOptions = null; onNeedFolders(); d.moveFolder = f }
            SheetAction(Lucide.Trash2, "Delete folder", tint = Nd.Danger, sub = "Files inside go to the trash") { d.folderOptions = null; d.deleteFolder = f }
        }
    }
    d.renameFolder?.let { f -> TextInputDialog("Rename folder", initial = f.name, onDismiss = { d.renameFolder = null }) { onRenameFolder(f, it); d.renameFolder = null } }
    d.moveFolder?.let { f ->
        FolderPickerSheet("Move “${f.name}”", allFolders.filter { it.id != f.id }, currentId = f.parentId, onDismiss = { d.moveFolder = null }) { onMoveFolder(f, it); d.moveFolder = null }
    }
    d.recolorFolder?.let { f ->
        NudgeSheet(onDismiss = { d.recolorFolder = null }) {
            Text("Folder colour", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
            Spacer(Modifier.height(16.dp))
            ColorPicker(f.color) { onRecolorFolder(f, it); d.recolorFolder = null }
        }
    }
    d.deleteFolder?.let { f ->
        ConfirmDialog(
            "Delete “${f.name}”?",
            "The folder and its sub-folders are removed. Files inside move to the trash and are deleted from Google Drive, so restoring them may not bring the content back.",
            "Delete", onDismiss = { d.deleteFolder = null },
        ) { onDeleteFolder(f); d.deleteFolder = null }
    }
}

@Composable
fun ColorPicker(selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        FolderPalette.forEach { hex ->
            val on = hex.equals(selected, ignoreCase = true)
            val ring by animateColorAsState(if (on) Nd.Ink else Color.Transparent, label = "ring")
            Box(
                Modifier.size(38.dp).clip(CircleShape).border(2.dp, ring, CircleShape).padding(4.dp).clip(CircleShape)
                    .background(folderColor(hex)).bounceClick { onPick(hex) },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Lucide.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Folder tree picker: "My Files" root + every folder indented by depth. */
@Composable
fun FolderPickerSheet(title: String, folders: List<Folder>, currentId: String?, onDismiss: () -> Unit, onPick: (Folder?) -> Unit) {
    val ordered = remember(folders) { flattenTree(folders) }
    NudgeSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(if (currentId == null) Nd.Mist else Nd.Surface)
                        .border(1.dp, Nd.CardBorder, RoundedCornerShape(18.dp)).bounceClick { onPick(null) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) { Icon(Lucide.House, null, tint = Nd.IrisDeep, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text("My Files (top level)", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                }
            }
            items(ordered, key = { it.first.id }) { (f, depth) ->
                FolderRow(f, Modifier.padding(start = (depth * 18).dp), trailing = {
                    if (f.id == currentId) Text("Current", style = MaterialTheme.typography.labelSmall, color = Nd.Muted, modifier = Modifier.padding(end = 6.dp))
                }) { onPick(f) }
            }
        }
    }
}

private fun flattenTree(all: List<Folder>): List<Pair<Folder, Int>> {
    val byParent = all.groupBy { it.parentId }
    val ids = all.map { it.id }.toSet()
    val out = mutableListOf<Pair<Folder, Int>>()
    fun walk(parent: String?, depth: Int) {
        byParent[parent].orEmpty().sortedBy { it.name.lowercase() }.forEach { out += it to depth; if (depth < 6) walk(it.id, depth + 1) }
    }
    walk(null, 0)
    all.filter { it.parentId != null && it.parentId !in ids && out.none { o -> o.first.id == it.id } }.forEach { out += it to 0 }
    return out
}

/**
 * Queues drive files for Nudge Nearby: downloads them to the cache (progress in the share dialog),
 * then opens the Nearby screen which starts looking for phones.
 */
fun sendNearby(c: AppContainer, @Suppress("UNUSED_PARAMETER") context: Context, files: List<FileItem>) =
    c.shareOut.sendNearby(files) { items ->
        c.nearby.outgoing.value = items
        c.pendingRoute.value = com.nudge.drive.ui.nav.Routes.Nearby
    }
