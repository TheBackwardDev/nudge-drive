package com.nudge.drive.data

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nudge.drive.NudgeApp
import com.nudge.drive.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable data class BackupFolder(val uri: String, val name: String, val serverFolderId: String? = null)

/** A phone photo/video folder (MediaStore bucket) that is backed up. */
@Serializable data class BackupBucket(val bucketId: Long, val name: String, val serverFolderId: String? = null)

@Serializable
data class BackupConfig(
    val enabled: Boolean = false,
    /** Phone photo folders (Camera, Screenshots…) — the main, Google Photos-style way. */
    val buckets: List<BackupBucket> = emptyList(),
    /** Extra folders picked with the system file picker (documents etc.). */
    val folders: List<BackupFolder> = emptyList(),
    /** Only files changed after this moment are backed up (0 = everything). */
    val sinceMs: Long = 0L,
    val sinceLabel: String = "Everything",
    val everyHours: Int = 6,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val lastRunMs: Long = 0L,
    val lastCount: Int = 0,
    val totalCount: Int = 0,
    val rootFolderId: String? = null,
) {
    val hasSources: Boolean get() = buckets.isNotEmpty() || folders.isNotEmpty()
}

data class BackupProgress(val done: Int, val total: Int, val current: String)

/** Choices for "what about the files already on the phone?" */
enum class BackupRange(val label: String, val days: Long?) {
    NewOnly("Only new files from now on", 0), Week("Last 7 days", 7), Month("Last 30 days", 30), All("Everything", null);
    fun sinceMs(): Long = when (days) { null -> 0L; 0L -> System.currentTimeMillis(); else -> System.currentTimeMillis() - days * 86_400_000L }
}

/**
 * Auto backup, like Google Photos: chosen phone photo folders (and optional extra folders) are
 * uploaded into "Phone backup / <folder>" on a WorkManager schedule, keeping each photo's original date.
 */
class BackupManager(private val context: Context, private val repo: Repository, private val transfers: Transfers, private val notices: NotificationCenter) {
    private val cfgFile = File(context.filesDir, "backup.json")
    private val indexFile = File(context.filesDir, "backup_index.txt")
    private val _config = MutableStateFlow(runCatching { AppJson.decodeFromString<BackupConfig>(cfgFile.readText()) }.getOrDefault(BackupConfig()))
    val config: StateFlow<BackupConfig> = _config
    private val _running = MutableStateFlow<BackupProgress?>(null)
    val running: StateFlow<BackupProgress?> = _running
    /** Keys of everything already uploaded (for the "backed up" ticks in the folder views). */
    private val _done = MutableStateFlow<Set<String>>(emptySet())
    val backedUp: StateFlow<Set<String>> = _done
    private val runLock = Mutex()
    private val writeLock = Any()

    init {
        Thread { _done.value = loadIndex() }.start()
    }

    /** Atomic config change + single-writer save. Safe from the UI and the worker at the same time. */
    fun update(f: (BackupConfig) -> BackupConfig) {
        synchronized(writeLock) {
            _config.update(f)
            runCatching { cfgFile.writeText(AppJson.encodeToString(BackupConfig.serializer(), _config.value)) }
        }
        schedule()
    }

    fun setBucket(folder: DeviceFolder, on: Boolean) = update { c ->
        if (on) { if (c.buckets.any { it.bucketId == folder.bucketId }) c else c.copy(buckets = c.buckets + BackupBucket(folder.bucketId, folder.name)) }
        else c.copy(buckets = c.buckets.filterNot { it.bucketId == folder.bucketId })
    }

    fun addFolder(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
        update { c -> if (c.folders.any { it.uri == uri.toString() }) c else c.copy(folders = c.folders + BackupFolder(uri.toString(), name)) }
    }

    fun removeFolder(uri: String) {
        update { c -> c.copy(folders = c.folders.filterNot { it.uri == uri }) }
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun setRange(r: BackupRange) = update { it.copy(sinceMs = r.sinceMs(), sinceLabel = r.label) }

    /** Signed out: stop backing up this phone into anyone's account and forget what was sent. */
    fun reset() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
        synchronized(writeLock) {
            _config.value = BackupConfig()
            runCatching { cfgFile.delete(); indexFile.delete() }
            _done.value = emptySet()
        }
    }

