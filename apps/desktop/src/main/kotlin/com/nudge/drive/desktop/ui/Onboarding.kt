package com.nudge.drive.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.HardDriveDownload
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.PartyPopper
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ShieldCheck
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.data.ImportStatus
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.BackupFolder
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.ui.brand.AnimatedLogoTile
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.UsageRing
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import com.nudge.drive.desktop.ui.pages.ConnectState
import com.nudge.drive.desktop.ui.pages.WaitingForGoogle
import com.nudge.drive.desktop.ui.pages.awaitConnectedDrive
import com.nudge.drive.desktop.ui.pages.startGoogleConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Whether the signed-in user still needs the first-run setup (shown once per user on this computer). */
@Composable
fun needsOnboarding(app: AppState): Boolean {
    val user by app.session.user.collectAsState()
    val settings by app.settings.state.collectAsState()
    val id = user?.id ?: return false
    return id !in settings.onboardedUsers
}

/** People already signed in before the setup existed shouldn't see it. Call once after the session loads. */
fun migrateOnboarding(app: AppState) {
    if (app.settings.value.onboardMigrated) return
    val existing = app.session.user.value?.id.takeIf { app.session.loggedIn.value == true }
    app.settings.update { s -> s.copy(onboardMigrated = true, onboardedUsers = if (existing != null) s.onboardedUsers + existing else s.onboardedUsers) }
}

private fun AppState.markOnboarded() {
    val id = session.user.value?.id ?: return
    settings.update { it.copy(onboardedUsers = it.onboardedUsers + id) }
}

/** First-run setup after sign-in: welcome → connect Google Drive → import existing files → PC backup → done. */
@Composable
fun Onboarding(app: AppState) {
    val scope = rememberCoroutineScope()
    val user by app.session.user.collectAsState()
    val settings by app.settings.state.collectAsState()
    var step by remember { mutableStateOf(0) }
    var accounts by remember { mutableStateOf<List<ConnectedAccount>>(emptyList()) }
    var connect by remember { mutableStateOf<ConnectState>(ConnectState.Idle) }
    val imports = remember { mutableStateMapOf<String, ImportStatus>() }

    suspend fun refresh() { runCatching { app.repo.accounts() }.onSuccess { accounts = it } }
    LaunchedEffect(Unit) { refresh() }
    // After "Connect Google Drive", wait for the new drive to show up (the browser does the rest).
    LaunchedEffect(connect) {
        val w = connect as? ConnectState.Waiting ?: return@LaunchedEffect
        val now = awaitConnectedDrive(app, w)
        if (now != null) { accounts = now; app.toast("Drive connected"); app.changed(); app.bringToFront() }
        connect = ConnectState.Idle
    }
    LaunchedEffect(imports.values.any { it.state == "running" }) {
        while (imports.values.any { it.state == "running" }) {
            delay(2000)
            imports.keys.toList().forEach { id -> runCatching { app.repo.importStatus(id) }.onSuccess { imports[id] = it } }
        }
    }

    fun connectGoogle() {
        if (connect == ConnectState.Opening) return
        connect = ConnectState.Opening
        scope.launch {
            // The list may still be loading: read what is there now rather than compare against nothing.
            connect = try { startGoogleConnect(app, null) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { app.fail(e, "Couldn't start connecting"); ConnectState.Idle }
        }
    }
    fun addFolder(dir: File?) {
        if (dir == null) return
        if (app.settings.value.backupFolders.any { File(it.path) == dir }) return
        app.settings.update { it.copy(backupFolders = it.backupFolders + BackupFolder(dir.absolutePath)) }
        app.backup.start(); app.backup.restartWatcher(); app.backup.trigger()
    }
    fun finish() { app.markOnboarded(); app.changed() }

    Column(Modifier.fillMaxSize()) {
        // Progress + skip
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i ->
                    val col by animateColorAsState(if (i <= step) Nd.Primary else Nd.Lilac, label = "seg")
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(col))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (step in 1..3) Text("Skip", style = MaterialTheme.typography.labelLarge, color = Nd.Muted,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick { step = 4 }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        AnimatedContent(
            step, Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { it * dir / 6 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it * dir / 8 } + fadeOut(tween(150)))
            },
            label = "step",
        ) { s ->
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (s) {
                            0 -> StepWelcome(user?.name?.substringBefore(' ')?.ifBlank { null } ?: "there") { step = 1 }
                            1 -> StepConnect(accounts, connect == ConnectState.Opening, connect is ConnectState.Waiting, onConnect = ::connectGoogle, onCancel = { connect = ConnectState.Idle }) { step = 2 }
                            2 -> StepImport(accounts.filter { it.isGoogle }, imports, onImport = { a ->
                                scope.launch { runCatching { app.repo.importDrive(a.id) }.onSuccess { imports[a.id] = it }.onFailure { app.fail(it, "Couldn't start the import") } }
                            }) { step = 3 }
                            3 -> StepBackup(settings.backupFolders, onPick = { addFolder(pickFolder(app.awtWindow, "Choose a folder to back up")) }, onAdd = ::addFolder) { step = 4 }
                            else -> StepDone(accounts, ::finish)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Title(title: String, body: String) {
    Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.8).sp, color = Nd.Ink, textAlign = TextAlign.Center)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
}

@Composable
private fun ColumnScope.Bottom(primary: String, enabled: Boolean = true, loading: Boolean = false, onPrimary: () -> Unit, secondary: String? = null, onSecondary: (() -> Unit)? = null) {
    Spacer(Modifier.height(28.dp))
    PrimaryButton(primary, Modifier.fillMaxWidth(), enabled = enabled, loading = loading, onClick = onPrimary)
    if (secondary != null && onSecondary != null) Text(
        secondary, style = MaterialTheme.typography.labelLarge, color = Nd.IrisDeep, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).bounceClick(onClick = onSecondary).padding(12.dp),
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.StepWelcome(name: String, next: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    AnimatedLogoTile(110.dp, durationMs = 1100)
    Spacer(Modifier.height(28.dp))
    Title("Welcome, $name!", "Nudge Drive joins all your Google Drives into one big storage pool.")
    Feature(Lucide.Layers, "One pool for every drive", "15 GB free per Google account, added together")
    Feature(Lucide.CloudUpload, "Smart uploads", "Each file goes to the drive with the most room")
    Feature(Lucide.ShieldCheck, "Automatic backup", "Keep folders on this computer safe without thinking")
    Bottom("Let's set it up", onPrimary = next)
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Nd.Glass).border(1.dp, Nd.GlassBorder, shape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Nd.Accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink); Text(body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted) }
    }
}

