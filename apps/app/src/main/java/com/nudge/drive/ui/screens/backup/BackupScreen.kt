package com.nudge.drive.ui.screens.backup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.FolderCheck
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Images
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkle
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import com.nudge.drive.AppContainer
import com.nudge.drive.data.BackupRange
import com.nudge.drive.data.DeviceFolder
import com.nudge.drive.data.DeviceMedia
import com.nudge.drive.data.MediaAccess
import com.nudge.drive.ui.components.Chip
import com.nudge.drive.ui.components.NudgeSheet
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.Shimmer
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.common.SubTopBar
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.util.friendlyDate
import java.time.Instant
import kotlinx.coroutines.launch

/** The steps of turning backup on, one at a time. */
private sealed interface Setup {
    data object Idle : Setup
    data object AskingPermission : Setup
    data object AskingRange : Setup
}

/**
 * Turning backup on, Google Photos style: allow photo access → Camera is selected → "back up what's
 * already there?" → on. Nothing is switched on until the user confirms the last step.
 */
@Composable
fun rememberBackupSetup(c: AppContainer, onEnabled: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf<Setup>(Setup.Idle) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        step = if (DeviceMedia.access(ctx) == MediaAccess.None) { c.toast("Nudge needs access to your photos to back them up."); Setup.Idle } else Setup.AskingRange
    }
    LaunchedEffect(step) { if (step == Setup.AskingPermission) perm.launch(DeviceMedia.permissions()) }
    if (step == Setup.AskingRange) RangeSheet("Back up photos already on your phone?", onDismiss = { step = Setup.Idle }) { r ->
        step = Setup.Idle
        c.scope.launchCatching {
            // Camera by default, like Google Photos. More folders can be switched on in Backup.
            val folders = DeviceMedia.folders(ctx)
            val picks = folders.filter { it.isCamera }.ifEmpty { folders.take(1) }
            picks.forEach { c.backup.setBucket(it, true) }
            c.backup.setRange(r)
            c.backup.update { it.copy(enabled = true) }
            c.backup.runNow()
            c.toast(if (r == BackupRange.NewOnly) "Backup is on" else "Backing up · ${r.label.lowercase()}")
        }
        onEnabled()
    }
    return { step = if (DeviceMedia.access(ctx) == MediaAccess.None) Setup.AskingPermission else Setup.AskingRange }
}

private fun kotlinx.coroutines.CoroutineScope.launchCatching(block: suspend () -> Unit) =
    launch { runCatching { block() } }

