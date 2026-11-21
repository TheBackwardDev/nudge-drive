package com.nudge.drive.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo/video folder on the phone (MediaStore "bucket"): Camera, Screenshots, WhatsApp Images… */
data class DeviceFolder(
    val bucketId: Long, val name: String, val count: Int, val videos: Int,
    val cover: Uri, val latestMs: Long, val relativePath: String,
) {
    val isCamera: Boolean get() = name.equals("Camera", true) || relativePath.startsWith("DCIM/Camera", true)
}

/** One photo or video on the phone. [key] identifies this exact version for the backup index. */
data class DeviceMediaItem(
    val id: Long, val uri: Uri, val name: String, val size: Long, val mime: String,
    val takenMs: Long, val modifiedSec: Long, val isVideo: Boolean, val durationMs: Long,
) { val key: String get() = "ms:$id|$size|$modifiedSec" }

enum class MediaAccess { Full, Partial, None }

/** Reads the phone's photo library through MediaStore. */
object DeviceMedia {
    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(ctx: Context): MediaAccess {
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && (granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO)) -> MediaAccess.Full
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.Full
            else -> MediaAccess.None
        }
    }

    private val mediaSelection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE},${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private fun itemUri(id: Long, video: Boolean): Uri =
        ContentUris.withAppendedId(if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /** All photo/video folders, newest activity first. */
    suspend fun folders(ctx: Context): List<DeviceFolder> = withContext(Dispatchers.IO) {
        if (access(ctx) == MediaAccess.None) return@withContext emptyList()
        val proj = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH,
        )
        data class Acc(var name: String, var count: Int = 0, var videos: Int = 0, var cover: Uri? = null, var latest: Long = 0, var path: String = "")
        val map = LinkedHashMap<Long, Acc>()
        runCatching {
            ctx.contentResolver.query(filesUri, proj, mediaSelection, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val bucket = c.getLong(1)
                    val video = c.getInt(3) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val acc = map.getOrPut(bucket) { Acc(c.getString(2) ?: "Other", path = c.getString(5).orEmpty()) }
                    acc.count++; if (video) acc.videos++
                    if (acc.cover == null) { acc.cover = itemUri(c.getLong(0), video); acc.latest = c.getLong(4) * 1000 }
                }
            }
        }
        map.map { (id, a) -> DeviceFolder(id, a.name, a.count, a.videos, a.cover!!, a.latest, a.path) }
            .sortedWith(compareByDescending<DeviceFolder> { it.isCamera }.thenByDescending { it.latestMs })
    }

    /** Photos and videos in one folder (newest first), optionally only those changed after [sinceMs]. */
    suspend fun items(ctx: Context, bucketId: Long, sinceMs: Long = 0): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        if (access(ctx) == MediaAccess.None) return@withContext emptyList()
        val proj = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DURATION,
        )
        val sel = "$mediaSelection AND ${MediaStore.MediaColumns.BUCKET_ID} = ? AND ${MediaStore.MediaColumns.DATE_MODIFIED} >= ?"
        val out = ArrayList<DeviceMediaItem>()
        runCatching {
            ctx.contentResolver.query(filesUri, proj, sel, arrayOf(bucketId.toString(), (sinceMs / 1000).toString()), "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val video = c.getInt(6) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val modified = c.getLong(5)
                    out += DeviceMediaItem(
                        id = c.getLong(0), uri = itemUri(c.getLong(0), video), name = c.getString(1) ?: "media",
                        size = c.getLong(2), mime = c.getString(3) ?: if (video) "video/mp4" else "image/jpeg",
                        takenMs = c.getLong(4).takeIf { it > 0 } ?: modified * 1000, modifiedSec = modified,
                        isVideo = video, durationMs = if (c.isNull(7)) 0 else c.getLong(7),
                    )
                }
            }
        }
        out
    }
}
