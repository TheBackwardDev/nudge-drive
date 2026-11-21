package com.nudge.drive.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nudge.drive.AppContainer
import com.nudge.drive.ui.brand.LogoTile
import com.nudge.drive.ui.components.ScreenBackground
import com.nudge.drive.ui.components.softShadow
import com.nudge.drive.ui.screens.auth.LoginScreen
import com.nudge.drive.ui.screens.auth.RegisterScreen
import com.nudge.drive.ui.screens.detail.FileDetailScreen
import com.nudge.drive.ui.screens.main.MainScreen
import com.nudge.drive.ui.screens.more.AboutScreen
import com.nudge.drive.ui.screens.more.ActivityScreen
import com.nudge.drive.ui.screens.more.ApiKeysScreen
import com.nudge.drive.ui.screens.more.SharedScreen
import com.nudge.drive.ui.screens.more.TrashScreen
import com.nudge.drive.ui.theme.Nd
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import com.nudge.drive.ui.components.bounceClick

object Routes {
    const val Login = "login"; const val Register = "register"; const val Main = "main"
    const val File = "file/{id}"; fun file(id: String) = "file/$id"
    const val View = "view/{id}"; fun view(id: String) = "view/$id"
    const val Edit = "edit/{id}"; fun edit(id: String) = "edit/$id"
    const val DeviceFolder = "device/{bucket}"; fun deviceFolder(bucket: Long) = "device/$bucket"
    const val Trash = "trash"; const val Shared = "shared"; const val Activity = "activity"; const val ApiKeys = "apikeys"; const val About = "about"
    const val Notifications = "notifications"; const val Settings = "settings"; const val Backup = "backup"
    const val Folders = "folders"; const val Nearby = "nearby"
    const val FreeUp = "freeup/{id}"; fun freeUp(accountId: String) = "freeup/$accountId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(c: AppContainer, loggedIn: Boolean?) {
    val user by c.session.user.collectAsState()
    val onboarded by c.prefs.onboardedUsers.collectAsState()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val stage = when {
        loggedIn == null -> Stage.Loading
        loggedIn == false -> Stage.Auth
        user?.emailVerified == false -> Stage.Verify
        user != null && user!!.id !in onboarded -> Stage.Onboarding
        else -> Stage.App
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = { (fadeIn(tween(420)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(250)) },
            label = "stage",
        ) { st ->
            when (st) {
                Stage.Loading -> ScreenBackground { }
                Stage.Auth -> AuthGraph(c)
                Stage.Verify -> com.nudge.drive.ui.screens.onboarding.VerifyEmailScreen(c)
                Stage.Onboarding -> com.nudge.drive.ui.screens.onboarding.OnboardingScreen(c) { user?.id?.let(c.prefs::markOnboarded) }
                Stage.App -> AppGraph(c)
            }
        }
        androidx.compose.animation.AnimatedVisibility(!splashDone, enter = fadeIn(), exit = fadeOut(tween(450)) + androidx.compose.animation.scaleOut(targetScale = 1.08f)) {
            com.nudge.drive.ui.brand.AnimatedSplash { splashDone = true }
        }
        ToastHost(c, Modifier.align(Alignment.TopCenter))
    }
}

private enum class Stage { Loading, Auth, Verify, Onboarding, App }

@Composable
private fun AnimatedVisibilityScope.Scoped(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavScope provides this, content = content)

