package com.nudge.drive.ui.screens.auth

import com.composables.icons.lucide.*
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.drive.AppContainer
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.googleSignIn
import com.nudge.drive.ui.brand.LogoTile
import com.nudge.drive.ui.components.GlassIconButton
import com.nudge.drive.ui.components.PillField
import com.nudge.drive.ui.components.PrimaryButton
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.bounceClick
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.theme.Nd
import com.nudge.drive.ui.theme.Sora
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun openCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFD9CEFF.toInt()).build())
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(url))
}

private fun Throwable.userMessage() = (this as? ApiException)?.friendly ?: message ?: "Something went wrong"

private enum class SignIn { Password, Google }

@Composable
fun LoginScreen(c: AppContainer, onRegister: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    // One sign-in at a time: password or Google, never both (Enter or a second tap while one runs does nothing).
    var signingIn by remember { mutableStateOf<SignIn?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }

    fun submit() {
        if (signingIn != null) return
        if (email.isBlank() || password.isBlank()) { error = "Enter your email and password."; return }
        focus.clearFocus(); signingIn = SignIn.Password; error = null
        scope.launch {
            runCatching { c.repo.login(email, password) }.onFailure {
                error = it.userMessage(); signingIn = null
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(55))
            }
        }
    }

    AuthScaffold(hero = { FloatingTiles() }) {
        Text("Welcome back", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.8).sp, color = Nd.Ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Sign in to reach all your drives in one place", style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp), textAlign = TextAlign.Center)
        Column(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillField(email, { email = it; error = null }, "Email", leading = Lucide.Mail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            PillField(
                password, { password = it; error = null }, "Password", leading = Lucide.Lock,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailing = { EyeToggle(show) { show = !show } },
            )
        }
        ErrorLine(error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Sign in", Modifier.fillMaxWidth(), loading = signingIn == SignIn.Password) { submit() }
        OrDivider()
        GoogleButton(signingIn == SignIn.Google) {
            if (signingIn != null) return@GoogleButton
            signingIn = SignIn.Google; error = null
            scope.launch {
                // The phone's own Google account picker first; the browser only if that isn't available.
                runCatching { if (!c.googleSignIn(connect = false)) openCustomTab(ctx, c.repo.googleLoginUrl()) }
                    .onFailure { error = it.userMessage() }
                signingIn = null
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            buildAnnotatedString {
                append("New here? ")
                withStyle(SpanStyle(color = Nd.IrisDeep, fontWeight = FontWeight.Bold)) { append("Create an account") }
            },
            style = MaterialTheme.typography.bodyMedium, color = Nd.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onRegister).padding(8.dp),
        )
    }
}

@Composable
fun RegisterScreen(c: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading) return
        error = when {
            name.trim().length < 2 -> "Your name needs at least 2 letters."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "That email doesn't look right."
            password.length < 8 -> "Use at least 8 characters for the password."
            else -> null
        }
        if (error != null) return
        focus.clearFocus(); loading = true
        scope.launch { runCatching { c.repo.register(name, email, password) }.onFailure { error = it.userMessage(); loading = false } }
    }

    AuthScaffold(
        hero = {
            Box(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                GlassIconButton(Lucide.ArrowLeft, "Back", onClick = onBack)
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoTile(84.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("One pool for every drive", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Nd.Ink)
                }
            }
        },
        sheetHeight = 0.66f,
    ) {
        Text("Create your account", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.8).sp, color = Nd.Ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillField(name, { name = it; error = null }, "Your name", leading = Lucide.UserRound, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            PillField(email, { email = it; error = null }, "Email", leading = Lucide.Mail, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            PillField(
                password, { password = it; error = null }, "Password (8+ characters)", leading = Lucide.Lock,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailing = { EyeToggle(show) { show = !show } },
            )
        }
        ErrorLine(error)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Create account", Modifier.fillMaxWidth(), loading = loading) { submit() }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        fun open(path: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("${com.nudge.drive.BuildConfig.WEB_BASE}$path"))) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("By creating an account you agree to the ", style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Terms", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, modifier = Modifier.clickable { open("/terms") }.padding(4.dp))
            Text("and", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(vertical = 4.dp))
            Text("Privacy policy", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep, modifier = Modifier.clickable { open("/privacy") }.padding(4.dp))
        }
    }
}

