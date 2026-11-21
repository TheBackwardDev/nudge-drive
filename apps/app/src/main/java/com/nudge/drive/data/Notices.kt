package com.nudge.drive.data

import kotlinx.coroutines.sync.withLock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nudge.drive.MainActivity
import com.nudge.drive.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

enum class NoticeType { Upload, UploadFailed, Download, Drive, Storage, Share, Invite, Info }

@Serializable
data class Notice(
    val id: Long,
    val type: NoticeType,
    val title: String,
    val body: String,
    val time: Long,
    val read: Boolean = false,
    val fileId: String? = null,
    val key: String? = null,
    /** Where tapping it should go; see [destination]. */
    val route: String? = null,
)

/**
 * Screen a notice opens. Real nav routes, or pseudo routes the app graph understands:
 * "tab:files" / "tab:drives", "sheet:transfers", "system:downloads".
 */
fun Notice.destination(): String? = route ?: when {
    fileId != null -> "view/$fileId"
    type == NoticeType.Upload -> "tab:files"
    type == NoticeType.UploadFailed -> "sheet:transfers"
    type == NoticeType.Download -> "system:downloads"
    type == NoticeType.Drive || type == NoticeType.Storage -> "tab:drives"
    type == NoticeType.Share || type == NoticeType.Invite -> "shared"
    else -> null
}

/**
 * In-app notification centre (bell) + Android system notifications.
 * Notices persist in a small JSON file so the inbox survives restarts.
 */
class NotificationCenter(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "notices.json")
    private val _items = MutableStateFlow<List<Notice>>(emptyList())
    val items: StateFlow<List<Notice>> = _items

    /** Completes once the saved inbox is read; nothing is written before that (so it can't be wiped). */
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val writeLock = kotlinx.coroutines.sync.Mutex()

    init {
        createChannels()
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { AppJson.decodeFromString(ListSerializer(Notice.serializer()), file.readText()) }.getOrDefault(emptyList())
            // Merge with anything posted while we were reading.
            _items.update { early -> (early + saved).distinctBy { it.id }.sortedByDescending { it.time }.take(150) }
            loaded.complete(Unit)
        }
    }

    private fun persist() = scope.launch(Dispatchers.IO) {
        loaded.await()
        writeLock.withLock { runCatching { file.writeText(AppJson.encodeToString(ListSerializer(Notice.serializer()), _items.value.take(150))) } }
    }

    /** [key] dedupes: a notice with the same key isn't repeated within [dedupeMs]. */
    fun post(type: NoticeType, title: String, body: String, fileId: String? = null, key: String? = null, dedupeMs: Long = 0, system: Boolean = true, route: String? = null) {
        val now = System.currentTimeMillis()
        if (key != null && _items.value.any { it.key == key && now - it.time < dedupeMs }) return
        val n = Notice(now + _items.value.size, type, title, body, now, fileId = fileId, key = key, route = route)
        _items.update { (listOf(n) + it).take(150) }
        persist()
        if (system) showSystem(n)
    }

    fun markAllRead() { _items.update { l -> l.map { it.copy(read = true) } }; persist() }
    fun markRead(id: Long) { _items.update { l -> l.map { if (it.id == id) it.copy(read = true) else it } }; persist() }
    fun remove(id: Long) { _items.update { l -> l.filterNot { it.id == id } }; persist() }
    fun clear() { _items.value = emptyList(); persist() }

    fun canPostSystem(): Boolean =
        prefs.systemNotifications.value &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun showSystem(n: Notice) {
        if (!canPostSystem()) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTICES, true)
            putExtra(EXTRA_NOTICE_ID, n.id)
            n.destination()?.let { putExtra(EXTRA_ROUTE, it) }
        }
        val pi = PendingIntent.getActivity(context, n.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = NotificationCompat.Builder(context, CH_ACTIVITY)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setColor(0xFF8B72F5.toInt())
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(n.id.toInt(), notif) }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ACTIVITY, "Activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Uploads finished, drives connected, storage warnings, shared files"
        })
        nm.createNotificationChannel(NotificationChannel(CH_TRANSFERS, "Transfers in progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows upload and download progress"
            setShowBadge(false)
        })
    }

    // ---- aggregation of transfer results so 20 uploads = one notice
    private var pending = mutableListOf<Transfer>()
    private var flushJob: Job? = null

    fun onTransferFinished(t: Transfer, fileId: String?) = synchronized(this) {
        pending += t
        if (pendingFileId == null) pendingFileId = fileId
        flushJob?.cancel()
        flushJob = scope.launch { delay(1500); flush() }
    }
    private var pendingFileId: String? = null

    private fun flush() {
        val (batch, fid) = synchronized(this) { (pending.toList() to pendingFileId).also { pending = mutableListOf(); pendingFileId = null } }
        val ups = batch.filter { it.kind == TransferKind.Upload && it.state == TransferState.Done }
        val failed = batch.filter { it.state == TransferState.Failed }
        val downs = batch.filter { it.kind == TransferKind.Download && it.state == TransferState.Done }
        if (ups.isNotEmpty()) post(
            NoticeType.Upload,
            if (ups.size == 1) "Uploaded ${ups[0].name}" else "${ups.size} files uploaded",
            if (ups.size == 1) "Saved to your storage pool." else ups.take(4).joinToString(", ") { it.name } + if (ups.size > 4) " and ${ups.size - 4} more" else "",
            fileId = if (ups.size == 1) fid else null,
        )
        if (downs.isNotEmpty()) post(
            NoticeType.Download,
            if (downs.size == 1) "Downloaded ${downs[0].name}" else "${downs.size} downloads finished",
            "Saved in Downloads › Nudge Drive",
        )
        if (failed.isNotEmpty()) post(
            NoticeType.UploadFailed,
            if (failed.size == 1) "Couldn't transfer ${failed[0].name}" else "${failed.size} transfers failed",
            failed.first().error ?: "Check your connection and try again.",
        )
    }

    companion object {
        const val CH_ACTIVITY = "activity"
        const val CH_TRANSFERS = "transfers"
        const val EXTRA_OPEN_NOTICES = "open_notices"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_NOTICE_ID = "notice_id"
    }
}
