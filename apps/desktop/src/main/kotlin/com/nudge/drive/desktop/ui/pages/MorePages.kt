package com.nudge.drive.desktop.ui.pages

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.History
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Link2Off
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.PencilLine
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.X
import com.nudge.drive.data.ApiKey
import com.nudge.drive.data.AppJson
import com.nudge.drive.data.AuditLog
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Invite
import com.nudge.drive.data.ShareLink
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.ui.NudgeDialog
import com.nudge.drive.desktop.ui.PageHeader
import com.nudge.drive.desktop.ui.ToolButton
import com.nudge.drive.desktop.ui.copyText
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.FileRow
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.friendlyDate
import com.nudge.drive.util.fullDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ======================================================================================== Shared (links + invites)

@Composable
fun SharedPage(app: AppState) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var links by remember { mutableStateOf<Load<List<ShareLink>>>(Load.Loading) }
    var sent by remember { mutableStateOf<List<Invite>>(emptyList()) }
    var received by remember { mutableStateOf<List<Invite>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        links = runCatching { app.repo.sharedLinks() }.fold({ Load.Ready(it) }, { Load.Failed("Couldn't load your links.") })
        runCatching { app.repo.invites() }.onSuccess { sent = it.sent; received = it.received }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Shared", "Public links and the people you've invited") { ToolButton(Lucide.RefreshCw, null) { reload++ } }
        Row(Modifier.padding(start = 30.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Links ${(links as? Load.Ready)?.data?.size ?: ""}".trim(), tab == 0) { tab = 0 }
            Chip("Invites sent ${sent.size}", tab == 1) { tab = 1 }
            Chip("Shared with me ${received.size}", tab == 2) { tab = 2 }
        }
        when (tab) {
            0 -> when (val s = links) {
                Load.Loading -> LoadingGrid()
                is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
                is Load.Ready -> if (s.data.isEmpty()) EmptyState(Lucide.Link, "No shared links", "Right-click a file and choose Copy share link.", Modifier.fillMaxWidth().padding(60.dp))
                else LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp)) {
                    items(s.data, key = { it.id }) { link ->
                        FileRow(link.file, subtitle = "Shared ${friendlyDate(link.createdAt)}", onClick = { app.openFile(link.file, listOf(link.file)) }, onMore = null)
                        Row(Modifier.padding(start = 70.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToolButton(Lucide.Copy, "Copy link") { link.url?.let { copyText(it); app.toast("Link copied") } }
                            ToolButton(Lucide.Link2Off, "Stop sharing") {
                                scope.launch { runCatching { app.repo.unshare(link.file.id) }.onSuccess { reload++; app.toast("Link turned off") }.onFailure { app.fail(it, "Couldn't stop sharing") } }
                            }
                            Text(link.url.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Nd.Subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            1 -> if (sent.isEmpty()) EmptyState(Lucide.Mail, "No invites sent", "Right-click a file and choose Invite people.", Modifier.fillMaxWidth().padding(60.dp))
            else LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sent, key = { it.id }) { inv ->
                    InviteRow(inv) {
                        scope.launch { runCatching { app.repo.revokeInvite(inv.id) }.onSuccess { app.toast("Invite revoked"); reload++ }.onFailure { app.fail(it, "Couldn't revoke") } }
                    }
                }
            }
            else -> if (received.isEmpty()) EmptyState(Lucide.Mail, "Nothing shared with you", "When someone invites your email to a file, it shows up here.", Modifier.fillMaxWidth().padding(60.dp))
            else LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(received, key = { it.id }) { inv -> InviteRow(inv, onRevoke = null) }
            }
        }
    }
}

@Composable
private fun InviteRow(inv: Invite, onRevoke: (() -> Unit)?) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Nd.Mist), contentAlignment = Alignment.Center) {
            Text(inv.email.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Nd.IrisDeep)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(inv.user?.name ?: inv.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1)
            Text("${if (inv.role == "editor") "Can edit" else "Can view"} · ${inv.target?.name ?: "deleted item"} · ${friendlyDate(inv.createdAt)}",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val ok = inv.status == "accepted"
        Text(inv.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = if (ok) Nd.Success else Nd.Audio,
            modifier = Modifier.clip(CircleShape).background(if (ok) Nd.SuccessSoft else Nd.soft(Nd.Audio)).padding(horizontal = 10.dp, vertical = 5.dp))
        if (onRevoke != null) { Spacer(Modifier.width(10.dp)); ToolButton(Lucide.X, "Revoke", onClick = onRevoke) }
    }
}