/** Gradient top + a white sheet that springs up from the bottom. */
@Composable
private fun AuthScaffold(hero: @Composable BoxScope.() -> Unit, sheetHeight: Float = 0.6f, content: @Composable () -> Unit) {
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 220f)) }
    ScreenBackground(Modifier.background(Nd.AuthBackground)) {
        Box(Modifier.fillMaxWidth().fillMaxSize(1f - sheetHeight + 0.06f), content = hero)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxSize(sheetHeight)
                .graphicsLayer { translationY = rise.value * size.height }
                .softShadow(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp), 30.dp, 0.18f)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(Nd.GlassStrong)
                .border(1.dp, Nd.GlassBorder, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) { content() }
    }
}

@Composable
private fun EyeToggle(show: Boolean, onToggle: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).bounceClick(onClick = onToggle), contentAlignment = Alignment.Center) {
        Icon(if (show) Lucide.EyeOff else Lucide.Eye, if (show) "Hide password" else "Show password", tint = Nd.Muted)
    }
}

@Composable
private fun ErrorLine(error: String?) {
    AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(error.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Nd.Danger, modifier = Modifier.padding(top = 10.dp, start = 8.dp))
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Nd.Line))
        Text("or", style = MaterialTheme.typography.labelMedium, color = Nd.Subtle, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Nd.Line))
    }
}

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(shape).background(Nd.Surface).border(1.dp, Nd.CardBorder, shape).bounceClick(enabled = !loading, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = Nd.Iris, strokeWidth = 2.dp)
        else {
            Text(
                "G", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.sweepGradient(listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFEA4335)))),
            )
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google", style = MaterialTheme.typography.labelLarge, color = Nd.Ink)
        }
    }
}

/** File tiles bobbing gently around a Google Drive tile. */
@Composable
private fun BoxScope.FloatingTiles() {
    val t = rememberInfiniteTransition(label = "float")
    val a by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(5f, -7f, infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Row(Modifier.statusBarsPadding().padding(start = 24.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(36.dp)
        Spacer(Modifier.width(10.dp))
        Text("Nudge Drive", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.5).sp, color = Nd.Ink)
    }
    Box(Modifier.align(Alignment.Center).padding(top = 50.dp).size(300.dp, 220.dp)) {
        TypeTile("JPG", Nd.Image, Color.White, Modifier.align(Alignment.CenterStart).offset(y = (a + 20).dp).rotate(-14f), 82.dp)
        TypeTile("MP4", Nd.Video, Color(0xFF1F1300), Modifier.align(Alignment.TopEnd).offset(y = b.dp).rotate(12f), 82.dp)
        Box(
            Modifier.align(Alignment.Center).offset(y = (b * 0.6f - 10).dp).rotate(-6f).size(126.dp)
                .softShadow(RoundedCornerShape(32.dp), 26.dp, 0.3f).clip(RoundedCornerShape(32.dp)).background(Nd.Surface),
            contentAlignment = Alignment.Center,
        ) { DriveGlyph(Modifier.size(70.dp)) }
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = (a * 0.8f).dp).rotate(9f).size(94.dp, 70.dp)
                .softShadow(RoundedCornerShape(18.dp), 18.dp, 0.25f).clip(RoundedCornerShape(18.dp)).background(Nd.Surface),
            contentAlignment = Alignment.Center,
        ) { Text("PDF", fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, color = Nd.Pdf) }
    }
}

@Composable
private fun TypeTile(label: String, bg: Color, fg: Color, modifier: Modifier, size: Dp) {
    Column(
        modifier.size(size, size * 1.15f).softShadow(RoundedCornerShape(18.dp), 18.dp, 0.25f).clip(RoundedCornerShape(18.dp)).background(bg).padding(12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(label, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = fg)
        Spacer(Modifier.height(5.dp)); Box(Modifier.size(42.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.55f)))
        Spacer(Modifier.height(5.dp)); Box(Modifier.size(28.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.55f)))
    }
}

/** Simplified three-colour Drive triangle. */
@Composable
fun DriveGlyph(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val s = size.minDimension / 24f
        fun p(vararg pts: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0] * s, pts[1] * s); var i = 2
            while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }; close()
        }
        drawPath(p(8.6f, 3f, 15.4f, 3f, 22f, 14.4f, 15.2f, 14.4f), Color(0xFFFFC107))
        drawPath(p(8.6f, 3f, 2f, 14.4f, 5.4f, 20.3f, 12f, 8.9f), Color(0xFF1FA463))
        drawPath(p(5.4f, 20.3f, 18.6f, 20.3f, 22f, 14.4f, 8.8f, 14.4f), Color(0xFF4285F4))
    }
}
