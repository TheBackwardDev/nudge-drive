package com.nudge.drive.ui.screens.more

import com.composables.icons.lucide.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nudge.drive.AppContainer
import com.nudge.drive.BuildConfig
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.ApiKey
import com.nudge.drive.data.AppJson
import com.nudge.drive.data.AuditLog
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Invite
import com.nudge.drive.data.ShareLink
import com.nudge.drive.data.StorageSummary
import com.nudge.drive.ui.brand.LogoTile
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.FileRow
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.TextInputDialog
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.common.SubTopBar
import com.nudge.drive.ui.screens.profile.MenuGroup
import com.nudge.drive.ui.screens.profile.MenuRow
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.formatBytes
import com.nudge.drive.util.friendlyDate
import com.nudge.drive.util.fullDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun Throwable.msg() = (this as? ApiException)?.friendly ?: message ?: "Something went wrong"

fun copyText(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareText(context: Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
}

// ---------------------------------------------------------------- Trash
@Composable
fun TrashScreen(c: AppContainer, nav: NavController) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<FileItem>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmForever by remember { mutableStateOf<List<String>?>(null) }
    fun load() = scope.launch { files = runCatching { c.repo.trash() }.getOrElse { c.toast(it.msg()); emptyList() } }
    LaunchedEffect(Unit) { load() }

    fun restore(ids: List<String>) = scope.launch {
        runCatching { c.repo.restore(ids) }.onSuccess { c.toast("Restored ${it.restored ?: ids.size}"); selected = emptySet(); load(); c.refreshBus.tryEmit(Unit) }.onFailure { c.toast(it.msg()) }
    }
    fun forever(ids: List<String>) = scope.launch {
        runCatching { c.repo.deleteForever(ids) }.onSuccess { c.toast("Deleted ${it.deleted ?: ids.size} forever"); selected = emptySet(); load() }.onFailure { c.toast(it.msg()) }
    }

    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("Trash", onBack = { nav.popBackStack() }) {
                AnimatedContent(selected.isNotEmpty(), label = "trashAct") { sel ->
                    Row {
                        if (sel) {
                            GlassIconButton(Lucide.ArchiveRestore, "Restore selected") { restore(selected.toList()) }
                            Spacer(Modifier.width(8.dp))
                            GlassIconButton(Lucide.Trash, "Delete selected forever", tint = Nd.Danger) { confirmForever = selected.toList() }
                        } else if (!files.isNullOrEmpty()) {
                            GlassIconButton(Lucide.Trash, "Empty trash", tint = Nd.Danger) { confirmForever = files!!.map { it.id } }
                        }
                    }
                }
            }
            val list = files
            when {
                list == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(5) { Shimmer(Modifier.fillMaxWidth().height(66.dp)) } }
                list.isEmpty() -> EmptyState(Lucide.Trash2, "Trash is empty", "Files you delete stay here until you remove them for good.")
                else -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("Tap to select. Restore puts files back where they were.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                    }
                    items(list, key = { it.id }) { f ->
                        FileRow(
                            f, Modifier.animateItem(), selected = f.id in selected, selectionMode = true,
                            subtitle = "${formatBytes(f.size)} · deleted ${friendlyDate(f.deletedAt)}",
                            onClick = { selected = if (f.id in selected) selected - f.id else selected + f.id },
                        )
                    }
                }
            }
        }
    }
    confirmForever?.let { ids ->
        ConfirmDialog("Delete ${ids.size} file${if (ids.size > 1) "s" else ""} forever?", "They'll be removed from Google Drive / S3 too. This can't be undone.", "Delete forever",
            onDismiss = { confirmForever = null }) { forever(ids); confirmForever = null }
    }
}