@Composable
private fun ColumnScope.StepConnect(accounts: List<ConnectedAccount>, connecting: Boolean, waiting: Boolean, onConnect: () -> Unit, onCancel: () -> Unit, next: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.size(120.dp).softShadow(RoundedCornerShape(34.dp), 24.dp, 0.25f).clip(RoundedCornerShape(34.dp)).background(Nd.Surface), contentAlignment = Alignment.Center) {
        DriveGlyph(Modifier.size(66.dp))
    }
    Spacer(Modifier.height(24.dp))
    Title("Connect your Google Drive", "Pick an account on Google's page in your browser, then come back here. Add as many as you like.")
    accounts.forEach { a ->
        val t = a.total
        val shape = RoundedCornerShape(20.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UsageRing(if (t == null || t <= 0) 0f else a.used.toFloat() / t, size = 42.dp, stroke = 4.dp, label = if (t == null) "∞" else null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (t != null) "${formatBytes((t - a.used).coerceAtLeast(0))} free" else "Connected", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
            }
            Icon(Lucide.CircleCheck, "Connected", tint = Nd.Success)
        }
    }
    if (waiting) WaitingForGoogle(Modifier.fillMaxWidth().padding(top = 10.dp), onCancel)
    if (accounts.isNotEmpty()) SecondaryButton("Add another account", Modifier.fillMaxWidth().padding(top = 10.dp), icon = Lucide.Plus, onClick = onConnect)
    if (accounts.isEmpty()) Bottom("Connect Google Drive", loading = connecting, onPrimary = onConnect)
    else Bottom("Continue", onPrimary = next)
}

