package com.nudge.drive.ui.screens.onboarding

import com.composables.icons.lucide.*
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.googleSignIn
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.data.ImportStatus
import com.nudge.drive.ui.brand.AnimatedLogoTile
import com.nudge.drive.ui.brand.LogoTile
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.UsageRing
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.auth.DriveGlyph
import com.nudge.drive.ui.screens.auth.openCustomTab
import com.nudge.drive.ui.screens.backup.rememberBackupSetup
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun Throwable.msg() = (this as? ApiException)?.friendly ?: message ?: "Something went wrong"

// ================================================================== Email verification
@Composable
fun VerifyEmailScreen(c: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by c.session.user.collectAsState()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(45) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown-- } }
    // If they tap the link in the email instead, notice it.
    LaunchedEffect(Unit) { while (true) { delay(5000); runCatching { c.repo.me() } } }

    fun submit(value: String) {
        if (value.length != 6 || busy) return
        busy = true; error = null
        scope.launch {
            runCatching { c.repo.verifyEmail(value) }
                .onSuccess { c.toast("Email verified") }
                .onFailure {
                    error = it.msg(); code = ""
                    for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(55))
                }
            busy = false
        }
    }

    ScreenBackground(Modifier.background(Nd.AuthBackground)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(30.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                LogoTile(84.dp)
                Box(Modifier.offset(8.dp, 8.dp).size(38.dp).clip(CircleShape).background(Nd.Primary).border(3.dp, Nd.Surface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Mail, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Check your inbox", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.8).sp, color = Nd.Ink)
            Text(
                "We sent a 6-digit code to\n${user?.email ?: "your email"}",
                style = MaterialTheme.typography.bodyLarge, color = Nd.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            BasicTextField(
                code,
                { v -> val d = v.filter(Char::isDigit).take(6); code = d; error = null; if (d.length == 6) submit(d) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.focusRequester(focus).offset { IntOffset(shake.value.roundToInt(), 0) },
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(6) { i ->
                            val ch = code.getOrNull(i)?.toString() ?: ""
                            val active = i == code.length
                            val border by animateColorAsState(if (error != null) Nd.Danger else if (active) Nd.Primary else Nd.GlassBorder, label = "b")
                            val s by animateFloatAsState(if (ch.isNotEmpty()) 1f else 0.94f, spring(dampingRatio = 0.5f), label = "s")
                            Box(
                                Modifier.size(48.dp, 58.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(16.dp))
                                    .background(Nd.GlassStrong).border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(ch, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Nd.Ink) }
                        }
                    }
                },
            )
            AnimatedVisibility(error != null) { Text(error.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Nd.Danger, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Verify email", Modifier.fillMaxWidth(), enabled = code.length == 6, loading = busy) { submit(code) }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Open email app", Modifier.fillMaxWidth(), icon = Lucide.MailOpen) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { c.toast("No email app found") }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (cooldown > 0) "Resend code in ${cooldown}s" else "Didn't get it? Resend code",
                style = MaterialTheme.typography.labelLarge, color = if (cooldown > 0) Nd.Subtle else Nd.IrisDeep,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).bounceClick(enabled = cooldown == 0) {
                    scope.launch {
                        runCatching { c.repo.resendVerification() }.onSuccess { c.toast("New code sent"); cooldown = 45 }.onFailure { c.toast(it.msg()) }
                    }
                }.padding(10.dp),
            )
            Text("Check your spam folder too.", style = MaterialTheme.typography.bodySmall, color = Nd.Subtle)
            Spacer(Modifier.height(20.dp))
            Text("Use a different account", style = MaterialTheme.typography.labelMedium, color = Nd.Muted,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).bounceClick { scope.launch { c.repo.logout() } }.padding(10.dp))
        }
    }
}

