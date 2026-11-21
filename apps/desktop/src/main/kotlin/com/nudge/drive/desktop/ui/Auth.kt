package com.nudge.drive.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.MailCheck
import com.composables.icons.lucide.MailOpen
import com.composables.icons.lucide.User
import com.nudge.drive.data.ApiException
import com.nudge.drive.desktop.AppState
import com.nudge.drive.desktop.data.GoogleLogin
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.ui.brand.AnimatedSplash
import com.nudge.drive.ui.brand.LogoTile
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.SecondaryButton
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Splash(onDone: () -> Unit) = AnimatedSplash(onDone)

/** Same shape check as Android's Patterns.EMAIL_ADDRESS. */
private val EmailPattern = Regex("""[a-zA-Z0-9+._%\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\-]{0,64}(\.[a-zA-Z0-9][a-zA-Z0-9\-]{0,25})+""")

private fun Throwable.userMessage() = (this as? ApiException)?.friendly ?: message ?: "Something went wrong"

/** Sign in / create account, then email verification when the server asks for it. */
@Composable
fun AuthFlow(app: AppState) {
    var register by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize()) {
        // Brand side
        Box(Modifier.weight(1.1f).fillMaxHeight().background(Nd.AuthBackground)) { BrandArt() }
        // Form side
        Box(Modifier.weight(1f).fillMaxHeight().background(Nd.Surface), contentAlignment = Alignment.Center) {
            AnimatedContent(register, transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(220)) { -it * dir / 5 } + fadeOut(tween(160)))
            }, label = "auth") { r ->
                Column(Modifier.widthIn(max = 400.dp).padding(32.dp)) {
                    if (r) RegisterForm(app) { register = false } else LoginForm(app) { register = true }
                }
            }
        }
    }
}

/** What the sign-in form is doing; the Google one waits on the browser and can be cancelled. */
private sealed interface SignIn {
    data object Idle : SignIn
    data object Password : SignIn
    data class Google(val job: Job) : SignIn
}

@Composable
private fun LoginForm(app: AppState, onRegister: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    // One sign-in at a time: password or Google, never both (Enter or a second click while one runs does nothing).
    var signingIn by remember { mutableStateOf<SignIn>(SignIn.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (signingIn != SignIn.Idle) return
        if (email.isBlank() || password.isBlank()) { error = "Enter your email and password."; return }
        signingIn = SignIn.Password; error = null
        scope.launch { runCatching { app.repo.login(email, password) }.onFailure { error = it.userMessage(); signingIn = SignIn.Idle } }
    }
    Title("Welcome back", "Sign in to reach all your drives in one place")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillField(email, { email = it; error = null }, "Email", leading = Lucide.Mail,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        PillField(password, { password = it; error = null }, "Password", leading = Lucide.Lock,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailing = { EyeToggle(show) { show = !show } })
    }
    ErrorLine(error)
    Spacer(Modifier.height(18.dp))
    PrimaryButton("Sign in", Modifier.fillMaxWidth(), loading = signingIn == SignIn.Password) { submit() }
    OrDivider()
    GoogleButton(signingIn is SignIn.Google) {
        if (signingIn != SignIn.Idle) return@GoogleButton
        error = null
        // Registered before it starts, so Cancel always has something to stop.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                GoogleLogin.signIn(app.api, app.repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                signingIn = SignIn.Idle
            }
            app.bringToFront()
        }
        signingIn = SignIn.Google(job)
        job.start()
    }
    (signingIn as? SignIn.Google)?.let { g ->
        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Nd.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).bounceClick { g.job.cancel() }.padding(6.dp))
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("New here? ", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
        Text("Create an account", style = MaterialTheme.typography.labelLarge, color = Nd.IrisDeep, modifier = Modifier.bounceClick(onClick = onRegister))
    }
}