/** Invite someone by email to view or edit a file. */
@Composable
fun InviteDialog(app: AppState, file: FileItem, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("viewer") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    NudgeDialog(onDismiss, width = 460) {
        Text("Invite people", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Nd.Ink)
        Text(file.name, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        PillField(email, { email = it; error = null }, "Their email, e.g. friend@gmail.com")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Can view", role == "viewer") { role = "viewer" }
            Chip("Can edit", role == "editor") { role = "editor" }
        }
        com.nudge.drive.desktop.ui.ErrorLine(error)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(end = 16.dp).bounceClick(onClick = onDismiss))
            PrimaryButton("Send invite", Modifier.widthIn(min = 150.dp), icon = Lucide.UserPlus, loading = busy) {
                val e = email.trim()
                if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(e)) { error = "Enter a valid email address."; return@PrimaryButton }
                busy = true
                scope.launch {
                    runCatching { app.repo.invite(e, role, "file", file.id) }
                        .onSuccess { app.toast("Invited $e (${if (role == "editor") "can edit" else "can view"})"); onDismiss() }
                        .onFailure { error = (it as? com.nudge.drive.data.ApiException)?.friendly ?: it.message ?: "Couldn't send the invite"; busy = false }
                }
            }
        }
    }
}

// ======================================================================================== Activity

