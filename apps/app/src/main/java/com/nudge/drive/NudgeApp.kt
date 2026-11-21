package com.nudge.drive

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nudge.drive.data.DataStoreSecureStore
import com.nudge.drive.data.ApiClient
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.NotificationCenter
import com.nudge.drive.data.Prefs
import com.nudge.drive.data.Repository
import com.nudge.drive.data.SessionStore
import com.nudge.drive.data.Transfers
import com.nudge.drive.util.isMedia
import com.nudge.drive.util.viewerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** A short message in the glass toast, optionally with one action such as Undo. */
data class ToastMsg(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** Simple manual DI container. */
typealias FileItemFolder = com.nudge.drive.data.Folder

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = Prefs(app, scope)
    val session = SessionStore(DataStoreSecureStore(app))
    val api = ApiClient(session, BuildConfig.API_BASE, "NudgeDrive-Android/${BuildConfig.VERSION_NAME}")
    val repo = Repository(api, BuildConfig.WEB_BASE)
    val notices = NotificationCenter(app, prefs, scope)
    val transfers = Transfers(app, api, scope).also { t ->
        t.onFinished = notices::onTransferFinished
        t.onUnreadable = { n -> toasts.tryEmit(ToastMsg(if (n == 1) "Couldn't read that file. Try sharing it again." else "Couldn't read $n of the files. Try sharing them again.")) }
    }
    val backup = com.nudge.drive.data.BackupManager(app, repo, transfers, notices)

    val shareOut = com.nudge.drive.data.ShareOut(app, repo, transfers, scope) { toast(it) }
    val nearby = com.nudge.drive.data.NearbyShare(app, scope) {
        val first = session.user.value?.name?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Nudge user"
        "$first · ${android.os.Build.MODEL}"
    }
    /** Main-screen tab (or "transfers" sheet) to show, set by notification taps. */
    val pendingTab = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** A file was saved from the in-app editor; open viewers reload it. */
    val fileEdited = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Files shared into Nudge from other apps, waiting for the user to pick a folder. */
    val incomingShares = kotlinx.coroutines.flow.MutableStateFlow<List<android.net.Uri>>(emptyList())
    /** A folder (with its parent trail) the Files tab should open, e.g. from the All folders screen. */
    val openFolderTrail = kotlinx.coroutines.flow.MutableStateFlow<List<FileItemFolder>?>(null)

    /** Fired when something changed server-side so visible lists reload (connect drive, uploads, moves…). */
    val refreshBus = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val toasts = MutableSharedFlow<ToastMsg>(extraBufferCapacity = 8)
    /** In-app Google account picker; set while MainActivity is alive (it needs an activity). */
    var googleNative: com.nudge.drive.data.GoogleNative? = null
    fun toast(msg: String) { toasts.tryEmit(ToastMsg(msg)) }
    fun toast(msg: String, actionLabel: String, action: () -> Unit) { toasts.tryEmit(ToastMsg(msg, actionLabel, action)) }

    /** Last-known file objects so detail screens can render instantly while they refresh. */
    val fileCache = ConcurrentHashMap<String, FileItem>()
    /** The list a photo/video was opened from, so the viewer can swipe through it like a gallery. */
    @Volatile var viewerList: List<FileItem> = emptyList()

    init {
        // Someone signed out (or another account signed in): drop everything tied to the previous user.
        scope.launch {
            var previous: String? = null
            session.user.map { it?.id }.distinctUntilChanged().collect { id ->
                if (previous != null && id != previous) {
                    backup.reset(); notices.clear(); fileCache.clear(); viewerList = emptyList()
                    nearby.stop(); nearby.clearOutgoing()
                }
                if (id != null) previous = id
            }
        }
    }

    /** Opens [file] in the in-app viewer when it can be previewed, otherwise on its details page. */
    fun openFileRoute(file: FileItem, siblings: List<FileItem> = emptyList()): String {
        fileCache[file.id] = file
        siblings.forEach { fileCache.putIfAbsent(it.id, it) }
        if (file.viewerType() == com.nudge.drive.util.ViewerType.None) return com.nudge.drive.ui.nav.Routes.file(file.id)
        viewerList = siblings.filter { it.isMedia }
        return com.nudge.drive.ui.nav.Routes.view(file.id)
    }
    /** A screen to open once the main graph is showing (from notification taps). */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

class NudgeApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Coil goes through the authenticated OkHttp client so thumbnails use the bearer token + refresh.
     * It gets its own dispatcher so a screen full of thumbnails loads in parallel (OkHttp allows only 5 per host by default)
     * without queueing behind uploads and downloads.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageHttp by lazy {
            container.api.http.newBuilder()
                .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 48; maxRequestsPerHost = 16 })
                .build()
        }
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttp }))
                add(coil3.video.VideoFrameDecoder.Factory()) // phone videos in the backup folder views
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.3).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("thumbs")).maxSizeBytes(400L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }
}
