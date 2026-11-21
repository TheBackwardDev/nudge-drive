package com.nudge.drive.data

import kotlinx.coroutines.ensureActive

import kotlinx.coroutines.async

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class TransferKind { Upload, Download }
enum class TransferState { Queued, Running, Done, Failed }

data class Transfer(
    val id: String = UUID.randomUUID().toString(),
    val kind: TransferKind,
    val name: String,
    val size: Long,
    val progress: Float = 0f,
    val state: TransferState = TransferState.Queued,
    val error: String? = null,
)

/**
 * Upload + download engine shared by every screen. Uploads run one at a time in the app scope so they
 * keep going while the user browses; Google targets use 5 MiB resumable chunks, S3 falls back to multipart.
 */
class Transfers(private val context: Context, private val api: ApiClient, private val scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<Transfer>>(emptyList())
    val items: StateFlow<List<Transfer>> = _items
    /** Called once per finished/failed transfer (notification centre hooks in here). */
    var onFinished: ((Transfer, String?) -> Unit)? = null
    private fun finished(id: String, fileId: String? = null) { _items.value.firstOrNull { it.id == id }?.let { onFinished?.invoke(it, fileId) } }
    private val _uploaded = MutableSharedFlow<FileItem>(extraBufferCapacity = 16)
    /** Emits each file as soon as it lands, so lists can refresh. */
    val uploaded: SharedFlow<FileItem> = _uploaded

    private data class Job(val transfer: Transfer, val uri: Uri, val mime: String, val folderId: String?)
    private val queue = Channel<Job>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) { for (job in queue) runUpload(job) }
    }

    fun clearFinished() = _items.update { list -> list.filter { it.state == TransferState.Running || it.state == TransferState.Queued } }

    private fun set(id: String, f: (Transfer) -> Transfer) = _items.update { l -> l.map { if (it.id == id) f(it) else it } }

    // ------------------------------------------------------------------ uploads
    /** Queues uploads. Files the app can't read (permission revoked, deleted) are reported, never crash the app. */
    fun upload(uris: List<Uri>, folderId: String?) {
        var unreadable = 0
        uris.forEach { uri ->
            val info = runCatching { meta(uri) }.getOrNull()
            if (info == null) { unreadable++; return@forEach }
            val (name, size) = info
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            val t = Transfer(kind = TransferKind.Upload, name = name, size = size)
            _items.update { it + t }
            queue.trySend(Job(t, uri, mime, folderId))
        }
        if (unreadable > 0) onUnreadable?.invoke(unreadable)
        if (uris.size > unreadable) TransferService.start(context)
    }

    /** Called with the number of shared files that couldn't be read. */
    var onUnreadable: ((Int) -> Unit)? = null

    private fun meta(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"; var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        // Opening it proves we can actually read it (throws SecurityException / FileNotFound otherwise).
        val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: throw java.io.FileNotFoundException(uri.toString())
        if (size < 0) size = length.coerceAtLeast(0L)
        return name to size
    }

    private suspend fun runUpload(job: Job) {
        val t = job.transfer
        set(t.id) { it.copy(state = TransferState.Running) }
        try {
            val file = uploadDirect(job.uri, t.name, t.size, job.mime, job.folderId) { p -> set(t.id) { it.copy(progress = p) } }
            set(t.id) { it.copy(state = TransferState.Done, progress = 1f) }
            _uploaded.tryEmit(file)
            finished(t.id, file.id)
        } catch (e: Exception) {
            val msg = (e as? ApiException)?.friendly ?: e.message ?: "Upload failed"
            set(t.id) { it.copy(state = TransferState.Failed, error = msg) }
            finished(t.id)
        }
    }

    /** Uploads one document straight away (used by the queue and by auto backup). */
    suspend fun uploadDirect(
        uri: Uri, name: String, size: Long, mime: String, folderId: String?,
        takenAtMs: Long? = null, onProgress: (Float) -> Unit,
    ): FileItem {
        if (size <= 0) throw IllegalStateException("Empty file")
        // Keep the original date (when the photo/video was taken), not the upload date.
        val taken = (takenAtMs ?: withContext(Dispatchers.IO) { takenAtFor(uri) })?.takeIf { it > 0 }
            ?.let { java.time.Instant.ofEpochMilli(it).toString() }
        val init = api.post<ResumableInit>(
            "/uploads/resumable/init",
            jsonOf("fileName" to name, "mimeType" to mime, "sizeBytes" to size.toString(), "folderId" to folderId, "takenAt" to taken),
        )
        return if (init.provider == "google_drive") chunked(uri, size, init.sessionId, onProgress) else multipart(uri, name, size, mime, folderId, taken, onProgress)
    }

    /** Best-known original date of a shared/picked file: MediaStore "date taken", else last modified. */
    private fun takenAtFor(uri: Uri): Long? = runCatching {
        val cols = arrayOf(android.provider.MediaStore.MediaColumns.DATE_TAKEN, android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
        context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            c.getLongOrNull(0)?.takeIf { it > 0 } ?: c.getLongOrNull(1)?.takeIf { it > 0 }?.let { it * 1000 }
        }
    }.getOrNull() ?: runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLongOrNull(0) else null
        }
    }.getOrNull()

    private fun android.database.Cursor.getLongOrNull(i: Int): Long? = if (i < 0 || isNull(i)) null else getLong(i)

    private suspend fun chunked(uri: Uri, total: Long, sessionId: String, onProgress: (Float) -> Unit): FileItem = withContext(Dispatchers.IO) {
        val chunkSize = 5L * 1024 * 1024
        var offset = 0L
        var input: InputStream = context.contentResolver.openInputStream(uri) ?: error("Cannot read file")
        val buf = ByteArray(chunkSize.toInt())
        var attempts = 0
        try {
            while (true) {
                val want = minOf(chunkSize, total - offset).toInt()
                var read = 0
                while (read < want) { val n = input.read(buf, read, want - read); if (n < 0) break; read += n }
                if (read <= 0) error("File ended early")
                val end = offset + read - 1
                val req = Request.Builder()
                    .url(api.url("/uploads/resumable/chunk/$sessionId"))
                    .header("Content-Range", "bytes $offset-$end/$total")
                    .put(buf.toRequestBody("application/octet-stream".toMediaType(), 0, read))
                    .build()
                val result = runCatching { api.http.newCall(req).execute().use { r -> api.readOrThrow(r) } }
                if (result.isFailure) {
                    if (++attempts > 3) throw result.exceptionOrNull()!!
                    // Ask the server where Google really is, then reopen the stream at that offset.
                    val st = api.get<ChunkResponse>("/uploads/resumable/status/$sessionId")
                    if (st.status == "failed") throw result.exceptionOrNull()!!
                    offset = st.offset.asLongOrZero()
                    input.close(); input = context.contentResolver.openInputStream(uri)!!
                    input.skipFully(offset)
                    continue
                }
                attempts = 0
                val resp = api.decode<ChunkResponse>(result.getOrThrow())
                val done = resp.file; if (resp.status == "completed" && done != null) return@withContext done
                offset = end + 1
                onProgress(offset.toFloat() / total)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        } finally { input.close() }
    }

    private suspend fun multipart(uri: Uri, name: String, size: Long, mime: String, folderId: String?, takenAt: String?, onProgress: (Float) -> Unit): FileItem = withContext(Dispatchers.IO) {
        val fileBody = object : RequestBody() {
            override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)!!.source().use { src ->
                    var sent = 0L
                    val b = okio.Buffer()
                    while (true) {
                        val n = src.read(b, 64 * 1024); if (n < 0) break
                        sink.write(b, n); sent += n
                        onProgress(sent.toFloat() / size)
                    }
                }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sizeBytes", size.toString())
            .addFormDataPart("fileName", name)
            .addFormDataPart("mimeType", mime)
            .apply { folderId?.let { addFormDataPart("folderId", it) } }
            .apply { takenAt?.let { addFormDataPart("takenAt", it) } }
            .addFormDataPart("file", name, fileBody)
            .build()
        val text = api.http.newCall(Request.Builder().url(api.url("/uploads")).post(body).build()).execute().use { api.readOrThrow(it) }
        val resp = api.decode<UploadResponse>(text)
        resp.file ?: resp.files.firstOrNull() ?: throw ApiException(400, resp.failed.firstOrNull()?.code, resp.failed.firstOrNull()?.message ?: "Upload failed")
    }

    // ------------------------------------------------------------------ downloads
    /** Saves a file into the phone's Downloads/Nudge Drive folder with progress in the transfer list. */
    fun download(file: FileItem) = scope.launch(Dispatchers.IO) {
        val name = exportName(file)
        stream(name, file.size, mimeFor(file), Request.Builder().url(api.url("/files/${file.id}/download")).build())
    }

    fun downloadZip(ids: List<String>) = scope.launch(Dispatchers.IO) {
        val req = Request.Builder().url(api.url("/files/batch-download")).post(api.jsonBody(jsonOf("fileIds" to ids))).build()
        stream("nudge-drive-${System.currentTimeMillis() / 1000}.zip", -1, "application/zip", req)
    }

    private fun stream(name: String, size: Long, mime: String, req: Request) {
        val t = Transfer(kind = TransferKind.Download, name = name, size = size, state = TransferState.Running)
        _items.update { it + t }
        TransferService.start(context) // after the item is listed, so the service never sees an empty list
        try {
            api.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) api.readOrThrow(r)
                val body = r.body!!
                val total = if (size > 0) size else body.contentLength()
                openDownloadOutput(name, mime).use { out ->
                    body.byteStream().use { input -> copy(input, out) { done -> if (total > 0) set(t.id) { it.copy(progress = done.toFloat() / total) } } }
                }
            }
            set(t.id) { it.copy(state = TransferState.Done, progress = 1f) }
            finished(t.id)
        } catch (e: Exception) {
            set(t.id) { it.copy(state = TransferState.Failed, error = (e as? ApiException)?.friendly ?: e.message) }
            finished(t.id)
        }
    }

    private fun openDownloadOutput(name: String, mime: String): OutputStream {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nudge Drive")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create download")
            context.contentResolver.openOutputStream(uri)!!
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Nudge Drive").apply { mkdirs() }
            File(dir, name).outputStream()
        }
    }

    /** Downloads into the app cache (for previews / "open with"), returning the local file. */
    suspend fun toCache(file: FileItem, onProgress: (Float) -> Unit = {}): File =
        fetchToCache("/files/${file.id}/download", File(context.cacheDir, "files/${file.id}"), exportName(file).replace('/', '_'), file.size, onProgress)

    /** A PDF rendering of any document (Word, Excel, PowerPoint, Google Docs, PDF), converted on the server. */
    suspend fun documentPreview(file: FileItem, fresh: Boolean = false, onProgress: (Float) -> Unit = {}): File {
        val dir = File(context.cacheDir, "previews")
        // Fresh = the file may have been edited in Google Docs; the server checks the version and is fast when unchanged.
        if (fresh) withContext(Dispatchers.IO) { File(dir, "${file.id}.pdf").delete() }
        return fetchToCache("/files/${file.id}/document-preview", dir, "${file.id}.pdf", if (file.mimeType == "application/pdf") file.size else 0, onProgress)
    }

    /** One download per cache file at a time; everyone else waits for the same result. */
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<File>>()

    private suspend fun fetchToCache(path: String, dir: File, name: String, knownSize: Long, onProgress: (Float) -> Unit): File {
        val out = File(dir, name)
        while (true) {
            inflight[out.path]?.let { return it.await() }
            val job = scope.async(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                dir.mkdirs()
                if (out.exists() && out.length() > 0) return@async out
                // Unique temp file: a half-written download can never be mistaken for a finished one.
                val tmp = File(dir, "$name.${System.nanoTime()}.partial")
                try {
                    api.http.newCall(Request.Builder().url(api.url(path)).build()).execute().use { r ->
                        if (!r.isSuccessful) api.readOrThrow(r)
                        val total = if (knownSize > 0) knownSize else r.body!!.contentLength()
                        tmp.outputStream().use { o -> r.body!!.byteStream().use { i -> copy(i, o) { ensureActive(); if (total > 0) onProgress(it.toFloat() / total) } } }
                    }
                    if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true) }
                    out
                } finally { tmp.delete() }
            }
            if (inflight.putIfAbsent(out.path, job) == null) {
                job.invokeOnCompletion { inflight.remove(out.path, job) }
                job.start()
                return job.await()
            }
        }
    }

    private inline fun copy(input: InputStream, out: OutputStream, progress: (Long) -> Unit) {
        val buf = ByteArray(64 * 1024); var done = 0L; var last = 0L
        while (true) {
            val n = input.read(buf); if (n < 0) break
            out.write(buf, 0, n); done += n
            if (done - last > 256 * 1024) { progress(done); last = done }
        }
        progress(done)
    }

    companion object {
        fun exportName(f: FileItem): String = when (f.mimeType) {
            "application/vnd.google-apps.document", "application/vnd.google-apps.presentation" -> f.name + ".pdf"
            "application/vnd.google-apps.spreadsheet" -> f.name + ".xlsx"
            "application/vnd.google-apps.drawing" -> f.name + ".png"
            else -> f.name
        }
        fun mimeFor(f: FileItem): String = when (f.mimeType) {
            "application/vnd.google-apps.document", "application/vnd.google-apps.presentation" -> "application/pdf"
            "application/vnd.google-apps.spreadsheet" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "application/vnd.google-apps.drawing" -> "image/png"
            else -> f.mimeType
        }
    }
}

private fun InputStream.skipFully(n: Long) {
    var left = n
    while (left > 0) { val s = skip(left); if (s <= 0) { if (read() < 0) break; left-- } else left -= s }
}