@Composable
fun RangeSheet(title: String, onDismiss: () -> Unit, onPick: (BackupRange) -> Unit) {
    NudgeSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Text("New photos are always backed up. Choose what to do with the ones you already have.",
            style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        BackupRange.entries.forEach { r ->
            val (icon, sub) = when (r) {
                BackupRange.NewOnly -> Lucide.Sparkle to "Skip what's already there"
                BackupRange.Week -> Lucide.Calendar to "Recent photos and videos"
                BackupRange.Month -> Lucide.Calendar to "About a month of photos"
                BackupRange.All -> Lucide.Images to "Can take a while and use lots of data"
            }
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape)
                    .bounceClick { onPick(r) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Nd.Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                }
                Icon(Lucide.ChevronRight, null, tint = Nd.Subtle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val cfg by c.backup.config.collectAsState()
    val running by c.backup.running.collectAsState()
    val done by c.backup.backedUp.collectAsState()
    var rangeOpen by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(DeviceMedia.access(ctx)) }
    var folders by remember { mutableStateOf<List<DeviceFolder>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val setup = rememberBackupSetup(c)
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = DeviceMedia.access(ctx); reload++ }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) c.backup.addFolder(uri) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = DeviceMedia.access(ctx); reload++ }
    LaunchedEffect(access, reload) { folders = if (access == MediaAccess.None) emptyList() else DeviceMedia.folders(ctx) }
    // Rough "backed up" counts per folder, from the index of uploaded items.
    // Recounted in steps (not on every single upload) to keep scrolling smooth during a backup.
    val doneKey = if (running == null) done.size else -1 - done.size / 25
    val doneCounts by androidx.compose.runtime.produceState(emptyMap<Long, Int>(), folders, doneKey, cfg.buckets) {
        val list = folders ?: return@produceState
        value = list.associate { f ->
            f.bucketId to if (cfg.buckets.any { it.bucketId == f.bucketId }) DeviceMedia.items(ctx, f.bucketId).count { it.key in done } else 0
        }
    }
    val on = cfg.enabled && cfg.hasSources

    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SubTopBar("Backup", onBack = { nav.popBackStack() }) }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatusCard(on, running, cfg.lastRunMs, cfg.lastCount, cfg.totalCount)
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Back up photos & videos", style = MaterialTheme.typography.titleMedium, color = Nd.Ink)
                                Text("From the folders you switch on below", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                            }
                            NdSwitch(on) { v -> if (v && !cfg.hasSources) setup() else c.backup.update { it.copy(enabled = v) } }
                        }
                    }
                    if (access != MediaAccess.Full) AccessCard(access, onAllow = { perm.launch(DeviceMedia.permissions()) }, onSettings = {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }
                    })
                    Label("Photo folders on this phone")
                }
            }
            val list = folders
            when {
                list == null -> items(4) { Shimmer(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().height(72.dp), corner = 20.dp) }
                list.isEmpty() && access != MediaAccess.None -> item {
                    Text("No photos or videos found on this phone yet.", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                else -> items(list, key = { it.bucketId }) { f ->
                    val selected = cfg.buckets.any { it.bucketId == f.bucketId }
                    DeviceFolderRow(f, selected, if (selected) doneCounts[f.bucketId] ?: 0 else null,
                        onToggle = { v -> c.backup.setBucket(f, v); if (v && cfg.enabled) c.backup.runNow() },
                        onOpen = { nav.navigate(com.nudge.drive.ui.nav.Routes.deviceFolder(f.bucketId)) },
                        modifier = Modifier.animateItem().padding(horizontal = 20.dp, vertical = 5.dp))
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Label("Other folders (documents, downloads…)")
                    Card {
                        cfg.folders.forEach { f ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
                                    Icon(Lucide.FolderCheck, null, tint = Nd.Accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(f.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Box(Modifier.size(40.dp).clip(CircleShape).bounceClick { c.backup.removeFolder(f.uri) }, contentAlignment = Alignment.Center) {
                                    Icon(Lucide.X, "Stop backing up ${f.name}", tint = Nd.Muted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = if (cfg.folders.isEmpty()) 0.dp else 6.dp).clip(RoundedCornerShape(16.dp)).background(Nd.Mist)
                                .bounceClick { addFolder.launch(null) }.padding(12.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Lucide.FolderPlus, null, tint = Nd.IrisDeep, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add another folder", style = MaterialTheme.typography.labelLarge, color = Nd.IrisDeep)
                        }
                    }

                    Label("Older photos")
                    Card(onClick = { rangeOpen = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                            Icon(Lucide.History, null, tint = Nd.Accent)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cfg.sinceLabel, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                                Text("Tap to back up older photos too", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                            }
                            Icon(Lucide.ChevronRight, null, tint = Nd.Subtle)
                        }
                    }

                    Label("How often")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "Every hour", 6 to "Every 6 hours", 12 to "Twice a day", 24 to "Once a day").forEach { (h, label) ->
                            Chip(label, cfg.everyHours == h) { c.backup.update { it.copy(everyHours = h) } }
                        }
                    }
                    Card {
                        Toggle(Lucide.Wifi, "Only on Wi-Fi", "Don't use mobile data", cfg.wifiOnly) { v -> c.backup.update { it.copy(wifiOnly = v) } }
                        Toggle(Lucide.Zap, "Only while charging", "Save battery", cfg.chargingOnly) { v -> c.backup.update { it.copy(chargingOnly = v) } }
                    }
                    PrimaryButton(
                        if (running != null) "Backing up…" else "Back up now", Modifier.fillMaxWidth(),
                        enabled = cfg.hasSources && running == null, icon = Lucide.CloudUpload,
                    ) { c.backup.update { it.copy(enabled = true) }; c.backup.runNow(); c.toast("Backup started") }
                    Text("Backed-up photos keep their original date and appear in My Files › Phone backup.",
                        style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
    if (rangeOpen) RangeSheet("Which photos should be backed up?", onDismiss = { rangeOpen = false }) { r ->
        rangeOpen = false; c.backup.setRange(r); if (r != BackupRange.NewOnly && cfg.enabled) { c.backup.runNow(); c.toast("Backing up · ${r.label.lowercase()}") }
    }
}

@Composable
private fun DeviceFolderRow(f: DeviceFolder, selected: Boolean, doneCount: Int?, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Nd.Glass).border(1.dp, if (selected) Nd.Lavender else Nd.GlassBorder, shape)
            .bounceClick(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(f.cover, f.name, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Nd.Mist))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("${f.count} item${if (f.count == 1) "" else "s"}")
                    if (doneCount != null) append(" · ${if (doneCount >= f.count) "all backed up" else "$doneCount backed up"}")
                },
                style = MaterialTheme.typography.bodySmall, color = if (doneCount != null && doneCount >= f.count) Nd.Success else Nd.Muted, maxLines = 1,
            )
        }
        NdSwitch(selected, onToggle)
    }
}

@Composable
private fun AccessCard(access: MediaAccess, onAllow: () -> Unit, onSettings: () -> Unit) {
    Card {
        Text(if (access == MediaAccess.Partial) "Nudge can only see some photos" else "Allow access to your photos",
            style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.padding(4.dp))
        Text(if (access == MediaAccess.Partial) "Choose \"Allow all\" so new photos are backed up automatically."
            else "Nudge needs this to show your photo folders and back them up.",
            style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(horizontal = 4.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Allow", Modifier.weight(1f), onClick = onAllow)
            PrimaryButton("Settings", Modifier.weight(1f), container = Nd.GlassStrong, content = Nd.Ink, onClick = onSettings)
        }
    }
}

@Composable
fun NdSwitch(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Nd.Primary, checkedThumbColor = Color.White, uncheckedTrackColor = Nd.Mist, uncheckedBorderColor = Nd.Line))

@Composable
private fun StatusCard(on: Boolean, running: com.nudge.drive.data.BackupProgress?, lastRun: Long, lastCount: Int, total: Int) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val bg by animateColorAsState(if (on) Nd.Primary else Nd.Mist, label = "bg")
    val shape = RoundedCornerShape(28.dp)
    Column(Modifier.fillMaxWidth().softShadow(shape, 18.dp, 0.14f).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).scale(if (running != null) pulse else 1f).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(if (on) Lucide.CloudUpload else Lucide.CloudOff, null, tint = if (on) Color.White else Nd.Muted, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (running != null) "Backing up now" else if (on) "Backup is on" else "Backup is off", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
                Text(
                    when {
                        running != null -> running.current
                        lastRun > 0 -> "Last backup ${friendlyDate(Instant.ofEpochMilli(lastRun).toString()).lowercase()} · " +
                            if (lastCount > 0) "$lastCount new" else "all up to date"
                        on -> "Waiting for the first run"
                        else -> "Turn it on to protect your photos"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(running != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 14.dp)) {
                val r = running
                if (r != null && r.total > 0) {
                    LinearProgressIndicator({ r.done.toFloat() / r.total }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist, strokeCap = StrokeCap.Round)
                    Text("${r.done} of ${r.total} files", style = MaterialTheme.typography.labelSmall, color = Nd.Muted, modifier = Modifier.padding(top = 6.dp))
                } else LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Nd.Primary, trackColor = Nd.Mist, strokeCap = StrokeCap.Round)
            }
        }
        if (total > 0) Text("$total files backed up so far", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().softShadow(shape, 12.dp, 0.08f).clip(shape).background(Nd.Glass).border(1.dp, Nd.GlassBorder, shape)
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier).padding(12.dp),
        content = content,
    )
}

@Composable
private fun Toggle(icon: ImageVector, label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).bounceClick { onChange(!checked) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Nd.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        NdSwitch(checked, onChange)
    }
}
