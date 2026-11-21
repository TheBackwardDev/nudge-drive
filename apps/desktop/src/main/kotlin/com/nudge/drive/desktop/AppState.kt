package com.nudge.drive.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nudge.drive.Endpoints
import com.nudge.drive.data.ApiClient
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Repository
import com.nudge.drive.data.SessionStore
import com.nudge.drive.desktop.data.DesktopTransfers
import com.nudge.drive.desktop.data.FileSecureStore
import com.nudge.drive.desktop.data.FolderBackup
import com.nudge.drive.desktop.data.Inbox
import com.nudge.drive.desktop.data.NoticeRoute
import com.nudge.drive.desktop.data.NoticeType
import com.nudge.drive.data.ConnectedAccount
import com.nudge.drive.data.StorageSummary
import com.nudge.drive.util.formatBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import com.nudge.drive.desktop.data.Platform
import com.nudge.drive.desktop.data.SettingsStore
import com.nudge.drive.desktop.data.TransferKind
import com.nudge.drive.desktop.data.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File

/** Sidebar destinations. */
sealed interface Page {
    data object Home : Page
    data class Files(val folderId: String? = null, val trail: List<Crumb> = emptyList()) : Page
    data object Photos : Page
    data object Recent : Page
    data object Folders : Page
    data object Shared : Page
    data object Nearby : Page
    data object Activity : Page
    data object ApiKeys : Page
    data object Drives : Page
    data object Backup : Page
    data object Trash : Page
    data object Inbox : Page
    data object Settings : Page
    data class Search(val query: String) : Page
    /** Free up space on a full Google account. */
    data class FreeUp(val accountId: String) : Page
}

data class Crumb(val id: String, val name: String)