@Composable
private fun AuthGraph(c: AppContainer) {
    val nav = rememberNavController()
    NavHost(
        nav, Routes.Login,
        enterTransition = { slideInHorizontally(offsetSpring) { it / 3 } + fadeIn(tween(260)) },
        exitTransition = { slideOutHorizontally(offsetSpring) { -it / 8 } + fadeOut(tween(180)) },
        popEnterTransition = { slideInHorizontally(offsetSpring) { -it / 8 } + fadeIn(tween(260)) },
        popExitTransition = { slideOutHorizontally(offsetSpring) { it / 3 } + fadeOut(tween(180)) },
    ) {
        composable(Routes.Login) { LoginScreen(c, onRegister = { nav.navigate(Routes.Register) }) }
        composable(Routes.Register) { RegisterScreen(c, onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppGraph(c: AppContainer) {
    val nav = rememberNavController()
    // Taps on Android notifications land here (file or inbox).
    val pending by c.pendingRoute.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { route ->
            c.pendingRoute.value = null
            when {
                route.startsWith("tab:") || route.startsWith("sheet:") -> {
                    runCatching { nav.popBackStack(Routes.Main, inclusive = false) }
                    c.pendingTab.value = route.substringAfter(':')
                }
                route == "system:downloads" -> runCatching {
                    nav.context.startActivity(android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                // Don't stack a second copy of the screen we're already on (e.g. Nearby opened twice).
                nav.currentDestination?.route != route -> runCatching { nav.navigate(route) { launchSingleTop = true } }
            }
        }
    }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                nav, Routes.Main,
                enterTransition = { slideInHorizontally(offsetSpring) { it / 3 } + fadeIn(tween(280)) },
                exitTransition = { slideOutHorizontally(offsetSpring) { -it / 10 } + fadeOut(tween(200)) },
                popEnterTransition = { slideInHorizontally(offsetSpring) { -it / 10 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutHorizontally(offsetSpring) { it / 3 } + fadeOut(tween(200)) },
            ) {
                composable(Routes.Main) { Scoped { MainScreen(c, nav) } }
                composable(
                    Routes.File,
                    enterTransition = { fadeIn(tween(300)) },
                    popExitTransition = { fadeOut(tween(250)) },
                ) { e -> Scoped { FileDetailScreen(c, e.arguments?.getString("id").orEmpty(), nav) } }
                composable(
                    Routes.View,
                    enterTransition = { fadeIn(tween(220)) + androidx.compose.animation.scaleIn(tween(260), initialScale = 0.94f) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(200)) + androidx.compose.animation.scaleOut(tween(220), targetScale = 0.94f) },
                ) { e -> Scoped { com.nudge.drive.ui.screens.viewer.ViewerScreen(c, e.arguments?.getString("id").orEmpty(), nav) } }
                composable(
                    Routes.Edit,
                    enterTransition = { slideInVertically(offsetSpring) { it / 4 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutVertically(offsetSpring) { it / 4 } + fadeOut(tween(200)) },
                ) { e -> Scoped { com.nudge.drive.ui.screens.viewer.EditorScreen(c, e.arguments?.getString("id").orEmpty(), nav) } }
                composable(Routes.DeviceFolder) { e -> Scoped { com.nudge.drive.ui.screens.backup.DeviceFolderScreen(c, e.arguments?.getString("bucket")?.toLongOrNull() ?: 0L, nav) } }
                composable(Routes.Trash) { Scoped { TrashScreen(c, nav) } }
                composable(Routes.Shared) { Scoped { SharedScreen(c, nav) } }
                composable(Routes.Activity) { Scoped { ActivityScreen(c, nav) } }
                composable(Routes.ApiKeys) { Scoped { ApiKeysScreen(c, nav) } }
                composable(Routes.About) { Scoped { AboutScreen(c, nav) } }
                composable(Routes.Notifications) { Scoped { com.nudge.drive.ui.screens.more.NotificationsScreen(c, nav) } }
                composable(Routes.Settings) { Scoped { com.nudge.drive.ui.screens.more.SettingsScreen(c, nav) } }
                composable(Routes.Backup) { Scoped { com.nudge.drive.ui.screens.backup.BackupScreen(c, nav) } }
                composable(Routes.Folders) { Scoped { com.nudge.drive.ui.screens.folders.AllFoldersScreen(c, nav) } }
                composable(Routes.FreeUp) { e -> Scoped { com.nudge.drive.ui.screens.drives.FreeUpScreen(c, nav, e.arguments?.getString("id") ?: "") } }
                composable(Routes.Nearby) { Scoped { com.nudge.drive.ui.screens.nearby.NearbyScreen(c, nav) } }
            }
            // Files shared in from other apps, and "getting it ready to share" progress.
            com.nudge.drive.ui.screens.share.SaveSharedSheet(c)
            com.nudge.drive.ui.screens.share.SharePrepDialog(c)
        }
    }
}

/** Glass toast that drops in from the top. */
@Composable
private fun ToastHost(c: AppContainer, modifier: Modifier = Modifier) {
    var msg by remember { mutableStateOf<com.nudge.drive.ToastMsg?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        c.toasts.collectLatest {
            msg = it; visible = true
            delay(if (it.action != null) 4500 else 2600); visible = false
        }
    }
    AnimatedVisibility(
        visible, modifier.statusBarsPadding().padding(top = 8.dp),
        enter = slideInVertically(navSpring()) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(250)) { -it * 2 } + fadeOut(),
    ) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.widthIn(max = 340.dp).softShadow(shape, 20.dp, 0.25f).clip(shape)
                .background(Nd.GlassStrong).border(1.dp, Nd.GlassBorder, shape).padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoTile(28.dp)
            Spacer(Modifier.width(10.dp))
            Text(msg?.text.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Nd.Ink, modifier = Modifier.widthIn(max = 220.dp))
            msg?.actionLabel?.let { label ->
                Spacer(Modifier.width(12.dp))
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = Nd.OnInverse,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Nd.Inverse)
                        .bounceClick { msg?.action?.invoke(); visible = false }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
