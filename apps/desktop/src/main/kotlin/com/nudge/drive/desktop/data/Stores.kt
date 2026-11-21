package com.nudge.drive.desktop.data

import com.nudge.drive.data.AppJson
import com.nudge.drive.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Session tokens in the user's app-data folder, encrypted with Windows DPAPI. */
class FileSecureStore(private val file: File = File(Platform.dataDir, "session.bin")) : SecureStore {
    private val lock = Mutex()
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    private fun load(): MutableMap<String, String> = runCatching {
        AppJson.decodeFromString(serializer, String(Platform.unprotect(file.readBytes()), Charsets.UTF_8)).toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun save(map: Map<String, String>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(Platform.protect(AppJson.encodeToString(serializer, map).toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        Platform.ownerOnly(file)
    }

    override suspend fun read(): Map<String, String> = withContext(Dispatchers.IO) { lock.withLock { load() } }
    override suspend fun write(values: Map<String, String?>) = withContext(Dispatchers.IO) {
        lock.withLock { val m = load(); values.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v }; save(m) }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock { file.delete(); Unit } }
}

@Serializable
data class BackupFolder(
    val path: String,
    val enabled: Boolean = true,
    /** Nudge folder that mirrors it ("PC backup/<name>"), created on first run. */
    val remoteFolderId: String? = null,
    val lastRunAt: Long = 0,
    val uploaded: Int = 0,
)

/** One user's folder backup choices, kept on this PC while someone else is signed in. */
@Serializable
data class UserBackup(
    val folders: List<BackupFolder> = emptyList(),
    val paused: Boolean = false,
    val sinceMs: Long = 0L,
    val sinceLabel: String = "Everything",
)

@Serializable
data class DesktopSettings(
    /** "system" | "light" | "dark" */
    val theme: String = "system",
    val downloadDir: String? = null,
    val closeToTray: Boolean = true,
    val backupFolders: List<BackupFolder> = emptyList(),
    val backupPaused: Boolean = false,
    val onboardedUsers: Set<String> = emptySet(),
    /** Set once: whoever was already signed in when first-run setup was added skips it. */
    val onboardMigrated: Boolean = false,
    val grid: Boolean = true,
    val sendToInstalled: Boolean = false,
    /** Phones on the same Wi-Fi can find this computer in Nearby share (you're always asked first). */
    val nearbyVisible: Boolean = true,
    /** Windows/macOS notifications from the tray icon (the in-app list always fills). */
    val systemNotifications: Boolean = true,
    /** Warn when the pool or a drive is almost full, or a drive needs attention. */
    val storageAlerts: Boolean = true,
    /** "Get started" card on Home was closed. */
    val checklistDismissed: Boolean = false,
    /** Received invite ids already turned into notifications (null = not checked yet, so old ones stay quiet). */
    val seenInvites: Set<String>? = null,
    /** Folder backup only uploads files changed after this moment (0 = everything). */
    val backupSinceMs: Long = 0L,
    val backupSinceLabel: String = "Everything",
    /** Whose folder backup the backup* settings above are (user id); null = nobody signed in. */
    val backupOwner: String? = null,
    /** Everyone else's folder backup settings, put back when they sign in again. */
    val backupByUser: Map<String, UserBackup> = emptyMap(),
) {
    val backupOfOwner get() = UserBackup(backupFolders, backupPaused, backupSinceMs, backupSinceLabel)
    fun withBackup(owner: String?, b: UserBackup) =
        copy(backupOwner = owner, backupFolders = b.folders, backupPaused = b.paused, backupSinceMs = b.sinceMs, backupSinceLabel = b.sinceLabel)
}

/** Choices for "what about the files already in the folder?" (same as the phone app). */
enum class BackupRange(val label: String, val days: Long?) {
    NewOnly("Only new files from now on", 0), Week("Last 7 days", 7), Month("Last 30 days", 30), All("Everything", null);
    fun sinceMs(): Long = when (days) { null -> 0L; 0L -> System.currentTimeMillis(); else -> System.currentTimeMillis() - days * 86_400_000L }
}

/** App settings (prefs.json in the app-data folder). */
class SettingsStore(private val file: File = File(Platform.dataDir, "prefs.json")) {
    private val _state = MutableStateFlow(runCatching { AppJson.decodeFromString(DesktopSettings.serializer(), file.readText()) }.getOrDefault(DesktopSettings()))
    val state: StateFlow<DesktopSettings> = _state
    val value get() = _state.value

    @Synchronized
    fun update(change: (DesktopSettings) -> DesktopSettings) {
        val next = change(_state.value)
        _state.value = next
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(DesktopSettings.serializer(), next))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    val downloadDir: File get() = value.downloadDir?.let { File(it) } ?: Platform.defaultDownloads
}
