package com.nudge.drive.desktop.data

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.mov.QuickTimeDirectory
import com.drew.metadata.mp4.Mp4Directory
import com.nudge.drive.data.ApiClient
import com.nudge.drive.data.ApiException
import com.nudge.drive.data.ChunkResponse
import com.nudge.drive.data.FileItem
import com.nudge.drive.data.Repository
import com.nudge.drive.data.ResumableInit
import com.nudge.drive.data.UploadResponse
import com.nudge.drive.data.asLongOrZero
import com.nudge.drive.data.jsonOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
import java.io.RandomAccessFile
import java.net.URLConnection
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TransferKind { Upload, Download }
enum class TransferState { Queued, Running, Done, Failed, Cancelled }

data class Transfer(
    val id: String = UUID.randomUUID().toString(),
    val kind: TransferKind,
    val name: String,
    val size: Long,
    val progress: Float = 0f,
    val state: TransferState = TransferState.Queued,
    val error: String? = null,
    /** Downloads: where it was saved. */
    val localPath: String? = null,
    val fileId: String? = null,
) {
    val active get() = state == TransferState.Queued || state == TransferState.Running
}

/**
 * Upload + download engine for the desktop app. Uploads run two at a time; Google Drive targets use
 * 8 MiB resumable chunks (resumed from the server's offset after a network drop), S3 uses multipart.
 */