// ---------------------------------------------------------------- Shared links & invites
@Composable
fun SharedScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var links by remember { mutableStateOf<List<ShareLink>?>(null) }
    var sent by remember { mutableStateOf<List<Invite>>(emptyList()) }
    var received by remember { mutableStateOf<List<Invite>>(emptyList()) }
    fun load() = scope.launch {
        links = runCatching { c.repo.sharedLinks() }.getOrElse { c.toast(it.msg()); emptyList() }
        runCatching { c.repo.invites() }.onSuccess { sent = it.sent; received = it.received }
    }
    LaunchedEffect(Unit) { load() }

    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("Shared", onBack = { nav.popBackStack() })
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Links ${links?.size ?: ""}", tab == 0) { tab = 0 }
                Chip("Invites sent ${sent.size}", tab == 1) { tab = 1 }
                Chip("Received ${received.size}", tab == 2) { tab = 2 }
            }
            AnimatedContent(tab, label = "sharedTab") { t ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (t) {
                        0 -> {
                            val l = links
                            if (l == null) items(4) { Shimmer(Modifier.fillMaxWidth().height(80.dp)) }
                            else if (l.isEmpty()) item { EmptyState(Lucide.Link, "No public links", "Open a file and tap Share link to create one.") }
                            else items(l, key = { it.id }) { s ->
                                LinkCard(s, Modifier.animateItem(),
                                    onCopy = { copyText(ctx, "Link", s.url.orEmpty()); c.toast("Link copied") },
                                    onShare = { shareText(ctx, s.url.orEmpty()) },
                                    onDisable = { scope.launch { runCatching { c.repo.unshare(s.file.id) }.onSuccess { c.toast("Link turned off"); load() } } },
                                )
                            }
                        }
                        1 -> if (sent.isEmpty()) item { EmptyState(Lucide.Mail, "No invites sent", "Invite someone from a file's detail page.") }
                        else items(sent, key = { it.id }) { inv ->
                            InviteCard(inv, Modifier.animateItem()) { scope.launch { runCatching { c.repo.revokeInvite(inv.id) }.onSuccess { c.toast("Invite revoked"); load() }.onFailure { c.toast(it.msg()) } } }
                        }
                        else -> if (received.isEmpty()) item { EmptyState(Lucide.Mail, "Nothing received", "When a friend invites your email, it shows up here.") }
                        else items(received, key = { it.id }) { inv -> InviteCard(inv, Modifier.animateItem(), onRevoke = null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().softShadow(shape, 10.dp, 0.07f).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).padding(14.dp)) { content() }
}

@Composable
private fun LinkCard(s: ShareLink, modifier: Modifier, onCopy: () -> Unit, onShare: () -> Unit, onDisable: () -> Unit) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.nudge.drive.ui.components.FileThumb(s.file, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.file.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Shared ${friendlyDate(s.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
        }
        Text(s.url.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Nd.IrisDeep, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 10.dp).clip(RoundedCornerShape(10.dp)).background(Nd.Mist).padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Copy", false, icon = Lucide.Copy, onClick = onCopy)
            Chip("Share", false, icon = Lucide.Share2, onClick = onShare)
            Chip("Turn off", false, icon = Lucide.Unlink, onClick = onDisable)
        }
    }
}