// ================================================================== Onboarding
@Composable
fun OnboardingScreen(c: AppContainer, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by c.session.user.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var accounts by remember { mutableStateOf<List<ConnectedAccount>>(emptyList()) }
    var connecting by remember { mutableStateOf(false) }
    val imports = remember { mutableStateMapOf<String, ImportStatus>() }
    var backupOn by remember { mutableStateOf(c.backup.config.value.enabled) }
    val backupSetup = rememberBackupSetup(c) { backupOn = true; step = 4 }

    suspend fun refresh() { runCatching { c.repo.accounts() }.onSuccess { accounts = it } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { refresh() } }
    LaunchedEffect(Unit) { refresh(); c.refreshBus.collect { refresh() } }
    // Poll running imports.
    LaunchedEffect(imports.values.any { it.state == "running" }) {
        while (imports.values.any { it.state == "running" }) {
            delay(2000)
            imports.keys.toList().forEach { id -> runCatching { c.repo.importStatus(id) }.onSuccess { imports[id] = it } }
        }
    }

    ScreenBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // progress
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i ->
                    val col by animateColorAsState(if (i <= step) Nd.Primary else Nd.Lilac, label = "seg")
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(col))
                }
                if (step in 1..3) Text("Skip", style = MaterialTheme.typography.labelMedium, color = Nd.Muted,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).bounceClick { step = 4 }.padding(6.dp))
            }
            AnimatedContent(
                step, Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { it * dir / 3 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it * dir / 4 } + fadeOut(tween(150)))
                },
                label = "step",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        0 -> StepWelcome(user?.name?.substringBefore(' ') ?: "there") { step = 1 }
                        1 -> StepConnect(accounts, connecting, onConnect = {
                            connecting = true
                            scope.launch { runCatching { if (!c.googleSignIn(connect = true)) openCustomTab(ctx, c.repo.googleConnectUrl()) }.onFailure { c.toast(it.msg()) }; connecting = false }
                        }) { step = 2 }
                        2 -> StepImport(accounts.filter { it.isGoogle }, imports, onImport = { a ->
                            scope.launch { runCatching { c.repo.importDrive(a.id) }.onSuccess { imports[a.id] = it }.onFailure { c.toast(it.msg()) } }
                        }) { step = 3 }
                        3 -> StepBackup(backupOn, onSetup = backupSetup) { step = 4 }
                        else -> StepDone(accounts) { c.refreshBus.tryEmit(Unit); onFinish() }
                    }
                }
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
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColumnScope.StepWelcome(name: String, next: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    AnimatedLogoTile(110.dp, durationMs = 1100)
    Spacer(Modifier.height(28.dp))
    Title("Welcome, $name!", "Nudge Drive joins all your Google Drives into one big storage pool.")
    Feature(Lucide.Layers, "One pool for every drive", "15 GB free per Google account, added together")
    Feature(Lucide.CloudUpload, "Smart uploads", "Each file goes to the drive with the most room")
    Feature(Lucide.ShieldCheck, "Automatic backup", "Keep your phone's photos safe without thinking")
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
private fun ColumnScope.StepConnect(accounts: List<ConnectedAccount>, connecting: Boolean, onConnect: () -> Unit, next: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.size(120.dp).softShadow(RoundedCornerShape(34.dp), 24.dp, 0.25f).clip(RoundedCornerShape(34.dp)).background(Nd.Surface), contentAlignment = Alignment.Center) {
        DriveGlyph(Modifier.size(66.dp))
    }
    Spacer(Modifier.height(24.dp))
    Title("Connect your Google Drive", "Pick an account on Google's page and you'll come right back. Add as many as you like.")
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
private fun ColumnScope.StepBackup(on: Boolean, onSetup: () -> Unit, next: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(34.dp)).background(Nd.Mist), contentAlignment = Alignment.Center) {
        Icon(Lucide.Smartphone, null, tint = Nd.Accent, modifier = Modifier.size(52.dp))
        Box(Modifier.align(Alignment.BottomEnd).offset((-8).dp, (-8).dp).size(38.dp).clip(CircleShape).background(Nd.Primary), contentAlignment = Alignment.Center) {
            Icon(Lucide.CloudUpload, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
    Spacer(Modifier.height(24.dp))
    Title("Back up your phone automatically", "Choose a folder like Camera or WhatsApp Images. New files upload on their own, on Wi-Fi.")
    AnimatedVisibility(on, enter = scaleIn() + fadeIn()) {
        Row(Modifier.clip(CircleShape).background(Nd.SuccessSoft).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleCheck, null, tint = Nd.Success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Auto backup is on", style = MaterialTheme.typography.labelLarge, color = Nd.Success)
        }
    }
    if (on) Bottom("Continue", onPrimary = next)
    else Bottom("Choose a folder", onPrimary = onSetup, secondary = "Maybe later", onSecondary = next)
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
    Title("You're all set!", if (accounts.isEmpty()) "You can connect a drive any time from the Drives tab." else "${accounts.size} drive${if (accounts.size == 1) "" else "s"} connected · ${formatBytes(total)} in your pool.")
    Bottom("Go to my drive", onPrimary = finish)
}