private fun AuditLog.meta(): JsonObject? = when (val m = metadata) {
    is JsonPrimitive -> m.contentOrNull?.let { runCatching { AppJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    is JsonObject -> m
    else -> null
}

private fun AuditLog.describe(): Pair<String, ImageVector> {
    val m = meta()
    val name = (m?.get("name") as? JsonPrimitive)?.contentOrNull ?: "an item"
    return when (action) {
        "UPLOAD_FILE" -> "Uploaded $name" to Lucide.CloudUpload
        "TRASH_FILE" -> "Moved $name to trash" to Lucide.Trash2
        "RESTORE_FILE" -> "Restored $name" to Lucide.ArchiveRestore
        "PERMANENT_DELETE_FILE" -> "Deleted $name forever" to Lucide.Trash
        "UPDATE_FILE" -> "Updated $name" to Lucide.PencilLine
        "MOVE_FILES" -> "Moved ${(m?.get("count") as? JsonPrimitive)?.contentOrNull ?: "some"} files" to Lucide.FolderInput
        "CREATE_FOLDER" -> "Created folder $name" to Lucide.FolderPlus
        "UPDATE_FOLDER" -> "Updated folder $name" to Lucide.PencilLine
        "DELETE_FOLDER" -> "Deleted folder $name" to Lucide.FolderX
        else -> action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } to Lucide.History
    }
}

@Composable
fun ActivityPage(app: AppState) {
    var state by remember { mutableStateOf<Load<List<AuditLog>>>(Load.Loading) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { state = runCatching { app.repo.activity() }.fold({ Load.Ready(it) }, { Load.Failed("Couldn't load your activity.") }) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Activity", "Everything that happened in your drive, newest first") { ToolButton(Lucide.RefreshCw, null) { reload++ } }
        when (val s = state) {
            Load.Loading -> LoadingGrid()
            is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
            is Load.Ready -> if (s.data.isEmpty()) EmptyState(Lucide.History, "No activity yet", "Uploads, moves and deletes will show up here.", Modifier.fillMaxWidth().padding(60.dp))
            else LazyColumn(contentPadding = PaddingValues(start = 32.dp, end = 28.dp, bottom = 60.dp)) {
                items(s.data, key = { it.id }) { log ->
                    val (text, icon) = log.describe()
                    Row(Modifier.fillMaxWidth().widthIn(max = 900.dp), verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Nd.Surface).border(1.dp, Nd.Line, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = Nd.Iris, modifier = Modifier.size(19.dp))
                            }
                            Box(Modifier.width(2.dp).height(22.dp).background(Nd.Lilac))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.padding(top = 3.dp)) {
                            Text(text, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(fullDate(log.createdAt), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================== API keys

@Composable
fun ApiKeysPage(app: AppState) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<ApiKey>>>(Load.Loading) }
    var reload by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf<String?>(null) }
    var revoke by remember { mutableStateOf<ApiKey?>(null) }
    LaunchedEffect(reload) { state = runCatching { app.repo.apiKeys() }.fold({ Load.Ready(it) }, { Load.Failed("Couldn't load your API keys.") }) }
    val endpoint = app.repo.publicUploadEndpoint
    Column(Modifier.fillMaxSize()) {
        PageHeader("API keys", "Upload from scripts, CI jobs and other apps") {
            ToolButton(Lucide.Plus, "New key", primary = true) { creating = true }
        }
        val shape = RoundedCornerShape(20.dp)
        Column(Modifier.padding(horizontal = 28.dp).widthIn(max = 900.dp).fillMaxWidth().clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(18.dp)) {
            Text("Upload endpoint", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nd.Mist).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) { Text("POST $endpoint", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Nd.IrisDeep) }
                Icon(Lucide.Copy, "Copy", tint = Nd.Muted, modifier = Modifier.size(16.dp).bounceClick { copyText(endpoint); app.toast("Endpoint copied") })
            }
            Text("Send multipart form data with the header  Authorization: Bearer <key>. Files are placed on your drives like normal uploads.",
                style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        Spacer(Modifier.height(14.dp))
        when (val s = state) {
            Load.Loading -> LoadingGrid()
            is Load.Failed -> EmptyState(Lucide.RefreshCw, "Couldn't load", s.message, Modifier.fillMaxWidth().padding(60.dp)) { SecondaryButton("Try again") { reload++ } }
            is Load.Ready -> if (s.data.isEmpty()) EmptyState(Lucide.KeyRound, "No API keys", "Create a key to upload from scripts, CI or other apps.", Modifier.fillMaxWidth().padding(40.dp))
            else LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.data, key = { it.id }) { k ->
                    val active = k.status == "active"
                    Row(Modifier.widthIn(max = 900.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nd.Surface).border(1.dp, Nd.CardBorder, RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.KeyRound, null, tint = if (active) Nd.Iris else Nd.Subtle)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(k.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                            Text("${k.keyPrefix}… · created ${friendlyDate(k.createdAt)} · " + if (active) "last used ${k.lastUsedAt?.let(::friendlyDate) ?: "never"}" else "revoked",
                                style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1)
                        }
                        if (active) ToolButton(Lucide.Trash2, "Revoke") { revoke = k }
                    }
                }
            }
        }
    }
    if (creating) TextInputDialog("New API key", placeholder = "Key name (e.g. Laptop backup script)", confirm = "Create", onDismiss = { creating = false }) { name ->
        creating = false
        scope.launch { runCatching { app.repo.createApiKey(name) }.onSuccess { secret = it.secret; reload++ }.onFailure { app.fail(it, "Couldn't create the key") } }
    }
    secret?.let { s ->
        NudgeDialog({ secret = null }, width = 520) {
            Text("Copy your key now", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Nd.Ink)
            Text("This is the only time it's shown. Store it somewhere safe.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Text(s, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = Nd.Ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nd.Mist).padding(12.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Close", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, modifier = Modifier.padding(end = 16.dp).bounceClick { secret = null })
                PrimaryButton("Copy key", Modifier.widthIn(min = 140.dp), icon = Lucide.Copy) { copyText(s); app.toast("Key copied"); secret = null }
            }
        }
    }
    revoke?.let { k ->
        ConfirmDialog("Revoke \"${k.name}\"?", "Scripts using this key stop working immediately.", "Revoke", onDismiss = { revoke = null }) {
            revoke = null
            scope.launch { runCatching { app.repo.revokeApiKey(k.id) }.onSuccess { app.toast("Key revoked"); reload++ }.onFailure { app.fail(it, "Couldn't revoke") } }
        }
    }
}