@Composable
private fun RegisterForm(app: AppState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (loading) return
        error = when {
            name.trim().length < 2 -> "Your name needs at least 2 letters."
            !EmailPattern.matches(email.trim()) -> "That email doesn't look right."
            password.length < 8 -> "Use at least 8 characters for the password."
            else -> null
        }
        if (error != null) return
        loading = true
        scope.launch { runCatching { app.repo.register(name.trim(), email.trim(), password) }.onFailure { error = it.userMessage(); loading = false } }
    }
    Title("Create your account", "One home for every drive you own")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillField(name, { name = it; error = null }, "Your name", leading = Lucide.User, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        PillField(email, { email = it; error = null }, "Email", leading = Lucide.Mail, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        PillField(password, { password = it; error = null }, "Password (8+ characters)", leading = Lucide.Lock,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailing = { EyeToggle(show) { show = !show } })
    }
    ErrorLine(error)
    Spacer(Modifier.height(18.dp))
    PrimaryButton("Create account", Modifier.fillMaxWidth(), loading = loading) { submit() }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("Already have an account? ", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
        Text("Sign in", style = MaterialTheme.typography.labelLarge, color = Nd.IrisDeep, modifier = Modifier.bounceClick(onClick = onBack))
    }
}

/** Shown inside the app while the account's email isn't verified yet. */
@Composable
fun VerifyEmail(app: AppState) {
    val scope = rememberCoroutineScope()
    val user by app.session.user.collectAsState()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableStateOf(0) }
    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown-- } }
    // Also notice when the link in the email was clicked.
    LaunchedEffect(Unit) { while (true) { delay(5000); runCatching { app.repo.me() } } }
    fun submit() {
        if (code.length != 6) { error = "Enter the 6-digit code from the email."; return }
        loading = true; error = null
        scope.launch { runCatching { app.repo.verifyEmail(code) }.onFailure { error = it.userMessage() }; loading = false }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Nd.soft(Nd.Iris)), contentAlignment = Alignment.Center) {
                Icon(Lucide.MailCheck, null, tint = Nd.Iris, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(18.dp))
            Title("Check your email", "We sent a 6-digit code to ${user?.email ?: "your email"}")
            PillField(code, { v -> code = v.filter { it.isDigit() }.take(6); error = null }, "6-digit code",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
            ErrorLine(error)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Verify", Modifier.fillMaxWidth(), loading = loading) { submit() }
            Spacer(Modifier.height(10.dp))
            val webmail = remember(user?.email) { webmailFor(user?.email) }
            SecondaryButton(webmail?.first ?: "Open email app", Modifier.fillMaxWidth(), icon = Lucide.MailOpen) {
                if (webmail != null) Platform.openInBrowser(webmail.second)
                else if (!openMailApp()) app.toast("No email app found")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (cooldown > 0) "Send a new code in ${cooldown}s" else "Send a new code",
                style = MaterialTheme.typography.labelLarge, color = if (cooldown > 0) Nd.Subtle else Nd.IrisDeep,
                modifier = Modifier.bounceClick(enabled = cooldown == 0) {
                    scope.launch { runCatching { app.repo.resendVerification() }.onSuccess { app.toast("New code sent"); cooldown = 45 }.onFailure { app.fail(it, "Couldn't send a code") } }
                },
            )
            Spacer(Modifier.height(10.dp))
            Text("Use a different account", style = MaterialTheme.typography.labelMedium, color = Nd.Muted,
                modifier = Modifier.bounceClick { scope.launch { app.logout() } })
        }
    }
}

/** Webmail for the big providers ("Open Gmail" beats a blank compose window); null = use the mail app. */
private fun webmailFor(email: String?): Pair<String, String>? = when (email?.substringAfterLast('@')?.lowercase()) {
    "gmail.com", "googlemail.com" -> "Open Gmail" to "https://mail.google.com/"
    "outlook.com", "hotmail.com", "live.com", "msn.com" -> "Open Outlook" to "https://outlook.live.com/mail/"
    "yahoo.com", "ymail.com" -> "Open Yahoo Mail" to "https://mail.yahoo.com/"
    "icloud.com", "me.com", "mac.com" -> "Open iCloud Mail" to "https://www.icloud.com/mail"
    "proton.me", "protonmail.com" -> "Open Proton Mail" to "https://mail.proton.me/"
    else -> null
}

/** Opens the default mail app; false when there isn't one. */
private fun openMailApp(): Boolean = runCatching {
    val d = java.awt.Desktop.getDesktop()
    if (java.awt.Desktop.isDesktopSupported() && d.isSupported(java.awt.Desktop.Action.MAIL)) { d.mail(); true } else false
}.getOrDefault(false)