@Composable
private fun InviteCard(inv: Invite, modifier: Modifier, onRevoke: (() -> Unit)?) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Nd.Mist), contentAlignment = Alignment.Center) {
                Text(inv.email.first().uppercase(), style = MaterialTheme.typography.titleMedium, color = Nd.IrisDeep)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(inv.user?.name ?: inv.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1)
                Text("${inv.role.replaceFirstChar { it.uppercase() }} · ${inv.target?.name ?: "deleted item"}", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val ok = inv.status == "accepted"
            Text(inv.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall,
                color = if (ok) Nd.Success else Nd.Audio,
                modifier = Modifier.clip(CircleShape).background(if (ok) Nd.SuccessSoft else Nd.soft(Nd.Audio)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
        if (onRevoke != null) Text("Revoke", style = MaterialTheme.typography.labelMedium, color = Nd.Danger,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).bounceClick(onClick = onRevoke).padding(6.dp))
    }
}

// ---------------------------------------------------------------- Activity
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
fun ActivityScreen(c: AppContainer, nav: NavController) {
    var logs by remember { mutableStateOf<List<AuditLog>?>(null) }
    LaunchedEffect(Unit) { logs = runCatching { c.repo.activity() }.getOrElse { c.toast(it.msg()); emptyList() } }
    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("Activity", onBack = { nav.popBackStack() })
            val l = logs
            when {
                l == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(6) { Shimmer(Modifier.fillMaxWidth().height(56.dp)) } }
                l.isEmpty() -> EmptyState(Lucide.History, "No activity yet", "Uploads, moves and deletes will show up here.")
                else -> LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(l, key = { it.id }) { log ->
                        val (text, icon) = log.describe()
                        Row(Modifier.animateItem().fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Nd.Surface).border(1.dp, Nd.Line, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = Nd.Iris, modifier = Modifier.size(19.dp))
                                }
                                Box(Modifier.width(2.dp).height(26.dp).background(Nd.Lilac))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.padding(top = 4.dp)) {
                                Text(text, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(fullDate(log.createdAt), style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- API keys
@Composable
fun ApiKeysScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf<List<ApiKey>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf<String?>(null) }
    var revoke by remember { mutableStateOf<ApiKey?>(null) }
    fun load() = scope.launch { keys = runCatching { c.repo.apiKeys() }.getOrElse { c.toast(it.msg()); emptyList() } }
    LaunchedEffect(Unit) { load() }
    val endpoint = c.repo.publicUploadEndpoint

    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SubTopBar("API keys", onBack = { nav.popBackStack() }) { GlassIconButton(Lucide.Plus, "New key", container = Nd.Primary) { creating = true } } }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Card {
                        Text("Upload endpoint", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                        Text("POST $endpoint", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Nd.IrisDeep,
                            modifier = Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(Nd.Mist).padding(10.dp).fillMaxWidth())
                        Text("Send multipart form data with header Authorization: Bearer <key>. Files are routed like normal uploads.",
                            style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                        Spacer(Modifier.height(8.dp))
                        Chip("Copy endpoint", false, icon = Lucide.Copy) { copyText(ctx, "Endpoint", endpoint); c.toast("Endpoint copied") }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            val l = keys
            when {
                l == null -> items(3) { Shimmer(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().height(70.dp)) }
                l.isEmpty() -> item { EmptyState(Lucide.KeyRound, "No API keys", "Create a key to upload from scripts, CI or other apps.") }
                else -> items(l, key = { it.id }) { k ->
                    Card(Modifier.animateItem().padding(horizontal = 20.dp, vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.KeyRound, null, tint = if (k.status == "active") Nd.Iris else Nd.Subtle)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(k.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                                Text("${k.keyPrefix}… · ${if (k.status == "active") "last used ${k.lastUsedAt?.let(::friendlyDate) ?: "never"}" else "revoked"}",
                                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1)
                            }
                            if (k.status == "active") Text("Revoke", style = MaterialTheme.typography.labelMedium, color = Nd.Danger,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick { revoke = k }.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
    if (creating) TextInputDialog("New API key", placeholder = "Key name (e.g. Laptop backup)", confirm = "Create", onDismiss = { creating = false }) { name ->
        creating = false
        scope.launch { runCatching { c.repo.createApiKey(name) }.onSuccess { secret = it.secret; load() }.onFailure { c.toast(it.msg()) } }
    }
    secret?.let { s ->
        ConfirmDialog("Copy your key now", "This is the only time it's shown:\n\n$s", "Copy key", danger = false,
            onDismiss = { secret = null }) { copyText(ctx, "API key", s); c.toast("Key copied"); secret = null }
    }
    revoke?.let { k ->
        ConfirmDialog("Revoke “${k.name}”?", "Scripts using this key will stop working immediately.", "Revoke", onDismiss = { revoke = null }) {
            revoke = null
            scope.launch { runCatching { c.repo.revokeApiKey(k.id) }.onSuccess { c.toast("Key revoked"); load() } }
        }
    }
}

// ---------------------------------------------------------------- About
@Composable
fun AboutScreen(c: AppContainer, nav: NavController) {
    var summary by remember { mutableStateOf<StorageSummary?>(null) }
    LaunchedEffect(Unit) { summary = runCatching { c.repo.summary() }.getOrNull() }
    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("About", onBack = { nav.popBackStack() })
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoTile(96.dp)
                Spacer(Modifier.height(14.dp))
                Text("Nudge Drive", style = MaterialTheme.typography.headlineMedium, color = Nd.Ink)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
                Spacer(Modifier.height(24.dp))
                MenuGroup {
                    InfoRow("Server", BuildConfig.WEB_BASE)
                    InfoRow("Connected drives", summary?.accounts?.size?.toString() ?: "…")
                    InfoRow("Pool size", summary?.let { formatBytes(it.total) } ?: "…")
                    InfoRow("Free space", summary?.let { formatBytes(it.available) } ?: "…")
                }
                Spacer(Modifier.height(14.dp))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                fun open(uri: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))) }
                MenuGroup {
                    MenuRow(Lucide.ShieldCheck, "Privacy policy") { open("${BuildConfig.WEB_BASE}/privacy") }
                    MenuRow(Lucide.FileText, "Terms of service") { open("${BuildConfig.WEB_BASE}/terms") }
                    MenuRow(Lucide.Mail, "Contact support", sub = "support@nudge.qd.je") { open("mailto:support@nudge.qd.je?subject=Nudge%20Drive%20help") }
                }
                Spacer(Modifier.height(16.dp))
                Text("Your files stream straight between this phone and your own Google Drive / S3 accounts through your private server.",
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(horizontal = 12.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
    }
}
