package com.nudge.drive.ui.screens.profile

import com.composables.icons.lucide.*
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.drive.AppContainer
import com.nudge.drive.data.StorageSummary
import com.nudge.drive.ui.components.ConfirmDialog
import com.nudge.drive.ui.components.StripedBar
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.ui.screens.main.MainActions
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(c: AppContainer, actions: MainActions) {
    val user by c.session.user.collectAsState()
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<StorageSummary?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { c.repo.me() }
        summary = runCatching { c.repo.summary() }.getOrNull()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            val darkNow = Nd.isDark
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            val revealScope = androidx.compose.runtime.rememberCoroutineScope()
            var toggleCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            Row(Modifier.statusBarsPadding().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", style = MaterialTheme.typography.headlineMedium, color = Nd.Ink, modifier = Modifier.weight(1f))
                // Quick light/dark switch; long-term choice lives in Settings.
                com.nudge.drive.ui.components.GlassIconButton(
                    if (Nd.isDark) Lucide.Sun else Lucide.Moon, if (Nd.isDark) "Switch to light mode" else "Switch to dark mode",
                    modifier = Modifier.onGloballyPositioned { toggleCenter = it.boundsInWindow().center },
                ) {
                    com.nudge.drive.ui.theme.ThemeReveal.switch(activity, toggleCenter, revealScope) {
                        c.prefs.setTheme(if (darkNow) com.nudge.drive.data.ThemeMode.Light else com.nudge.drive.data.ThemeMode.Dark)
                    }
                }
            }
        }
        item {
            val shape = RoundedCornerShape(28.dp)
            Column(Modifier.fillMaxWidth().softShadow(shape, 18.dp, 0.12f).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFCDBFFF), Nd.Iris))), contentAlignment = Alignment.Center) {
                        Text(user?.name?.firstOrNull()?.uppercase() ?: "N", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(user?.name ?: "—", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
                    }
                }
                summary?.let { s ->
                    Spacer(Modifier.height(18.dp))
                    StripedBar(if (s.total > 0) s.used.toFloat() / s.total else 0f, Modifier.fillMaxWidth(), height = 14.dp)
                    Text("${formatBytes(s.used)} used of ${formatBytes(s.total)} across ${s.accounts.size} drive${if (s.accounts.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            MenuGroup {
                MenuRow(Lucide.Bell, "Notifications") { actions.navigate(Routes.Notifications) }
                MenuRow(Lucide.Settings, "Settings", sub = "Theme, notifications, default view") { actions.navigate(Routes.Settings) }
                MenuRow(Lucide.Layers, "Drives & upload rules") { actions.goTab("drives") }
                MenuRow(Lucide.ShieldCheck, "Auto backup", sub = "Back up phone folders automatically") { actions.navigate(Routes.Backup) }
                MenuRow(Lucide.Share2, "Shared links & invites") { actions.navigate(Routes.Shared) }
                MenuRow(Lucide.Trash2, "Trash") { actions.navigate(Routes.Trash) }
                MenuRow(Lucide.History, "Activity") { actions.navigate(Routes.Activity) }
            }
        }
        item {
            MenuGroup {
                MenuRow(Lucide.RefreshCw, "Sync from Google Drive", sub = "Pick up files added on drive.google.com") {
                    scope.launch {
                        runCatching { c.repo.syncGoogle() }
                            .onSuccess { r -> c.toast("Synced ${r.results.sumOf { it.created + it.updated }} changes"); c.refreshBus.tryEmit(Unit) }
                            .onFailure { c.toast("Sync failed") }
                    }
                }
                MenuRow(Lucide.KeyRound, "API keys", sub = "Upload from scripts and other apps") { actions.navigate(Routes.ApiKeys) }
                MenuRow(Lucide.Info, "About Nudge Drive") { actions.navigate(Routes.About) }
            }
        }
        item {
            MenuGroup {
                MenuRow(Lucide.LogOut, "Sign out", tint = Nd.Danger) { confirmLogout = true }
                MenuRow(Lucide.UserX, "Delete account", sub = "Permanently remove your account and data", tint = Nd.Danger) { confirmDelete = true }
            }
        }
    }
    if (confirmLogout) ConfirmDialog("Sign out?", "You'll need your email and password, or Google, to sign back in.", "Sign out",
        onDismiss = { confirmLogout = false }) { confirmLogout = false; scope.launch { c.repo.logout() } }
    if (confirmDelete) com.nudge.drive.ui.components.DeleteAccountDialog(c.repo, user?.email.orEmpty(), onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        c.toast("Your account was deleted")
    }
}

@Composable
fun MenuGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().softShadow(shape, 12.dp, 0.08f).clip(shape).background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(6.dp)) { content() }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, sub: String? = null, tint: Color = Nd.Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).bounceClick(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (tint == Nd.Danger) Nd.DangerSoft else Nd.Mist), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (tint == Nd.Danger) Nd.Danger else Nd.IrisDeep, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        if (tint != Nd.Danger) Icon(Lucide.ChevronRight, null, tint = Nd.Subtle)
    }
}