/** A short message at the bottom of the window, optionally with one action such as Undo. */
data class ToastMsg(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** What's open in the full-window viewer: a file and the list it can step through. */
data class ViewerState(val list: List<FileItem>, val index: Int)

/** Everything the desktop app shares: services + navigation. Lives for the whole process. */
class AppState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore()
    val session = SessionStore(FileSecureStore())
    val api = ApiClient(session, Endpoints.API_BASE, Platform.userAgent)
    val repo = Repository(api, Endpoints.WEB_BASE)
    val transfers = DesktopTransfers(api, repo, settings, scope)
    val backup = FolderBackup(repo, transfers, settings, scope)
    val inbox = Inbox()
    val nearby = com.nudge.drive.desktop.data.DesktopNearby(scope, settings) { session.user.value?.name }

    // ---- navigation
    var page by mutableStateOf<Page>(Page.Home)
        private set
    private val history = ArrayDeque<Page>()
    var viewer by mutableStateOf<ViewerState?>(null)
    var editorFor by mutableStateOf<FileItem?>(null)
    var showTransfers by mutableStateOf(false)

    fun go(p: Page) { if (p != page) { history.addLast(page); if (history.size > 50) history.removeFirst(); page = p } }
    fun back(): Boolean { val p = history.removeLastOrNull() ?: return false; page = p; return true }
    val canGoBack get() = history.isNotEmpty()

    fun openFolder(id: String?, trail: List<Crumb>) = go(Page.Files(id, trail))
    fun openFile(file: FileItem, list: List<FileItem>) {
        val idx = list.indexOfFirst { it.id == file.id }
        viewer = if (idx >= 0) ViewerState(list, idx) else ViewerState(listOf(file), 0)
    }

    // ---- snackbars
    val toasts = MutableSharedFlow<ToastMsg>(extraBufferCapacity = 8)
    fun toast(msg: String) { toasts.tryEmit(ToastMsg(msg)) }
    /** A toast with one action button, such as "Undo". */
    fun toast(msg: String, actionLabel: String, action: () -> Unit) { toasts.tryEmit(ToastMsg(msg, actionLabel, action)) }
    fun fail(e: Throwable, fallback: String) = toast((e as? ApiException)?.friendly ?: e.message?.takeIf { it.length < 140 } ?: fallback)

    /** Bumped when files change (upload, delete, move…) so open pages reload. */
    val changes = MutableStateFlow(0)
    fun changed() { changes.value++ }

    /** Drives whose "Import all" is being watched app-wide; one watcher each (no duplicate "Imported" notices). */
    val importing = mutableStateListOf<String>()

    /** Files dropped on the window / passed by "Send to" before a folder is chosen. */
    val incoming = mutableStateListOf<File>()

    var darkSystem by mutableStateOf(Platform.systemDark())

    // ---- window hooks (set by Main)
    var bringToFront: () -> Unit = {}
    var focusWindow: () -> Unit = {}
    var awtWindow: java.awt.Window? = null
    var icon: androidx.compose.ui.graphics.painter.Painter? = null

    /** Folder that uploads go to from where the user is (open folder, else My Drive). */
    val currentFolderId: String? get() = (page as? Page.Files)?.folderId

    fun pickAndUpload(folderId: String? = currentFolderId) {
        val files = com.nudge.drive.desktop.ui.pickFiles(awtWindow, "Upload to Nudge Drive")
        if (files.isNotEmpty()) { transfers.upload(files, folderId); showTransfers = true }
    }

    /** Fetches drive files and opens Nearby share with them queued. */
    fun sendToPhone(files: List<FileItem>) {
        nearby.queueDriveFiles(files, transfers)
        go(Page.Nearby)
    }

    fun pickFolderAndUpload(folderId: String? = currentFolderId) {
        val dir = com.nudge.drive.desktop.ui.pickFolder(awtWindow, "Upload a folder") ?: return
        transfers.upload(listOf(dir), folderId); showTransfers = true
    }

    init {
        // Thumbnails and photos load through the authenticated client, cached on disk.
        SingletonImageLoader.setSafe { ctx: PlatformContext ->
            ImageLoader.Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { api.http })) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.25).build() }
                .diskCache { DiskCache.Builder().directory(File(Platform.cacheDir, "images").toOkioPath()).maxSizeBytes(512L * 1024 * 1024).build() }
                .crossfade(true)
                .build()
        }
        transfers.onFinished = { t ->
            when (t.state) {
                TransferState.Done -> if (t.kind == TransferKind.Download)
                    inbox.post("Downloaded", t.name, t.localPath?.let { NoticeRoute.ShowLocal(it) })
                TransferState.Failed -> inbox.post(if (t.kind == TransferKind.Upload) "Upload failed" else "Download failed", "${t.name}: ${t.error ?: "unknown error"}", NoticeRoute.Transfers)
                else -> {}
            }
            if (t.kind == TransferKind.Upload && transfers.items.value.none { it.kind == TransferKind.Upload && it.active }) {
                val done = transfers.items.value.count { it.kind == TransferKind.Upload && it.state == TransferState.Done }
                if (done > 0) inbox.post("Upload complete", "$done file${if (done == 1) "" else "s"} uploaded", t.fileId?.let { NoticeRoute.OpenFile(it) } ?: NoticeRoute.Transfers, system = done > 0)
            }
        }
        scope.launch { transfers.uploaded.collect { changed() } }
        // Nearby share: visible to phones while signed in (unless turned off), always asking first.
        nearby.onRequest = { r ->
            bringToFront()
            inbox.post("${r.peer.substringBefore(" · ")} wants to send you files", "${r.files.size} file${if (r.files.size == 1) "" else "s"} · code ${r.code}. Open Nudge Drive to accept.", NoticeRoute.Nearby)
        }
        nearby.onRequestGone = { peer -> toast("${peer.substringBefore(" · ")} cancelled sending") }
        nearby.onReceived = { peer, files -> inbox.post("Received from ${peer.substringBefore(" · ")}", "${files.size} file${if (files.size == 1) "" else "s"} saved to ${settings.downloadDir.name}", files.firstOrNull()?.let { NoticeRoute.ShowLocal(it.absolutePath) }) }
        nearby.onError = { toast(it) }
        scope.launch {
            kotlinx.coroutines.flow.combine(session.loggedIn.map { it == true }, settings.state.map { it.nearbyVisible }) { a, b -> a && b }
                .distinctUntilChanged().collect { nearby.setVisible(it) }
        }
        backup.onUploaded = { n -> inbox.post("Backup", "$n new file${if (n == 1) "" else "s"} backed up from this PC", NoticeRoute.Backup); changed() }
        // Inbox alerts (storage, drive errors, new shares).
        backup.onStorageFull = {
            inbox.post("Backup paused: storage is full", "Connect another Google account to keep backing up.", NoticeRoute.Drives,
                type = NoticeType.Storage, key = "backup-full", dedupeMs = Inbox.DAY)
        }
        startAlertPolling()
        // Watch the OS theme while following it.
        scope.launch(Dispatchers.IO) { while (true) { delay(4000); val d = Platform.systemDark(); if (d != darkSystem) darkSystem = d } }
        // Set up the editor engine quietly in the background once signed in.
        scope.launch {
            session.loggedIn.map { it == true }.distinctUntilChanged().collect { on ->
                if (on) launch { delay(60_000); com.nudge.drive.desktop.ui.viewer.EditorEngine.start(scope) }
            }
        }
        // Whoever is signed in owns the folder backup and the navigation. Another user signing in, signing
        // out or the session expiring (the API client drops it without logout()) starts clean.
        scope.launch {
            session.loggedIn.filterNotNull().first() // loaded from disk
            var atLaunch = true
            session.user.map { it?.id }.distinctUntilChanged().collect { id ->
                if (!atLaunch) withContext(Dispatchers.Main) { resetNavigation() }
                backup.switchUser(id, adoptUnowned = atLaunch)
                if (id != null) backup.start()
                atLaunch = false
            }
        }
    }

    private fun resetNavigation() {
        viewer = null; editorFor = null; showTransfers = false; history.clear(); page = Page.Home
    }

    // ---- inbox alerts: storage warnings + newly received shares (mirrors the phone app's Home checks)

    /** Raise a notice (once a day each) when the pool or a single drive is nearly full, or a drive has an error. */
    fun storageAlerts(s: StorageSummary, accounts: List<ConnectedAccount>) {
        if (!settings.value.storageAlerts) return
        if (s.total > 0 && s.used.toFloat() / s.total > 0.85f) {
            inbox.post("Storage pool is ${(s.used * 100 / s.total)}% full", "Only ${formatBytes(s.available)} left. Connect another Google account to add 15 GB.",
                NoticeRoute.Drives, type = NoticeType.Storage, key = "pool-full", dedupeMs = Inbox.DAY)
        }
        accounts.forEach { a ->
            val t = a.total
            if (t != null && t > 0 && a.used.toFloat() / t > 0.92f) inbox.post("${a.email} is almost full", "New uploads will go to your other drives.",
                NoticeRoute.Drives, type = NoticeType.Storage, key = "full-${a.id}", dedupeMs = Inbox.DAY)
            if (a.lastError != null) inbox.post("${a.email} needs attention", "Its storage couldn't be refreshed. Try reconnecting it in Drives.",
                NoticeRoute.Drives, type = NoticeType.Drive, key = "err-${a.id}", dedupeMs = Inbox.DAY)
        }
    }

    /** Turn newly received invites into notices. The first check only remembers what's there. */
    suspend fun checkInvites() {
        val received = runCatching { repo.invites().received }.getOrNull() ?: return
        val seen = settings.value.seenInvites
        val fresh = if (seen == null) emptyList() else received.filter { it.id !in seen }
        fresh.forEach { inv ->
            inbox.post("${inv.user?.name ?: inv.email} shared “${inv.target?.name ?: "a file"}” with you",
                "You can ${if (inv.role == "editor") "view and edit" else "view"} it.", NoticeRoute.Shared, type = NoticeType.Invite, key = "inv-${inv.id}")
        }
        if (seen == null || fresh.isNotEmpty()) settings.update { it.copy(seenInvites = received.map { r -> r.id }.toSet() + (seen ?: emptySet())) }
    }

    /** Storage + invite checks right after sign-in / app start, then every 10 minutes while signed in. */
    private fun startAlertPolling() = scope.launch {
        session.loggedIn.map { it == true }.distinctUntilChanged().collectLatest { on ->
            if (!on) return@collectLatest
            delay(5_000)
            while (true) {
                runCatching {
                    coroutineScope {
                        val s = async { repo.summary() }
                        val a = async { repo.accounts() }
                        storageAlerts(s.await(), a.await())
                    }
                }
                checkInvites()
                delay(10 * 60_000L)
            }
        }
    }

    suspend fun logout() {
        backup.stop()
        repo.logout()
        resetNavigation()
        File(Platform.cacheDir, "files").deleteRecursively()
        File(Platform.cacheDir, "previews").deleteRecursively()
    }
}
