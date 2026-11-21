package com.nudge.drive

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nudge.drive.data.ApiException
import com.nudge.drive.ui.nav.AppRoot
import com.nudge.drive.ui.nav.Routes
import com.nudge.drive.data.NoticeType
import com.nudge.drive.data.NotificationCenter
import com.nudge.drive.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import com.nudge.drive.ui.theme.NudgeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as NudgeApp).container }
    private val googleNative = com.nudge.drive.data.GoogleNative(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // No waiting on the system splash: the animated splash starts on the very first frame.
        splash.setKeepOnScreenCondition { false }
        lifecycleScope.launch {
            container.session.load()
            container.prefs.migrateOnboarding(container.session.user.value?.id)
            container.backup.schedule()
        }
        container.googleNative = googleNative
        // Only a fresh launch brings a new intent. After rotation / a theme change (or reopening from
        // Recents) it's the same one again: replaying it would re-share files and re-run the sign-in exchange.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) handleIntent(intent)
        setIntent(Intent())

        setContent {
            val theme by container.prefs.theme.collectAsState()
            val dark = when (theme) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
            NudgeTheme(dark) {
                val loggedIn by container.session.loggedIn.collectAsState()
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    AppRoot(container, loggedIn)
                    com.nudge.drive.ui.theme.ThemeRevealOverlay()
                }
            }
        }
    }

    override fun onDestroy() {
        if (container.googleNative === googleNative) container.googleNative = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(NotificationCenter.EXTRA_OPEN_NOTICES, false)) {
            // Go straight to what the notification is about (file, drives, transfers…), else the inbox.
            intent.getLongExtra(NotificationCenter.EXTRA_NOTICE_ID, -1L).takeIf { it >= 0 }?.let(container.notices::markRead)
            container.pendingRoute.value = intent.getStringExtra(NotificationCenter.EXTRA_ROUTE)
                ?: intent.getStringExtra(NotificationCenter.EXTRA_FILE_ID)?.let { Routes.view(it) } ?: Routes.Notifications
            intent.removeExtra(NotificationCenter.EXTRA_ROUTE)
            intent.removeExtra(NotificationCenter.EXTRA_OPEN_NOTICES)
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> handleLink(intent.data)
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (intent.action == Intent.ACTION_SEND) listOfNotNull(intent.streamUri()) else intent.streamUris()
                if (uris.isEmpty()) { container.toast("Nothing to share was found."); return }
                if (intent.component?.className?.endsWith("NearbyToNudge") == true) {
                    // Read names/sizes now while we hold the share grant, then open Nearby with them queued.
                    lifecycleScope.launch {
                        container.nearby.outgoing.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            uris.map { com.nudge.drive.data.NearbyShare.describe(this@MainActivity, it) }
                        }
                        container.pendingRoute.value = Routes.Nearby
                    }
                } else {
                    // Shown as "Save to Nudge Drive" with a folder picker once the user is signed in.
                    container.incomingShares.value = uris
                }
            }
        }
    }

    /** Google OAuth finishes on nudge.qd.je; App Links bring those redirects back here. */
    private fun handleLink(uri: Uri?) {
        uri ?: return
        when (uri.path) {
            "/google-auth" -> {
                val token = uri.getQueryParameter("token")
                if (token == null) { container.toast("Google sign-in was cancelled or failed."); return }
                lifecycleScope.launch {
                    container.session.loggedIn.filterNotNull().first()
                    runCatching { container.repo.exchangeGoogle(token) }
                        .onSuccess { container.toast("Signed in as ${it.user.name}") }
                        .onFailure { container.toast((it as? ApiException)?.friendly ?: "Google sign-in failed.") }
                }
            }
            "/google-connected" -> {
                if (uri.getQueryParameter("status") == "success") {
                    container.toast("Google Drive connected")
                    container.notices.post(NoticeType.Drive, "Google Drive connected", "Its free space is now part of your storage pool.", system = false)
                } else container.toast("Couldn't connect that Google account.")
                container.refreshBus.tryEmit(Unit)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}