@Composable
private fun Title(title: String, sub: String) {
    Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.8).sp, color = Nd.Ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 22.dp), textAlign = TextAlign.Center)
}

@Composable
private fun EyeToggle(show: Boolean, onToggle: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).bounceClick(onClick = onToggle), contentAlignment = Alignment.Center) {
        Icon(if (show) Lucide.EyeOff else Lucide.Eye, if (show) "Hide password" else "Show password", tint = Nd.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ErrorLine(error: String?) {
    AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(error.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Nd.Danger, modifier = Modifier.padding(top = 10.dp, start = 8.dp))
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Nd.Line))
        Text("or", style = MaterialTheme.typography.labelMedium, color = Nd.Subtle, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Nd.Line))
    }
}

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).bounceClick(enabled = !loading, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Nd.Iris, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Finish signing in in your browser…", style = MaterialTheme.typography.labelLarge, color = Nd.Muted)
        } else {
            Text("G", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                style = TextStyle(brush = Brush.sweepGradient(listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFEA4335)))))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google", style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
        }
    }
}

@Composable
private fun BoxScope.BrandArt() {
    val t = rememberInfiniteTransition(label = "float")
    val a by t.animateFloat(-8f, 8f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(6f, -9f, infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Row(Modifier.padding(start = 36.dp, top = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(40.dp)
        Spacer(Modifier.width(12.dp))
        Text("Nudge Drive", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = (-0.5).sp, color = Nd.Ink)
    }
    Box(Modifier.align(Alignment.Center).size(380.dp, 300.dp)) {
        TypeTile("JPG", Nd.Image, Color.White, Modifier.align(Alignment.CenterStart).offset(y = (a + 20).dp).rotate(-14f), 100.dp)
        TypeTile("MP4", Nd.Video, Color(0xFF1F1300), Modifier.align(Alignment.TopEnd).offset(y = b.dp).rotate(12f), 100.dp)
        Box(
            Modifier.align(Alignment.Center).offset(y = (b * 0.6f - 10).dp).rotate(-6f).size(150.dp)
                .softShadow(RoundedCornerShape(38.dp), 26.dp, 0.3f).clip(RoundedCornerShape(38.dp)).background(Nd.Surface),
            contentAlignment = Alignment.Center,
        ) { DriveGlyph(Modifier.size(84.dp)) }
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = (a * 0.8f).dp).rotate(9f).size(114.dp, 84.dp)
                .softShadow(RoundedCornerShape(20.dp), 18.dp, 0.25f).clip(RoundedCornerShape(20.dp)).background(Nd.Surface),
            contentAlignment = Alignment.Center,
        ) { Text("PDF", fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Nd.Pdf) }
    }
    Column(Modifier.align(Alignment.BottomStart).padding(36.dp)) {
        Text("All your drives,\none place.", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp, color = Nd.Ink)
        Text("Pool every Google Drive and S3 bucket into one big, fast storage.", style = MaterialTheme.typography.bodyLarge, color = Nd.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TypeTile(label: String, bg: Color, fg: Color, modifier: Modifier, size: Dp) {
    Column(
        modifier.size(size, size * 1.15f).softShadow(RoundedCornerShape(20.dp), 18.dp, 0.25f).clip(RoundedCornerShape(20.dp)).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(label, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = fg)
        Spacer(Modifier.height(6.dp)); Box(Modifier.size(50.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.55f)))
        Spacer(Modifier.height(6.dp)); Box(Modifier.size(32.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.55f)))
    }
}

/** Simplified three-colour Drive triangle. */
@Composable
fun DriveGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        fun p(vararg pts: Float) = Path().apply {
            moveTo(pts[0] * s, pts[1] * s); var i = 2
            while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }; close()
        }
        drawPath(p(8.6f, 3f, 15.4f, 3f, 22f, 14.4f, 15.2f, 14.4f), Color(0xFFFFC107))
        drawPath(p(8.6f, 3f, 2f, 14.4f, 5.4f, 20.3f, 12f, 8.9f), Color(0xFF1FA463))
        drawPath(p(5.4f, 20.3f, 18.6f, 20.3f, 22f, 14.4f, 8.8f, 14.4f), Color(0xFF4285F4))
    }
}