@Composable
private fun ColumnScope.StepImport(accounts: List<ConnectedAccount>, imports: Map<String, ImportStatus>, onImport: (ConnectedAccount) -> Unit, next: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(34.dp)).background(Nd.Primary), contentAlignment = Alignment.Center) {
        Icon(Lucide.HardDriveDownload, null, tint = Color.White, modifier = Modifier.size(48.dp))
    }
    Spacer(Modifier.height(24.dp))
    Title("Bring in your existing files?", "Show everything already in your Google Drive inside Nudge Drive, folders and all. Nothing is moved or copied.")
    if (accounts.isEmpty()) Text("Connect a Google Drive first to import its files.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
    accounts.forEach { a ->
        val st = imports[a.id]
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Nd.Surface), contentAlignment = Alignment.Center) { DriveGlyph(Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.email, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (st?.state) {
                            "running" -> "Importing… ${st.folders} folders, ${st.created} files so far"
                            "done" -> "Done · ${st.created} files added"
                            "failed" -> st.error ?: "Import failed"
                            else -> "Not imported yet"
                        },
                        style = MaterialTheme.typography.bodySmall, color = if (st?.state == "failed") Nd.Danger else Nd.Muted,
                    )
                }
                when (st?.state) {
                    "running" -> CircularProgressIndicator(Modifier.size(24.dp), color = Nd.Primary, strokeWidth = 2.5.dp)
                    "done" -> Icon(Lucide.CircleCheck, null, tint = Nd.Success)
                    else -> Text(if (st?.state == "failed") "Retry" else "Import", style = MaterialTheme.typography.labelLarge, color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(Nd.Primary).bounceClick { onImport(a) }.padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
            if (st?.state == "running") LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist, strokeCap = StrokeCap.Round)
        }
    }
    Text("Tip: deleting an imported folder in the app also deletes it from Google Drive.", style = MaterialTheme.typography.bodySmall, color = Nd.Subtle, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    Bottom(if (imports.values.any { it.state == "running" }) "Continue (keeps importing)" else "Continue", onPrimary = next)
}

@Composable
private fun ColumnScope.StepBackup(folders: List<BackupFolder>, onPick: () -> Unit, onAdd: (File) -> Unit, next: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(34.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
        Icon(Lucide.Monitor, null, tint = Nd.Accent, modifier = Modifier.size(52.dp))
        Box(Modifier.align(Alignment.BottomEnd).offset((-8).dp, (-8).dp).size(38.dp).clip(CircleShape).background(Nd.Primary), contentAlignment = Alignment.Center) {
            Icon(Lucide.CloudUpload, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
    Spacer(Modifier.height(24.dp))
    Title("Back up this computer automatically", "Choose a folder like Documents or Pictures. New and changed files upload on their own, into \"PC backup\" on your drives.")
    val on = folders.isNotEmpty()
    AnimatedVisibility(on, enter = scaleIn() + fadeIn()) {
        Row(Modifier.clip(CircleShape).background(Nd.SuccessSoft).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleCheck, null, tint = Nd.Success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Backing up ${folders.size} folder${if (folders.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = Nd.Success)
        }
    }
    folders.forEach { bf ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.FolderOpen, null, tint = Nd.Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
            Text(bf.path, style = MaterialTheme.typography.bodyMedium, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    // Quick picks for the usual folders.
    val home = File(System.getProperty("user.home"))
    val suggestions = listOf("Documents", "Pictures", "Desktop", "Videos").map { File(home, it) }
        .filter { it.isDirectory && folders.none { b -> File(b.path) == it } }
    if (suggestions.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        suggestions.forEach { d ->
            Row(Modifier.clip(CircleShape).background(Nd.Glass).border(1.dp, Nd.GlassBorder, CircleShape).bounceClick { onAdd(d) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Plus, null, tint = Nd.Ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(d.name, style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
            }
        }
    }
    if (on) {
        SecondaryButton("Add another folder", Modifier.fillMaxWidth().padding(top = 16.dp), icon = Lucide.Plus, onClick = onPick)
        Bottom("Continue", onPrimary = next)
    } else Bottom("Choose a folder", onPrimary = onPick, secondary = "Maybe later", onSecondary = next)
}

@Composable
private fun ColumnScope.StepDone(accounts: List<ConnectedAccount>, finish: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 200f)) }
    Spacer(Modifier.height(40.dp))
    Box(Modifier.size(130.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1 - pop.value) * -30f }.clip(CircleShape).background(Nd.Primary), contentAlignment = Alignment.Center) {
        Icon(Lucide.PartyPopper, null, tint = Color.White, modifier = Modifier.size(60.dp))
    }
    Spacer(Modifier.height(28.dp))
    val total = accounts.sumOf { it.total ?: 0L }
    Title("You're all set!", if (accounts.isEmpty()) "You can connect a drive any time from Drives in the sidebar." else "${accounts.size} drive${if (accounts.size == 1) "" else "s"} connected · ${formatBytes(total)} in your pool.")
    Bottom("Go to my drive", onPrimary = finish)
}
