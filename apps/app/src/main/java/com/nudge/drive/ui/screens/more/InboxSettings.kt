package com.nudge.drive.ui.screens.more

import com.composables.icons.lucide.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.nudge.drive.AppContainer
import com.nudge.drive.data.Notice
import com.nudge.drive.data.NoticeType
import com.nudge.drive.data.ThemeMode
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.EmptyState
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.data.destination
import com.nudge.drive.ui.screens.common.SubTopBar
import com.nudge.drive.ui.screens.profile.MenuGroup
import com.nudge.drive.ui.screens.profile.MenuRow
import com.nudge.drive.ui.theme.DarkNd
import com.nudge.drive.ui.theme.LightNd
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.NdColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun NoticeType.visual(): Pair<ImageVector, Color> = when (this) {
    NoticeType.Upload -> Lucide.CloudUpload to Nd.Iris
    NoticeType.UploadFailed -> Lucide.CircleAlert to Nd.Pdf
    NoticeType.Download -> Lucide.Download to Nd.Image
    NoticeType.Drive -> Lucide.HardDrive to Nd.Doc
    NoticeType.Storage -> Lucide.TriangleAlert to Nd.Video
    NoticeType.Share -> Lucide.Link to Nd.Zip
    NoticeType.Invite -> Lucide.UserPlus to Nd.Image
    NoticeType.Info -> Lucide.Info to Nd.Iris
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun dayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "Earlier" }
}

