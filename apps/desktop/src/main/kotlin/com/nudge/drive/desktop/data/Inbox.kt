package com.nudge.drive.desktop.data

import com.nudge.drive.data.AppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** Where tapping a notification takes you. */
@Serializable
sealed interface NoticeRoute {
    @Serializable data class OpenFile(val fileId: String) : NoticeRoute
    @Serializable data object Transfers : NoticeRoute
    @Serializable data object Backup : NoticeRoute
    @Serializable data object Drives : NoticeRoute
    @Serializable data object Nearby : NoticeRoute
    @Serializable data object Shared : NoticeRoute
    @Serializable data class ShowLocal(val path: String) : NoticeRoute
}

/** What a notification is about (picks its icon). */
@Serializable
enum class NoticeType { Upload, UploadFailed, Download, Drive, Storage, Share, Invite, Backup, Nearby, Info }

@Serializable
data class Notice(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val time: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val route: NoticeRoute? = null,
    val type: NoticeType = NoticeType.Info,
    /** Dedupe key: a notice with the same key isn't repeated within the post's dedupe window. */
    val key: String? = null,
)

/** In-app inbox (persisted) + Windows/macOS notifications through the tray icon. */
class Inbox(private val file: File = File(Platform.dataDir, "inbox.json")) {
    private val ser = ListSerializer(Notice.serializer())
    private val _items = MutableStateFlow(runCatching { AppJson.decodeFromString(ser, file.readText()) }.getOrDefault(emptyList()))
    val items: StateFlow<List<Notice>> = _items
    /** Set by the UI: shows a system notification (tray balloon / toast). */
    var systemNotify: ((Notice) -> Unit)? = null

    /** [key] dedupes: a notice with the same key isn't repeated within [dedupeMs]. */
    fun post(
        title: String, body: String, route: NoticeRoute? = null, system: Boolean = true,
        type: NoticeType = typeFor(title, route), key: String? = null, dedupeMs: Long = 0,
    ) {
        val now = System.currentTimeMillis()
        if (key != null && _items.value.any { it.key == key && now - it.time < dedupeMs }) return
        val n = Notice(title = title, body = body, route = route, type = type, key = key, time = now)
        _items.update { (listOf(n) + it).take(150) }
        persist()
        if (system) systemNotify?.invoke(n)
    }

    fun markAllRead() { _items.update { l -> l.map { it.copy(read = true) } }; persist() }
    fun markRead(id: String) { _items.update { l -> l.map { if (it.id == id) it.copy(read = true) else it } }; persist() }
    fun remove(id: String) { _items.update { l -> l.filter { it.id != id } }; persist() }
    fun clear() { _items.value = emptyList(); persist() }

    @Synchronized
    private fun persist() { runCatching { file.writeText(AppJson.encodeToString(ser, _items.value)) } }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000

        /** Best guess for posts that don't say what they are. */
        fun typeFor(title: String, route: NoticeRoute?): NoticeType = when {
            title.contains("failed", ignoreCase = true) -> NoticeType.UploadFailed
            route is NoticeRoute.ShowLocal -> NoticeType.Download
            route is NoticeRoute.Backup -> NoticeType.Backup
            route is NoticeRoute.Nearby -> NoticeType.Nearby
            route is NoticeRoute.Drives -> NoticeType.Drive
            route is NoticeRoute.Shared -> NoticeType.Invite
            route is NoticeRoute.OpenFile || title.startsWith("Upload") -> NoticeType.Upload
            else -> NoticeType.Info
        }
    }
}
