package com.nudge.drive.data

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Progress of preparing files (download into the cache) before sharing or sending them nearby. */
data class SharePrep(val title: String, val progress: Float, val op: Int = 0)

/**
 * Shares real file content (not just a link) with any app, or hands it to Nudge Nearby.
 * Exactly one preparation runs at a time; each has an [op] id, so a cancelled or replaced one
 * can never update the dialog or act on its result.
 */
class ShareOut(
    private val context: Context,
    private val repo: Repository,
    private val transfers: Transfers,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit,
) {
    val state = MutableStateFlow<SharePrep?>(null)
    private var job: Job? = null
    @Volatile private var op = 0

    fun cancel() { op++; job?.cancel(); job = null; state.value = null }

    private fun prepare(files: List<FileItem>, then: suspend (List<Uri>) -> Unit) {
        if (files.isEmpty() || job?.isActive == true) return
        val title = if (files.size == 1) files[0].name else "${files.size} files"
        val my = ++op
        state.value = SharePrep(title, 0f, my)
        job = scope.launch {
            try {
                val uris = cache(files) { p -> if (op == my) state.value = SharePrep(title, p, my) }
                if (op != my) return@launch
                then(uris)
            } catch (e: CancellationException) {
                // cancelled by the user
            } catch (e: Exception) {
                if (op == my) toast((e as? ApiException)?.friendly ?: "Couldn't prepare $title")
            } finally {
                if (op == my) state.value = null
            }
        }
    }

    fun share(activity: Activity, files: List<FileItem>) = prepare(files) { uris ->
        val title = if (files.size == 1) files[0].name else "${files.size} files"
        val links = runCatching { repo.sharedLinks() }.getOrDefault(emptyList()).associate { it.file.id to it.url }
        val text = files.mapNotNull { links[it.id] }.joinToString(System.lineSeparator())
        withContext(Dispatchers.Main) { state.value = null; activity.startActivity(chooser(uris, files, text, title)) }
    }

    /** Downloads [files] and hands them over for Nudge Nearby. */
    fun sendNearby(files: List<FileItem>, onReady: (List<OutgoingItem>) -> Unit) = prepare(files) { uris ->
        val items = files.zip(uris).map { (f, u) -> OutgoingItem(u, Transfers.exportName(f), f.size, Transfers.mimeFor(f)) }
        withContext(Dispatchers.Main) { onReady(items) }
    }

    /** Downloads (or reuses) cached copies and returns shareable content Uris. */
    suspend fun cache(files: List<FileItem>, onProgress: (Float) -> Unit): List<Uri> {
        val total = files.sumOf { it.size.coerceAtLeast(1) }.toFloat()
        var done = 0L
        return files.map { f ->
            val out = transfers.toCache(f) { p -> onProgress(((done + p * f.size.coerceAtLeast(1)) / total).coerceIn(0f, 1f)) }
            done += f.size.coerceAtLeast(1)
            FileProvider.getUriForFile(context, context.packageName + ".files", out)
        }
    }

    private fun chooser(uris: List<Uri>, files: List<FileItem>, text: String, title: String): Intent {
        val mimes = files.map { Transfers.mimeFor(it) }
        val type = when {
            mimes.size == 1 -> mimes[0]
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it.startsWith("video/") } -> "video/*"
            mimes.all { it.startsWith("image/") || it.startsWith("video/") } -> "*/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        send.type = type
        if (text.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
        send.putExtra(Intent.EXTRA_SUBJECT, title)
        send.clipData = ClipData.newRawUri(title, uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share $title").apply {
            // Saving a Nudge file back into Nudge makes no sense; Nudge Nearby stays available.
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context.packageName, "com.nudge.drive.SaveToNudge")))
        }
    }
}
