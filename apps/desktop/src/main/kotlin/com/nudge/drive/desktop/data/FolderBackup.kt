package com.nudge.drive.desktop.data

import com.nudge.drive.data.AppJson
import com.nudge.drive.data.Folder
import com.nudge.drive.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/** What the backup is doing right now. */
sealed interface BackupStatus {
    data object Off : BackupStatus
    data object Idle : BackupStatus
    data class Scanning(val folder: String) : BackupStatus
    data class Uploading(val folder: String, val done: Int, val total: Int, val current: String) : BackupStatus
    data class Error(val message: String) : BackupStatus
    /** No drive has room left; backup waits until there's space again. */
    data object Full : BackupStatus
}

/**
 * Keeps chosen PC folders backed up into "PC backup/<folder>" on the user's drives, like Google Drive
 * for desktop. Each folder keeps an index of what's already uploaded (path + size + modified time),
 * so only new or changed files go up. Runs at start, every 15 minutes, and shortly after files change.
 */
class FolderBackup(
    private val repo: Repository,
    private val transfers: DesktopTransfers,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Off)
    val status: StateFlow<BackupStatus> = _status
    private val runLock = Mutex()
    private var loop: Job? = null
    private var watcher: Job? = null
    /** The one backup run (timer, file watcher or "Back up now"); stop() cancels it. */
    private var run: Job? = null
    /** Asked to back up while a run was going: it does one more pass when it's done. */
    private var again = false
    /** Between start() and stop(). A run that stop() cancelled must not put a status back over Off. */
    private var active = false
    var onUploaded: ((count: Int) -> Unit)? = null
    /** Called when a run stops because none of the drives has room left. */
    var onStorageFull: (() -> Unit)? = null

    private class StorageFull : Exception()

    @Synchronized
    fun start() {
        active = true
        if (loop?.isActive == true) return
        loop = scope.launch(Dispatchers.IO) {
            delay(5_000)
            while (isActive) {
                trigger()
                delay(15 * 60_000)
            }
        }
        restartWatcher()
    }

    @Synchronized
    fun stop() {
        active = false
        loop?.cancel(); watcher?.cancel(); run?.cancel()
        loop = null; watcher = null; run = null; again = false
        _status.value = BackupStatus.Off
    }

    private suspend fun setStatus(s: BackupStatus) {
        val ctx = currentCoroutineContext()
        synchronized(this) { if (active && ctx.isActive) _status.value = s }
    }

    /**
     * Puts [userId]'s folder backup settings in place and keeps the previous user's aside, so on a shared
     * PC one person's folders never back up into someone else's account (and come back when they sign in
     * again). [adoptUnowned]: settings from before they were per user belong to whoever was signed in at launch.
     */
    fun switchUser(userId: String?, adoptUnowned: Boolean) {
        stop()
        settings.update { s ->
            val owner = s.backupOwner
            when {
                owner == userId -> s
                owner == null && userId != null && adoptUnowned -> {
                    // Their indexes were kept per folder only.
                    s.backupFolders.forEach { bf -> legacyIndex(bf).takeIf { it.exists() }?.renameTo(indexFile(bf, userId)) }
                    s.copy(backupOwner = userId)
                }
                else -> {
                    val all = if (owner != null) s.backupByUser + (owner to s.backupOfOwner) else s.backupByUser
                    s.copy(backupByUser = if (userId != null) all - userId else all).withBackup(userId, userId?.let { all[it] } ?: UserBackup())
                }
            }
        }
    }

    @Synchronized
    fun restartWatcher() {
        watcher?.cancel()
        if (!active) return
        val dirs = settings.value.backupFolders.filter { it.enabled }.map { File(it.path) }.filter { it.isDirectory }
        if (dirs.isEmpty()) { if (settings.value.backupFolders.none { it.enabled }) _status.value = BackupStatus.Off; return }
        watcher = scope.launch(Dispatchers.IO) {
            val ws = FileSystems.getDefault().newWatchService()
            try {
                dirs.forEach { d -> d.walkTopDown().filter { it.isDirectory && !it.isHidden }.take(2000).forEach { runCatching { it.toPath().register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY) } } }
                while (isActive) {
                    val key = ws.poll(2, TimeUnit.SECONDS) ?: continue
                    key.pollEvents(); key.reset()
                    // Let copies finish, then back up once for the whole burst.
                    delay(20_000)
                    while (true) { val k = ws.poll() ?: break; k.pollEvents(); k.reset() }
                    trigger()
                }
            } finally { ws.close() }
        }
    }

    /** Backs up every enabled folder now: one run at a time, and asked again meanwhile → one more pass after it. */
    @Synchronized
    fun trigger() {
        if (!active) return
        if (run != null) { again = true; return }
        run = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]
            try {
                while (true) {
                    runNow()
                    synchronized(this@FolderBackup) {
                        if (!again) { if (run === me) run = null; return@launch }
                        again = false
                    }
                }
            } finally {
                synchronized(this@FolderBackup) { if (run === me) run = null }
            }
        }
    }

    private suspend fun runNow() = runLock.withLock {
        val s = settings.value
        val owner = s.backupOwner
        val folders = s.backupFolders.filter { it.enabled }
        if (folders.isEmpty() || s.backupPaused) { setStatus(if (folders.isEmpty()) BackupStatus.Off else BackupStatus.Idle); return@withLock }
        var total = 0
        try {
            for (bf in folders) { val n = backupOne(bf, owner) { total += it }; total += n }
            setStatus(BackupStatus.Idle)
            if (total > 0) onUploaded?.invoke(total)
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageFull) {
            setStatus(BackupStatus.Full)
            if (total > 0) onUploaded?.invoke(total)
            onStorageFull?.invoke()
        } catch (e: Exception) {
            setStatus(BackupStatus.Error(e.message ?: "Backup failed"))
        }
    }

    private fun legacyIndex(bf: BackupFolder) = File(Platform.dataDir, "backup-index-${bf.path.hashCode().toUInt().toString(16)}.json")
    /** What's already uploaded from a folder, per user: the same folder backs up again into another account. */
    private fun indexFile(bf: BackupFolder, owner: String? = settings.value.backupOwner) =
        if (owner == null) legacyIndex(bf)
        else File(Platform.dataDir, "backup-index-${owner.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}-${bf.path.hashCode().toUInt().toString(16)}.json")

    /** Changes one of [owner]'s folders, unless someone else has signed in meanwhile. */
    private fun updateFolder(owner: String?, path: String, f: (BackupFolder) -> BackupFolder) =
        settings.update { st -> if (st.backupOwner != owner) st else st.copy(backupFolders = st.backupFolders.map { if (it.path == path) f(it) else it }) }
    private val indexSer = MapSerializer(String.serializer(), String.serializer())

    /** [partial] reports what was uploaded before a "storage full" stop (the return value is lost then). */
    private suspend fun backupOne(bf: BackupFolder, owner: String?, partial: (Int) -> Unit): Int {
        val root = File(bf.path)
        if (!root.isDirectory) return 0
        val label = root.name.ifBlank { root.absolutePath }
        setStatus(BackupStatus.Scanning(label))
        val idxFile = indexFile(bf, owner)
        val index = runCatching { AppJson.decodeFromString(indexSer, idxFile.readText()).toMutableMap() }.getOrDefault(mutableMapOf())
        val since = settings.value.backupSinceMs
        val pending = root.walkTopDown()
            .onEnter { !it.isHidden && !it.name.startsWith(".") && it.name != "node_modules" && it.name != "\$RECYCLE.BIN" }
            .filter { it.isFile && !it.isHidden && !it.name.startsWith("~$") && !it.name.endsWith(".tmp") && it.length() > 0 }
            .filter { f -> f.lastModified() >= since && index[f.relativeTo(root).path] != stamp(f) }
            .toList()
        if (pending.isEmpty()) return 0

        val remoteRoot = ensureRemoteRoot(bf, owner, label)
        val folderIds = mutableMapOf("" to remoteRoot)
        var done = 0
        for (f in pending) {
            if (settings.value.backupPaused) break
            val rel = f.relativeTo(root)
            setStatus(BackupStatus.Uploading(label, done, pending.size, f.name))
            val parentRel = rel.parent ?: ""
            val folderId = remoteFolderFor(parentRel, folderIds)
            try {
                transfers.uploadDirect(f, folderId)
                index[rel.path] = stamp(f)
                done++
                if (done % 10 == 0) idxFile.writeText(AppJson.encodeToString(indexSer, index))
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.nudge.drive.data.ApiException) {
                if (e.code == "NO_ACCOUNT_WITH_ENOUGH_SPACE") {
                    // No point trying the rest: save progress and pause until there's room.
                    idxFile.writeText(AppJson.encodeToString(indexSer, index))
                    updateFolder(owner, bf.path) { it.copy(lastRunAt = System.currentTimeMillis(), uploaded = it.uploaded + done) }
                    partial(done)
                    throw StorageFull()
                }
            } catch (e: Exception) {
                // Skip this one (locked, vanished…); it's retried on the next run.
            }
        }
        idxFile.writeText(AppJson.encodeToString(indexSer, index))
        updateFolder(owner, bf.path) { it.copy(lastRunAt = System.currentTimeMillis(), uploaded = it.uploaded + done) }
        return done
    }

    private fun stamp(f: File) = "${f.length()}:${f.lastModified()}"

    private suspend fun ensureRemoteRoot(bf: BackupFolder, owner: String?, label: String): String {
        bf.remoteFolderId?.let { id -> if (runCatching { repo.folders(id) }.isSuccess) return id }
        val parent = findOrCreate(null, "PC backup")
        val mine = findOrCreate(parent.id, label)
        updateFolder(owner, bf.path) { it.copy(remoteFolderId = mine.id) }
        return mine.id
    }

    private suspend fun remoteFolderFor(rel: String, cache: MutableMap<String, String>): String {
        cache[rel]?.let { return it }
        val parentRel = File(rel).parent ?: ""
        val parentId = remoteFolderFor(parentRel, cache)
        val id = findOrCreate(parentId, File(rel).name).id
        cache[rel] = id
        return id
    }

    private suspend fun findOrCreate(parentId: String?, name: String): Folder =
        repo.folders(parentId).firstOrNull { it.name == name } ?: repo.createFolder(name, parentId, "#8b72f5")

    /** Forget a folder's index (so removing and re-adding it doesn't skip files that were deleted online). */
    fun forget(path: String) { indexFile(BackupFolder(path)).delete() }
}
