package com.nudge.drive.desktop.data

import com.nudge.drive.data.FileItem
import com.nudge.drive.data.LanEvent
import com.nudge.drive.data.LanFileInfo
import com.nudge.drive.data.LanListener
import com.nudge.drive.data.LanOutgoing
import com.nudge.drive.data.LanPeer
import com.nudge.drive.data.LanSaver
import com.nudge.drive.data.LanShare
import com.nudge.drive.data.LanTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress

enum class ShareFileState { Waiting, Moving, Done, Failed }

data class ShareFile(val name: String, val size: Long, val bytes: Long = 0, val state: ShareFileState = ShareFileState.Waiting, val saved: File? = null) {
    val progress: Float get() = if (size <= 0) (if (state == ShareFileState.Done) 1f else 0f) else (bytes.toFloat() / size).coerceIn(0f, 1f)
}

sealed interface SharePhase {
    data object Idle : SharePhase
    /** Connecting; [code] shows once both sides have exchanged keys. */
    data class Asking(val peer: String, val code: String?, val sending: Boolean) : SharePhase
    data class Moving(val peer: String, val sending: Boolean) : SharePhase
    data class Done(val peer: String, val sending: Boolean) : SharePhase
    data class Failed(val message: String, val sending: Boolean) : SharePhase
}

data class ShareRequest(val transfer: Long, val peer: String, val kind: String, val code: String, val files: List<LanFileInfo>)

/**
 * Nearby share for the computer: send files to phones (and other computers) on the same Wi-Fi, and
 * receive from them. Phones find this computer while their Nearby share screen is open; the
 * computer stays visible in the background (Settings can turn that off) and always asks first.
 */
class DesktopNearby(private val scope: CoroutineScope, private val settings: SettingsStore, private val userName: () -> String?) {
    val lan = LanShare(scope, ::deviceName, "computer") { AppLog.w("nearby", it) }
    val peers: StateFlow<List<LanPeer>> get() = lan.peers

    val phase = MutableStateFlow<SharePhase>(SharePhase.Idle)
    val files = MutableStateFlow<List<ShareFile>>(emptyList())
    val request = MutableStateFlow<ShareRequest?>(null)
    /** Local files waiting to be sent. */
    val outgoing = MutableStateFlow<List<File>>(emptyList())
    /** Drive files still being fetched before they can be sent. */
    val preparing = MutableStateFlow(0)
    /** Files received since the app started (newest first). */
    val received = MutableStateFlow<List<File>>(emptyList())

    var onRequest: (ShareRequest) -> Unit = {}
    var onRequestGone: (String) -> Unit = {}
    var onReceived: (peer: String, files: List<File>) -> Unit = { _, _ -> }
    var onError: (String) -> Unit = {}

    @Volatile private var current = -1L
    private var sending = false

    val busy: Boolean get() = phase.value is SharePhase.Asking || phase.value is SharePhase.Moving
    val visible: Boolean get() = lan.isReceiving

    private val computerName: String by lazy {
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: if (Platform.isMac) "Mac" else "Computer"
    }

