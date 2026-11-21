package com.nudge.drive.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable data class User(val id: String, val name: String, val email: String, val status: String? = null, val emailVerified: Boolean? = null)
@Serializable data class AuthResponse(val accessToken: String, val refreshToken: String, val user: User)
@Serializable data class AccessResponse(val accessToken: String)
@Serializable data class MeResponse(val user: User)
@Serializable data class UrlResponse(val url: String? = null)
@Serializable data class StatusResponse(val status: String? = null)
@Serializable data class GoogleNativeConfig(val clientId: String, val scopes: List<String> = emptyList())
@Serializable data class NativeConnectResponse(val status: String? = null, val email: String? = null)
@Serializable data class DeleteCodeResponse(val status: String? = null, val email: String? = null)

@Serializable data class AccountRef(val id: String? = null, val email: String = "", val provider: String = "")
@Serializable data class FolderRef(val id: String, val name: String)

@Serializable
data class FileItem(
    val id: String,
    val connectedAccountId: String? = null,
    val folderId: String? = null,
    val provider: String = "google_drive",
    val name: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: String = "0",
    val status: String = "active",
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
    /** When the photo/video was taken (EXIF or the phone's media date); null for other files. */
    val takenAt: String? = null,
    val connectedAccount: AccountRef? = null,
    val folder: FolderRef? = null,
) {
    val size: Long get() = sizeBytes.toLongOrNull() ?: 0L
    /** Date shown in timelines: when it was taken if known, otherwise when it was added to the drive. */
    val momentAt: String get() = takenAt ?: createdAt
}

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val color: String = "#3b82f6",
    val iconUrl: String? = null,
    val parentId: String? = null,
    val providerFolderId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class StorageAccount(
    val id: String = "",
    val connectedAccountId: String = "",
    val totalBytes: String? = null,
    val usedBytes: String = "0",
    val availableBytes: String? = null,
    val trashBytes: String? = null,
    val lastSyncedAt: String? = null,
)

@Serializable
data class ConnectedAccount(
    val id: String,
    val provider: String,
    val providerAccountId: String = "",
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val status: String = "connected",
    val lastError: String? = null,
    val createdAt: String = "",
    val storageAccount: StorageAccount? = null,
) {
    val used: Long get() = storageAccount?.usedBytes?.toLongOrNull() ?: 0L
    val total: Long? get() = storageAccount?.totalBytes?.toLongOrNull()
    val isGoogle: Boolean get() = provider == "google_drive"
}

@Serializable data class AccountsResponse(val accounts: List<ConnectedAccount> = emptyList())
@Serializable data class QuotaResponse(val quota: StorageAccount)

@Serializable
data class SummaryAccount(
    val id: String, val provider: String, val email: String, val status: String = "",
    val totalBytes: String? = null, val usedBytes: String = "0", val availableBytes: String? = null, val lastSyncedAt: String? = null,
)

@Serializable
data class StorageSummary(
    val totalBytes: String = "0", val usedBytes: String = "0", val availableBytes: String = "0",
    val accounts: List<SummaryAccount> = emptyList(),
) {
    val total: Long get() = totalBytes.toLongOrNull() ?: 0L
    val used: Long get() = usedBytes.toLongOrNull() ?: 0L
    val available: Long get() = availableBytes.toLongOrNull() ?: 0L
}

@Serializable
data class Breakdown(val photo: String = "0", val video: String = "0", val document: String = "0")

@Serializable
data class RoutingPolicy(
    val id: String = "", val mode: String = "most_available",
    val priorityAccountIds: List<String> = emptyList(), val roundRobinCursor: Int = 0,
)
@Serializable data class PolicyResponse(val policy: RoutingPolicy)

@Serializable data class FilesResponse(val files: List<FileItem> = emptyList())
@Serializable data class FileResponse(val file: FileItem)
@Serializable data class FoldersResponse(val folders: List<Folder> = emptyList())
@Serializable data class FolderResponse(val folder: Folder)

@Serializable data class ShareResponse(val url: String, val shareId: String = "")
@Serializable data class ShareLink(val id: String, val url: String? = null, val createdAt: String = "", val expiresAt: String? = null, val file: FileItem)
@Serializable data class SharesResponse(val shares: List<ShareLink> = emptyList())
@Serializable data class PreviewToken(val path: String, val url: String? = null)

@Serializable data class BatchResult(val status: String? = null, val moved: Int? = null, val deleted: Int? = null, val restored: Int? = null)
@Serializable data class SyncResult(val accountId: String = "", val created: Int = 0, val updated: Int = 0, val deleted: Int = 0)
@Serializable data class SyncResponse(val status: String? = null, val results: List<SyncResult> = emptyList())

@Serializable data class InviteUser(val id: String, val name: String, val email: String)
@Serializable
data class InviteTarget(
    val id: String, val name: String, val type: String,
    val mimeType: String? = null, val sizeBytes: String? = null, val folderId: String? = null,
)
@Serializable
data class Invite(
    val id: String, val email: String, val role: String = "viewer", val status: String = "pending",
    val targetType: String = "file", val targetId: String = "", val target: InviteTarget? = null,
    val createdAt: String = "", val user: InviteUser? = null,
)
@Serializable data class InvitesResponse(val sent: List<Invite> = emptyList(), val received: List<Invite> = emptyList())
@Serializable data class InviteResponse(val invite: Invite)