    fun schedule() {
        val wm = WorkManager.getInstance(context)
        val c = _config.value
        if (!c.enabled || !c.hasSources) { wm.cancelUniqueWork(PERIODIC); return }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (c.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(c.chargingOnly)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<BackupWorker>(c.everyHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow() {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    private fun loadIndex(): Set<String> = runCatching { indexFile.readLines().toHashSet() }.getOrDefault(emptySet())

    private data class Candidate(val uri: Uri, val name: String, val size: Long, val mime: String, val modified: Long, val key: String, val serverFolderId: String?, val takenMs: Long?)

    private fun walk(dir: DocumentFile, folderId: String?, since: Long, index: Set<String>, out: MutableList<Candidate>, depth: Int = 0) {
        if (depth > 8) return
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (name.startsWith(".")) continue
            if (f.isDirectory) { walk(f, folderId, since, index, out, depth + 1); continue }
            val size = f.length(); val mod = f.lastModified()
            if (size <= 0 || mod < since) continue
            val key = "${f.uri}|$size|$mod"
            if (key in index) continue
            out += Candidate(f.uri, name, size, f.type ?: "application/octet-stream", mod, key, folderId, null)
        }
    }

    /** What a pass backs up. If it changes during a pass (Screenshots switched on, range widened…), the run goes again. */
    private fun BackupConfig.sources() = listOf(enabled, buckets.map { it.bucketId }, folders.map { it.uri }, sinceMs)

    /**
     * Backs up until nothing is left. "Back up now" while a run is going is ignored (KEEP), so changes made
     * meanwhile are picked up here with another pass instead of waiting for the next scheduled run.
     * Returns how many files were uploaded.
     */
    suspend fun run(onProgress: suspend (BackupProgress) -> Unit = {}): Int {
        var uploaded = 0
        repeat(3) {
            val before = _config.value.sources()
            uploaded += pass(onProgress)
            if (_config.value.sources() == before) return uploaded
        }
        return uploaded
    }

    /** One backup pass. Returns how many files were uploaded. */
    private suspend fun pass(onProgress: suspend (BackupProgress) -> Unit): Int = runLock.withLock {
        val start = _config.value
        if (!start.enabled || !start.hasSources) return 0
        _running.value = BackupProgress(0, 0, "Looking for new photos…")
        var uploaded = 0
        try {
            // "Phone backup" and one sub-folder per source must exist on the server.
            val existing = repo.allFolders().map { it.id }.toSet()
            val rootId = start.rootFolderId?.takeIf { it in existing } ?: repo.createFolder("Phone backup", null, "#8b72f5").id
            val bucketFolders = start.buckets.associate { b ->
                b.bucketId to (b.serverFolderId?.takeIf { it in existing } ?: repo.createFolder(b.name, rootId, "#3b82f6").id)
            }
            val safFolders = start.folders.associate { f ->
                f.uri to (f.serverFolderId?.takeIf { it in existing } ?: repo.createFolder(f.name, rootId, "#10b981").id)
            }
            // Merge by id into the *current* config, so folders added/removed meanwhile are kept as the user left them.
            update { cur ->
                cur.copy(
                    rootFolderId = rootId,
                    buckets = cur.buckets.map { b -> bucketFolders[b.bucketId]?.let { b.copy(serverFolderId = it) } ?: b },
                    folders = cur.folders.map { f -> safFolders[f.uri]?.let { f.copy(serverFolderId = it) } ?: f },
                )
            }

            val index = loadIndex()
            val candidates = withContext(Dispatchers.IO) {
                buildList {
                    start.buckets.forEach { b ->
                        DeviceMedia.items(context, b.bucketId, start.sinceMs).filter { it.key !in index && it.size > 0 }.forEach { m ->
                            add(Candidate(m.uri, m.name, m.size, m.mime, m.modifiedSec * 1000, m.key, bucketFolders[b.bucketId], m.takenMs))
                        }
                    }
                    start.folders.forEach { f ->
                        // One unreadable folder (permission revoked) must not stop the rest.
                        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(f.uri))?.let { walk(it, safFolders[f.uri], start.sinceMs, index, this) } }
                    }
                }.sortedBy { it.modified }
            }
            var failed = 0
            for ((i, cand) in candidates.withIndex()) {
                if (!_config.value.enabled) break // turned off mid-run
                val p = BackupProgress(i, candidates.size, cand.name)
                _running.value = p; onProgress(p)
                try {
                    transfers.uploadDirect(cand.uri, cand.name, cand.size, cand.mime, cand.serverFolderId, cand.takenMs) { }
                    withContext(Dispatchers.IO) { indexFile.appendText(cand.key + "\n") }
                    _done.update { it + cand.key }
                    uploaded++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    if (e.code == "NO_ACCOUNT_WITH_ENOUGH_SPACE") {
                        notices.post(NoticeType.Storage, "Backup paused: storage is full", "Connect another Google account to keep backing up.", route = "tab:drives", key = "backup-full", dedupeMs = 86_400_000L)
                        break
                    }
                    failed++
                } catch (e: Exception) { failed++ }
            }
            update { it.copy(lastRunMs = System.currentTimeMillis(), lastCount = uploaded, totalCount = it.totalCount + uploaded) }
            if (uploaded > 0) notices.post(
                NoticeType.Upload, "Backed up $uploaded photo${if (uploaded == 1) "" else "s"} and files",
                "Saved in My Files › Phone backup" + if (failed > 0) " · $failed couldn't be uploaded" else "",
                route = "backup",
            )
        } finally {
            _running.value = null
        }
        uploaded
    }

    companion object {
        const val PERIODIC = "auto-backup"
        const val NOW = "backup-now"
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private fun info(text: String, done: Int, total: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, NotificationCenter.CH_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_nudge).setColor(0xFF8B72F5.toInt())
            .setContentTitle("Backing up your files").setContentText(text)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true).setSilent(true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(5151, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(5151, n)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Getting ready…", 0, 0)

    override suspend fun doWork(): Result {
        val c = (applicationContext as NudgeApp).container
        c.session.load()
        if (c.session.refreshToken == null) return Result.success()
        runCatching { setForeground(info("Looking for new files…", 0, 0)) }
        return try {
            c.backup.run { p -> runCatching { setForeground(info("${p.done + 1} of ${p.total} · ${p.current}", p.done, p.total)) } }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