class DesktopTransfers(private val api: ApiClient, private val repo: Repository, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<Transfer>>(emptyList())
    val items: StateFlow<List<Transfer>> = _items
    private val _uploaded = MutableSharedFlow<FileItem>(extraBufferCapacity = 64)
    /** Every file as soon as it lands, so lists can refresh. */
    val uploaded: SharedFlow<FileItem> = _uploaded
    /** Finished or failed transfers (tray notifications hook in here). */
    var onFinished: ((Transfer) -> Unit)? = null

    private data class UploadJob(val transfer: Transfer, val file: File, val folderId: String?)
    private val queue = Channel<UploadJob>(Channel.UNLIMITED)
    private val running = ConcurrentHashMap<String, Job>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    init {
        repeat(2) { scope.launch(Dispatchers.IO) { for (job in queue) if (job.transfer.id !in cancelled) runUpload(job) } }
    }

    private fun set(id: String, f: (Transfer) -> Transfer) = _items.update { l -> l.map { if (it.id == id) f(it) else it } }
    /** A worker's own state change. Cancelled is final: a worker that finishes after Cancel must not turn it into Done. */
    private fun move(id: String, f: (Transfer) -> Transfer) = set(id) { if (it.state == TransferState.Cancelled) it else f(it) }
    private fun finished(id: String) { _items.value.firstOrNull { it.id == id }?.let { onFinished?.invoke(it) } }

    fun clearFinished() = _items.update { l -> l.filter { it.active } }
    fun cancel(id: String) {
        cancelled += id
        running[id]?.cancel()
        set(id) { if (it.active) it.copy(state = TransferState.Cancelled) else it }
    }

    // ------------------------------------------------------------------ uploads
    /** Queues files; folders are recreated on the drive with everything inside them. */
    fun upload(paths: List<File>, folderId: String?) {
        val (dirs, files) = paths.filter { it.exists() }.partition { it.isDirectory }
        files.forEach { enqueue(it, folderId) }
        if (dirs.isNotEmpty()) scope.launch(Dispatchers.IO) { dirs.forEach { uploadTree(it, folderId) } }
    }

    private fun enqueue(f: File, folderId: String?) {
        if (!f.canRead()) return
        val t = Transfer(kind = TransferKind.Upload, name = f.name, size = f.length())
        _items.update { it + t }
        queue.trySend(UploadJob(t, f, folderId))
    }

    private suspend fun uploadTree(dir: File, parentId: String?) {
        val remote = runCatching { repo.createFolder(dir.name, parentId, "#8b72f5") }.getOrElse {
            // Same name already there: reuse it.
            repo.folders(parentId).firstOrNull { f -> f.name == dir.name } ?: return
        }
        dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (child.isHidden || child.name.startsWith(".")) return@forEach
            if (child.isDirectory) uploadTree(child, remote.id) else enqueue(child, remote.id)
        }
    }

    private suspend fun runUpload(job: UploadJob) {
        val t = job.transfer
        move(t.id) { it.copy(state = TransferState.Running) }
        // Registered before it starts, so a Cancel at any moment finds it (or sees it was cancelled already).
        val worker = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val file = uploadDirect(job.file, job.folderId) { p -> move(t.id) { it.copy(progress = p) } }
                move(t.id) { it.copy(state = TransferState.Done, progress = 1f, fileId = file.id) }
                _uploaded.tryEmit(file)
            } catch (e: CancellationException) {
                set(t.id) { it.copy(state = TransferState.Cancelled) }
            } catch (e: Exception) {
                move(t.id) { it.copy(state = TransferState.Failed, error = (e as? ApiException)?.friendly ?: e.message ?: "Upload failed") }
            }
        }
        running[t.id] = worker
        if (t.id in cancelled) worker.cancel() else worker.start()
        worker.join()
        running.remove(t.id)
        finished(t.id)
    }

    /** Uploads one file now (used by the queue and by folder backup). */
    suspend fun uploadDirect(file: File, folderId: String?, onProgress: (Float) -> Unit = {}): FileItem {
        val size = file.length()
        if (size <= 0) throw IllegalStateException("${file.name} is empty")
        val mime = mimeOf(file)
        val taken = withContext(Dispatchers.IO) { takenAt(file, mime) }?.toString()
        val init = api.post<ResumableInit>(
            "/uploads/resumable/init",
            jsonOf("fileName" to file.name, "mimeType" to mime, "sizeBytes" to size.toString(), "folderId" to folderId, "takenAt" to taken),
        )
        return if (init.provider == "google_drive") chunked(file, size, init.sessionId, onProgress)
        else multipart(file, size, mime, folderId, taken, onProgress)
    }

    private suspend fun chunked(file: File, total: Long, sessionId: String, onProgress: (Float) -> Unit): FileItem = withContext(Dispatchers.IO) {
        val chunk = 8L * 1024 * 1024
        val buf = ByteArray(chunk.toInt())
        var offset = 0L
        var attempts = 0
        RandomAccessFile(file, "r").use { raf ->
            while (true) {
                ensureActive()
                val want = minOf(chunk, total - offset).toInt()
                raf.seek(offset)
                raf.readFully(buf, 0, want)
                val end = offset + want - 1
                val req = Request.Builder().url(api.url("/uploads/resumable/chunk/$sessionId"))
                    .header("Content-Range", "bytes $offset-$end/$total")
                    .put(buf.toRequestBody("application/octet-stream".toMediaType(), 0, want)).build()
                val result = runCatching { api.http.newCall(req).execute().use { api.readOrThrow(it) } }
                if (result.isFailure) {
                    if (++attempts > 4) throw result.exceptionOrNull()!!
                    kotlinx.coroutines.delay(1500L * attempts)
                    // Ask the server where Google really is, then continue from there.
                    val st = api.get<ChunkResponse>("/uploads/resumable/status/$sessionId")
                    if (st.status == "failed") throw result.exceptionOrNull()!!
                    st.file?.takeIf { st.status == "completed" }?.let { return@withContext it }
                    offset = st.offset.asLongOrZero()
                    continue
                }
                attempts = 0
                val resp = api.decode<ChunkResponse>(result.getOrThrow())
                val done = resp.file
                if (resp.status == "completed" && done != null) return@withContext done
                offset = end + 1
                onProgress(offset.toFloat() / total)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    private suspend fun multipart(file: File, size: Long, mime: String, folderId: String?, takenAt: String?, onProgress: (Float) -> Unit): FileItem = withContext(Dispatchers.IO) {
        val ctx = coroutineContext
        val fileBody = object : RequestBody() {
            override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                file.source().use { src ->
                    var sent = 0L
                    val b = okio.Buffer()
                    while (true) {
                        if (!ctx.isActive) throw java.io.IOException("Cancelled")
                        val n = src.read(b, 64 * 1024); if (n < 0) break
                        sink.write(b, n); sent += n
                        onProgress(sent.toFloat() / size)
                    }
                }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("sizeBytes", size.toString())
            .addFormDataPart("fileName", file.name)
            .addFormDataPart("mimeType", mime)
            .apply { folderId?.let { addFormDataPart("folderId", it) } }
            .apply { takenAt?.let { addFormDataPart("takenAt", it) } }
            .addFormDataPart("file", file.name, fileBody)
            .build()
        val text = api.http.newCall(Request.Builder().url(api.url("/uploads")).post(body).build()).execute().use { api.readOrThrow(it) }
        val resp = api.decode<UploadResponse>(text)
        resp.file ?: resp.files.firstOrNull() ?: throw ApiException(400, resp.failed.firstOrNull()?.code, resp.failed.firstOrNull()?.message ?: "Upload failed")
    }

    // ------------------------------------------------------------------ downloads
    /** Saves files into the download folder (Downloads\Nudge Drive by default). */
    fun download(files: List<FileItem>) = files.forEach { f ->
        startDownload(exportName(f), f.size, Request.Builder().url(api.url("/files/${f.id}/download")).build(), f.id)
    }

    fun downloadZip(ids: List<String>) =
        startDownload("nudge-drive-${System.currentTimeMillis() / 1000}.zip", -1, Request.Builder().url(api.url("/files/batch-download")).post(api.jsonBody(jsonOf("fileIds" to ids))).build(), null)

    private fun startDownload(name: String, size: Long, req: Request, fileId: String?) {
        val dir = settings.downloadDir.apply { mkdirs() }
        val out = uniqueFile(dir, safeName(name))
        val t = Transfer(kind = TransferKind.Download, name = out.name, size = size, state = TransferState.Running, localPath = out.absolutePath, fileId = fileId)
        _items.update { it + t }
        val worker = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val tmp = File(dir, out.name + ".nudgepart")
            try {
                api.http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) api.readOrThrow(r)
                    val total = if (size > 0) size else r.body!!.contentLength()
                    tmp.outputStream().use { o -> r.body!!.byteStream().use { i -> copy(i, o) { done -> ensureActive(); if (total > 0) move(t.id) { it.copy(progress = done.toFloat() / total) } } } }
                }
                Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                move(t.id) { it.copy(state = TransferState.Done, progress = 1f) }
            } catch (e: CancellationException) {
                set(t.id) { it.copy(state = TransferState.Cancelled) }
            } catch (e: Exception) {
                move(t.id) { it.copy(state = TransferState.Failed, error = (e as? ApiException)?.friendly ?: e.message) }
            } finally {
                tmp.delete()
            }
        }
        running[t.id] = worker
        worker.invokeOnCompletion { running.remove(t.id); finished(t.id) }
        if (t.id in cancelled) worker.cancel() else worker.start()
    }

    // ------------------------------------------------------------------ cache (viewers)
    private val inflight = ConcurrentHashMap<String, Deferred<File>>()

    /** Original file in the cache (viewers, "open with"). */
    suspend fun toCache(file: FileItem, onProgress: (Float) -> Unit = {}): File =
        fetchToCache("/files/${file.id}/download", File(Platform.cacheDir, "files/${file.id}"), safeName(exportName(file)), file.size, onProgress)

    /** PDF rendering of any document (Word, Excel, PowerPoint, Google Docs), converted on the server. */
    suspend fun documentPreview(file: FileItem, fresh: Boolean = false, onProgress: (Float) -> Unit = {}): File {
        val dir = File(Platform.cacheDir, "previews")
        if (fresh) withContext(Dispatchers.IO) { File(dir, "${file.id}.pdf").delete() }
        return fetchToCache("/files/${file.id}/document-preview", dir, "${file.id}.pdf", if (file.mimeType == "application/pdf") file.size else 0, onProgress)
    }

    fun forgetCached(fileId: String) {
        File(Platform.cacheDir, "files/$fileId").deleteRecursively()
        File(Platform.cacheDir, "previews/$fileId.pdf").delete()
    }

    private suspend fun fetchToCache(path: String, dir: File, name: String, knownSize: Long, onProgress: (Float) -> Unit): File {
        val out = File(dir, name)
        while (true) {
            inflight[out.path]?.let { return it.await() }
            val job = scope.async(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                dir.mkdirs()
                if (out.exists() && out.length() > 0) return@async out
                val tmp = File(dir, "$name.${System.nanoTime()}.partial")
                try {
                    api.http.newCall(Request.Builder().url(api.url(path)).build()).execute().use { r ->
                        if (!r.isSuccessful) api.readOrThrow(r)
                        val total = if (knownSize > 0) knownSize else r.body!!.contentLength()
                        tmp.outputStream().use { o -> r.body!!.byteStream().use { i -> copy(i, o) { ensureActive(); if (total > 0) onProgress(it.toFloat() / total) } } }
                    }
                    if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
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
        val buf = ByteArray(128 * 1024); var done = 0L; var last = 0L
        while (true) {
            val n = input.read(buf); if (n < 0) break
            out.write(buf, 0, n); done += n
            if (done - last > 512 * 1024) { progress(done); last = done }
        }
        progress(done)
    }

    companion object {
        fun exportName(f: FileItem): String = when (f.mimeType) {
            "application/vnd.google-apps.document" -> f.name + ".docx"
            "application/vnd.google-apps.presentation" -> f.name + ".pptx"
            "application/vnd.google-apps.spreadsheet" -> f.name + ".xlsx"
            "application/vnd.google-apps.drawing" -> f.name + ".png"
            else -> f.name
        }

        fun safeName(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "file" }

        fun uniqueFile(dir: File, name: String): File {
            var f = File(dir, name)
            if (!f.exists()) return f
            val base = name.substringBeforeLast('.', name); val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
            var i = 2
            while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
            return f
        }

        fun mimeOf(f: File): String =
            runCatching { Files.probeContentType(f.toPath()) }.getOrNull()
                ?: URLConnection.guessContentTypeFromName(f.name)
                ?: extraMimes[f.extension.lowercase()]
                ?: "application/octet-stream"

        private val extraMimes = mapOf(
            "heic" to "image/heic", "webp" to "image/webp", "mkv" to "video/x-matroska", "mp4" to "video/mp4", "mov" to "video/quicktime",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "pdf" to "application/pdf", "zip" to "application/zip", "md" to "text/markdown", "json" to "application/json",
        )

        /** When a photo/video was taken (EXIF / QuickTime), else the file's modified time. */
        fun takenAt(f: File, mime: String): Instant? {
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) return null
            val meta = runCatching { ImageMetadataReader.readMetadata(f) }.getOrNull()
            val fromMeta = meta?.let { m ->
                m.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.getDateOriginal(java.util.TimeZone.getDefault())
                    ?: m.getFirstDirectoryOfType(QuickTimeDirectory::class.java)?.getDate(QuickTimeDirectory.TAG_CREATION_TIME)
                    ?: m.getFirstDirectoryOfType(Mp4Directory::class.java)?.getDate(Mp4Directory.TAG_CREATION_TIME)
            }?.toInstant()?.takeIf { it.isAfter(Instant.parse("1971-01-01T00:00:00Z")) }
            return fromMeta ?: f.lastModified().takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        }
    }
}