@Serializable
data class ApiKey(
    val id: String, val name: String, val keyPrefix: String = "", val scopes: List<String> = emptyList(),
    val status: String = "active", val lastUsedAt: String? = null, val expiresAt: String? = null,
    val revokedAt: String? = null, val createdAt: String = "",
)
@Serializable data class ApiKeysResponse(val apiKeys: List<ApiKey> = emptyList())
@Serializable data class ApiKeyCreated(val apiKey: ApiKey, val secret: String)

@Serializable
data class AuditLog(
    val id: String, val action: String, val entityType: String = "", val entityId: String? = null,
    val metadata: JsonElement? = null, val createdAt: String = "",
)
@Serializable data class AuditResponse(val logs: List<AuditLog> = emptyList())

@Serializable data class ResumableInit(val sessionId: String, val provider: String, val offset: JsonElement? = null)
@Serializable data class ChunkResponse(val status: String, val offset: JsonElement? = null, val file: FileItem? = null)
@Serializable data class UploadFailure(val fileName: String = "", val code: String = "", val message: String = "")
@Serializable data class UploadResponse(val file: FileItem? = null, val files: List<FileItem> = emptyList(), val failed: List<UploadFailure> = emptyList())

@Serializable data class ApiError(val code: String? = null, val message: String? = null)
@Serializable data class VerifyResponse(val status: String? = null, val emailVerified: Boolean? = null)
@Serializable data class ImportStatus(val state: String = "idle", val folders: Int = 0, val created: Int = 0, val updated: Int = 0, val error: String? = null)

// ---------- Free up space (move files off a full Google Drive) ----------
@Serializable data class FreeUpQuota(val totalBytes: String? = null, val usedBytes: String = "0", val driveBytes: String = "0", val driveTrashBytes: String = "0", val otherBytes: String = "0")
@Serializable data class FreeUpCategory(val key: String, val count: Int = 0, val bytes: String = "0")
@Serializable data class FreeUpFile(val id: String, val name: String, val mimeType: String = "", val sizeBytes: String = "0", val category: String = "other")
@Serializable data class FreeUpDestination(val id: String, val email: String, val displayName: String? = null, val avatarUrl: String? = null, val provider: String = "google_drive", val availableBytes: String? = null) {
    val isGoogle: Boolean get() = provider == "google_drive"
}
@Serializable data class FreeUpTrash(val count: Int = 0, val bytes: String = "0")
@Serializable data class FreeUpAccount(val id: String, val email: String, val displayName: String? = null, val avatarUrl: String? = null, val fullSync: Boolean = false)
@Serializable data class FreeUpJobDest(val id: String, val email: String, val provider: String = "google_drive", val files: Int = 0, val bytes: String = "0")
@Serializable data class FreeUpFailure(val name: String, val reason: String)
@Serializable data class FreeUpJob(
    val id: String, val state: String, val totalFiles: Int = 0, val totalBytes: String = "0", val doneFiles: Int = 0, val movedBytes: String = "0",
    val current: List<String> = emptyList(), val outOfSpace: Boolean = false, val error: String? = null, val freedBytes: String? = null,
    val destinations: List<FreeUpJobDest> = emptyList(), val failures: List<FreeUpFailure> = emptyList(), val failedCount: Int = 0,
    /** "move" = Drive files moved off the Drive; "photos" = Google Photos picks copied to other drives. */
    val kind: String = "move", val skipped: Int = 0, val folderName: String? = null,
    /** When the copied Google Photos were taken (ISO), to find them again in Google Photos. */
    val takenFrom: String? = null, val takenTo: String? = null,
) {
    val running: Boolean get() = state == "running"
    val isPhotos: Boolean get() = kind == "photos"
}
@Serializable data class FreeUpPlan(
    val account: FreeUpAccount, val quota: FreeUpQuota, val knownBytes: String = "0", val nudgeTrash: FreeUpTrash = FreeUpTrash(),
    val categories: List<FreeUpCategory> = emptyList(), val largest: List<FreeUpFile> = emptyList(), val destinations: List<FreeUpDestination> = emptyList(),
    val importing: ImportStatus? = null, val job: FreeUpJob? = null,
    /** Nudge may read the Google Photos the user picks on this account. */
    val photosAccess: Boolean = false,
    /** Google Photos already copied out of this account (originals still to delete in Google Photos). */
    val photosCopied: PhotosCopied? = null,
)
@Serializable data class PhotosCopied(val count: Int = 0, val bytes: String = "0", val takenFrom: String? = null, val takenTo: String? = null)
@Serializable data class PhotosSession(val id: String, val pickerUri: String? = null, val mediaItemsSet: Boolean = false, val pollIntervalMs: Long = 4000)
@Serializable data class PhotosSessionResponse(val session: PhotosSession)
@Serializable data class FreeUpJobResponse(val job: FreeUpJob? = null)
@Serializable data class EmptyTrashResponse(val removedFiles: Int = 0, val freedBytes: String = "0")

fun JsonElement?.asLongOrZero(): Long = ((this as? JsonPrimitive)?.contentOrNull)?.toLongOrNull() ?: 0L

// ---------- Mobile document views (Nudge server additions)
@Serializable data class SheetTab(val name: String, val rows: List<List<String>> = emptyList(), val colWidths: List<Double?> = emptyList(), val truncated: Boolean = false)
@Serializable data class SheetData(val sheets: List<SheetTab> = emptyList())
@Serializable data class EditLink(val url: String, val account: String? = null)
@Serializable data class CreatedDocument(val file: FileItem, val url: String? = null)
@Serializable data class OfficeSession(val url: String, val accessToken: String, val accessTokenTtl: Long, val fileName: String = "")