    /** "Alex · DESKTOP-1234": how phones see this computer. */
    fun deviceName(): String {
        val first = userName()?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }
        return if (first != null) "$first · $computerName" else computerName
    }

    private val listener = LanListener { tid, e -> onEvent(tid, e) }

    fun setVisible(on: Boolean) {
        if (on) lan.startReceiving(saver, listener)
        else { lan.stopReceiving(); request.value = null }
    }

    // ------------------------------------------------------------------ sending

    fun addFiles(list: List<File>) {
        val flat = list.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.take(2000).toList() else listOf(f) }.filter { it.isFile }
        outgoing.update { cur -> cur + flat.filter { f -> cur.none { it.absolutePath == f.absolutePath } } }
    }

    fun remove(f: File) = outgoing.update { l -> l.filter { it != f } }
    fun clearOutgoing() { outgoing.value = emptyList() }

    /** Drive files: fetched into the cache first, then queued. */
    fun queueDriveFiles(items: List<FileItem>, transfers: DesktopTransfers) {
        if (items.isEmpty()) return
        preparing.update { it + items.size }
        scope.launch {
            var failed = 0
            items.forEach { item ->
                runCatching { transfers.toCache(item) }.onSuccess { addFiles(listOf(it)) }.onFailure { failed++; AppLog.e("nearby", "couldn't fetch a drive file to send", it) }
                preparing.update { (it - 1).coerceAtLeast(0) }
            }
            if (failed > 0) onError("Couldn't get $failed file${if (failed == 1) "" else "s"} from your drive")
        }
    }

    fun send(peer: LanPeer) {
        if (busy) return
        val list = outgoing.value.filter { it.isFile }
        if (list.isEmpty()) return
        sending = true
        files.value = emptyList()
        phase.value = SharePhase.Asking(peer.name, null, sending = true)
        val tid = lan.newTransferId()
        current = tid
        lan.send(tid, peer, list.map { f -> LanOutgoing(LanFileInfo(f.name, f.length(), DesktopTransfers.mimeOf(f))) { f.inputStream() } }, listener)
    }

    // ------------------------------------------------------------------ receiving

    fun accept() {
        val r = request.value ?: return
        request.value = null
        if (busy) { lan.decline(); return }
        current = r.transfer
        sending = false
        files.value = emptyList()
        phase.value = SharePhase.Asking(r.peer, r.code, sending = false)
        lan.accept()
    }

    fun decline() { request.value = null; lan.decline() }

    /** Stops the running transfer; queued files stay so another device can be picked. */
    fun cancel() {
        current = -1
        lan.cancel()
        phase.value = SharePhase.Idle
        files.value = emptyList()
    }

    /** Back to the start after Done / Failed. */
    fun finish() {
        val p = phase.value
        if (p is SharePhase.Done && p.sending) outgoing.value = emptyList()
        phase.value = SharePhase.Idle
        files.value = emptyList()
    }

    private fun onEvent(tid: Long, e: LanEvent) {
        when (e) {
            is LanEvent.Request -> {
                val r = ShareRequest(tid, e.peer, e.kind, e.code, e.files)
                request.value = r
                onRequest(r)
                return
            }
            is LanEvent.RequestGone -> {
                if (request.value?.transfer == tid) { request.value = null; onRequestGone(e.peer) }
                if (tid == current && phase.value is SharePhase.Asking) phase.value = SharePhase.Failed("${e.peer} cancelled before sending.", false)
                return
            }
            else -> {}
        }
        if (tid != current) return
        when (e) {
            is LanEvent.Waiting -> phase.value = SharePhase.Asking(e.peer, e.code, sending)
            is LanEvent.Started -> {
                files.value = e.files.map { ShareFile(it.name, it.size) }
                phase.value = SharePhase.Moving(e.peer, sending)
            }
            is LanEvent.Progress -> setFile(e.index) { it.copy(bytes = e.bytes, state = ShareFileState.Moving) }
            is LanEvent.FileDone -> {
                val saved = e.saved as? File
                setFile(e.index) { it.copy(bytes = maxOf(it.bytes, it.size), state = ShareFileState.Done, saved = saved) }
                if (saved != null) received.update { (listOf(saved) + it).take(200) }
            }
            is LanEvent.FileFailed -> setFile(e.index) { it.copy(state = ShareFileState.Failed) }
            is LanEvent.Finished -> {
                files.update { l -> l.map { if (it.state == ShareFileState.Done) it else it.copy(state = ShareFileState.Failed) } }
                val done = files.value.filter { it.state == ShareFileState.Done }
                phase.value = if (done.isEmpty()) SharePhase.Failed("Nothing was transferred. Try again.", sending) else SharePhase.Done(e.peer, sending)
                if (!sending && done.isNotEmpty()) onReceived(e.peer, done.mapNotNull { it.saved })
            }
            is LanEvent.Declined -> phase.value = SharePhase.Failed("${e.peer} declined the files.", sending)
            is LanEvent.Failed -> {
                AppLog.w("nearby", "transfer failed: ${e.message}")
                files.update { l -> l.map { if (it.state == ShareFileState.Done) it else it.copy(state = ShareFileState.Failed) } }
                phase.value = SharePhase.Failed(e.message, sending)
            }
            else -> {}
        }
    }

    private fun setFile(i: Int, change: (ShareFile) -> ShareFile) = files.update { l -> l.mapIndexed { k, f -> if (k == i) change(f) else f } }

    /** Received files go to the downloads folder, written under a temporary name until complete. */
    private val saver = LanSaver { info ->
        val dir = settings.downloadDir.apply { mkdirs() }
        val name = windowsSafe(info.name)
        val part = File(dir, "$name.${System.nanoTime()}.nudgepart")
        LanTarget(
            BufferedOutputStream(FileOutputStream(part), 256 * 1024),
            commit = {
                synchronized(this) {
                    val dest = unique(dir, name)
                    if (!part.renameTo(dest)) { part.copyTo(dest); part.delete() }
                    dest
                }
            },
            abort = { part.delete() },
        )
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var n = 1
        while (f.exists()) { f = File(dir, "$stem ($n)$ext"); n++ }
        return f
    }

    private fun windowsSafe(name: String): String {
        val stem = name.substringBefore('.').uppercase()
        val reserved = stem in setOf("CON", "PRN", "AUX", "NUL") || Regex("(COM|LPT)[1-9]").matches(stem)
        return if (reserved) "_$name" else name
    }
}