private fun stamp(ms: Long): String {
    val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (z.toLocalDate() == LocalDate.now()) timeFmt.format(z) else dayFmt.format(z) + " · " + timeFmt.format(z)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val items by c.notices.items.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val sysOn by c.prefs.systemNotifications.collectAsState()
    var permitted by remember { mutableStateOf(c.notices.canPostSystem()) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) c.prefs.setSystemNotifications(true)
        permitted = c.notices.canPostSystem()
    }

    ScreenBackground {
        Column(Modifier.fillMaxSize()) {
            SubTopBar("Notifications", onBack = { nav.popBackStack() }) {
                if (items.any { !it.read }) { GlassIconButton(Lucide.CheckCheck, "Mark all as read") { c.notices.markAllRead() }; Spacer(Modifier.width(8.dp)) }
                if (items.isNotEmpty()) GlassIconButton(Lucide.Trash2, "Clear all") { confirmClear = true }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!permitted || !sysOn) item {
                    val shape = RoundedCornerShape(22.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(Nd.Mist).bounceClick {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { c.prefs.setSystemNotifications(true); permitted = c.notices.canPostSystem() }
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.BellPlus, null, tint = Nd.Accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Turn on phone notifications", style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
                            Text("Get told when uploads finish, friends share files or a drive fills up.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
                        }
                    }
                }
                if (items.isEmpty()) item { EmptyState(Lucide.BellOff, "You're all caught up", "Uploads, shares and storage alerts will show up here.") }
                var lastLabel = ""
                items.forEach { n ->
                    val label = dayLabel(n.time)
                    if (label != lastLabel) {
                        lastLabel = label
                        item(key = "h-$label") { Text(label, style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                    }
                    item(key = n.id) {
                        val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            if (v != SwipeToDismissBoxValue.Settled) c.notices.remove(n.id); true
                        })
                        SwipeToDismissBox(
                            state = state, modifier = Modifier.animateItem(),
                            backgroundContent = {
                                // Only while actually swiping, so it never shows through the card.
                                if (state.dismissDirection != SwipeToDismissBoxValue.Settled) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Nd.DangerSoft).padding(horizontal = 22.dp), contentAlignment = Alignment.CenterEnd) {
                                    Icon(Lucide.Trash2, "Remove", tint = Nd.Danger)
                                }
                            },
                        ) {
                            NoticeRow(n) {
                                c.notices.markRead(n.id)
                                c.pendingRoute.value = n.destination()
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Clear all notifications?", "This only clears the list in the app.", "Clear", onDismiss = { confirmClear = false }) {
        c.notices.clear(); confirmClear = false
    }
}

@Composable
private fun NoticeRow(n: Notice, onClick: () -> Unit) {
    val (icon, color) = n.type.visual()
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().softShadow(shape, 10.dp, 0.07f).clip(shape).background(if (n.read) Nd.Glass else Nd.GlassStrong)
            .border(1.dp, if (n.read) Nd.GlassBorder else Nd.Lilac, shape).bounceClick(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Nd.soft(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, color = Nd.Ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!n.read) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(Nd.Accent))
            }
            Text(n.body, style = MaterialTheme.typography.bodySmall, color = Nd.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(stamp(n.time), style = MaterialTheme.typography.labelSmall, color = Nd.Subtle, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------------------------------------------------------------- Settings
@Composable
fun SettingsScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val theme by c.prefs.theme.collectAsState()
    val sys by c.prefs.systemNotifications.collectAsState()
    val alerts by c.prefs.storageAlerts.collectAsState()
    val grid by c.prefs.grid.collectAsState()
    var permitted by remember { mutableStateOf(c.notices.canPostSystem()) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        c.prefs.setSystemNotifications(ok); permitted = c.notices.canPostSystem()
        if (!ok) c.toast("Allow notifications for Nudge Drive in Android settings")
    }
    LaunchedEffect(sys) { permitted = c.notices.canPostSystem() }

    ScreenBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            SubTopBar("Settings", onBack = { nav.popBackStack() })
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Appearance", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                    val revealScope = androidx.compose.runtime.rememberCoroutineScope()
                    ThemeMode.entries.forEach { m ->
                        var center by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        ThemeOption(m, theme == m, Modifier.weight(1f).onGloballyPositioned { center = it.boundsInWindow().center }) {
                            if (theme != m) com.nudge.drive.ui.theme.ThemeReveal.switch(activity, center, revealScope) { c.prefs.setTheme(m) }
                        }
                    }
                }
                Text("Notifications", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                MenuGroup {
                    ToggleRow(Lucide.Bell, "Phone notifications", "Uploads, downloads, shares and alerts", sys && permitted) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { c.prefs.setSystemNotifications(on); permitted = c.notices.canPostSystem() }
                    }
                    ToggleRow(Lucide.TriangleAlert, "Storage alerts", "Warn me when a drive is almost full", alerts) { c.prefs.setStorageAlerts(it) }
                    MenuRow(Lucide.Settings2, "Android notification settings", sub = "Sounds, channels and badges") {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                }
                Text("Files", style = MaterialTheme.typography.labelMedium, color = Nd.Muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                MenuGroup {
                    ToggleRow(Lucide.LayoutGrid, "Grid view by default", "Show files as thumbnails instead of a list", grid) { c.prefs.setGrid(it) }
                    MenuRow(Lucide.Trash2, "Trash") { nav.navigate(Routes.Trash) }
                }
                MenuGroup { MenuRow(Lucide.Info, "About Nudge Drive") { nav.navigate(Routes.About) } }
            }
        }
    }
}

@Composable
private fun ThemeOption(m: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) Nd.Accent else Nd.GlassBorder, label = "ring")
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, spring(), label = "w")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.softShadow(shape, 10.dp, 0.08f).clip(shape).background(Nd.Glass).border(width, ring, shape).bounceClick(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(14.dp))) {
            when (m) {
                ThemeMode.Light -> MiniPreview(LightNd, Modifier.fillMaxSize())
                ThemeMode.Dark -> MiniPreview(DarkNd, Modifier.fillMaxSize())
                ThemeMode.System -> Row(Modifier.fillMaxSize()) {
                    MiniPreview(LightNd, Modifier.weight(1f).fillMaxSize()); MiniPreview(DarkNd, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (m) { ThemeMode.System -> Lucide.SunMoon; ThemeMode.Light -> Lucide.Sun; ThemeMode.Dark -> Lucide.Moon },
                null, tint = if (selected) Nd.Accent else Nd.Muted, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(m.label, style = MaterialTheme.typography.labelMedium, color = Nd.Ink)
        }
    }
}

/** Tiny phone sketch drawn with a given palette. */
@Composable
private fun MiniPreview(t: NdColors, modifier: Modifier) {
    Box(modifier.background(t.background).padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Bar(t.ink.copy(alpha = 0.8f), 30.dp, 5.dp)
            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(7.dp)).background(t.glassStrong).padding(5.dp)) {
                Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(CircleShape).background(Nd.Iris).align(Alignment.BottomStart))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Nd.Primary))
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(t.glass))
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(t.glass))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).height(9.dp).clip(CircleShape).background(t.glassStrong))
    }
}

@Composable
private fun Bar(color: Color, w: Dp, h: Dp) = Box(Modifier.size(w, h).clip(CircleShape).background(color))

@Composable
private fun ToggleRow(icon: ImageVector, label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).bounceClick { onChange(!checked) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Nd.IrisDeep, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Nd.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Nd.Iris, checkedThumbColor = Color.White, uncheckedTrackColor = Nd.Mist, uncheckedBorderColor = Nd.Line))
    }
}
