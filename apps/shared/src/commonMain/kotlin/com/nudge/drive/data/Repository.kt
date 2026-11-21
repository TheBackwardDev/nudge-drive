package com.nudge.drive.data

/** Percent-encodes a query value (spaces as %20, like android.net.Uri.encode). */
internal fun enc(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

/** Every server endpoint the app uses, in one place. Paths are relative to API_BASE (…/api). */
class Repository(private val api: ApiClient, val webBase: String) {
    val session get() = api.session

    // ---------- Auth ----------
    suspend fun login(email: String, password: String) =
        api.post<AuthResponse>("/auth/login", jsonOf("email" to email.trim(), "password" to password)).also { session.save(it) }

    suspend fun register(name: String, email: String, password: String) =
        api.post<AuthResponse>("/auth/register", jsonOf("name" to name.trim(), "email" to email.trim(), "password" to password)).also { session.save(it) }

    suspend fun googleLoginUrl(): String = api.get<UrlResponse>("/auth/google/url").url ?: error("No Google sign-in configured on the server.")

    /** Native (in-app) Google sign-in: the app gets a server auth code from Google and sends it here. */
    suspend fun googleNativeConfig() = api.get<GoogleNativeConfig>("/auth/google/native-config")

    suspend fun googleNativeLogin(code: String) =
        api.post<AuthResponse>("/auth/google/native", jsonOf("code" to code)).also { session.save(it) }

    suspend fun googleNativeConnect(code: String) = api.post<NativeConnectResponse>("/auth/google/native/connect", jsonOf("code" to code))

    suspend fun exchangeGoogle(token: String) =
        api.post<AuthResponse>("/auth/google/exchange", jsonOf("token" to token)).also { session.save(it) }

    suspend fun me(): User = api.get<MeResponse>("/auth/me").user.also { session.updateUser(it) }

    suspend fun verifyEmail(code: String): VerifyResponse {
        val r = api.post<VerifyResponse>("/auth/verify-email", jsonOf("code" to code))
        // The code is accepted at this point; refreshing the profile is best effort (the screen also polls).
        try { me() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            session.user.value?.let { session.updateUser(it.copy(emailVerified = true)) }
        }
        return r
    }
    suspend fun resendVerification() = api.post<VerifyResponse>("/auth/resend-verification")

    suspend fun logout() {
        runCatching { api.post<StatusResponse>("/auth/logout") }
        session.clear()
    }

    /** Emails a 6-digit code that confirms deleting the account (for people who sign in with Google). */
    suspend fun requestDeleteCode() = api.post<DeleteCodeResponse>("/auth/delete-account/code")

    /** Permanently deletes the account (confirmed by [password] or the emailed [code]), then signs out here. */
    suspend fun deleteAccount(password: String? = null, code: String? = null) {
        val body = if (password != null) jsonOf("password" to password) else jsonOf("code" to code)
        api.post<StatusResponse>("/auth/delete-account", body)
        session.clear()
    }

    // ---------- Storage ----------
    suspend fun summary() = api.get<StorageSummary>("/storage/summary")
    suspend fun breakdown() = api.get<Breakdown>("/storage/breakdown")
    suspend fun policy() = api.get<PolicyResponse>("/storage/routing-policy").policy
    suspend fun setPolicy(mode: String, priority: List<String>) =
        api.patch<PolicyResponse>("/storage/routing-policy", jsonOf("mode" to mode, "priorityAccountIds" to priority)).policy

    // ---------- Connected accounts ----------
    suspend fun accounts() = api.get<AccountsResponse>("/connected-accounts").accounts
    suspend fun googleConnectUrl(): String = api.get<UrlResponse>("/connected-accounts/google/connect-url").url ?: error("No Google config on server.")
    /** Browser consent that adds "read the Google Photos I pick" to that Google account. */
    suspend fun googlePhotosConnectUrl(email: String): String =
        api.get<UrlResponse>("/connected-accounts/google/connect-url?photos=1&hint=${enc(email)}").url ?: error("No Google config on server.")
    suspend fun syncQuota(id: String) = api.post<QuotaResponse>("/connected-accounts/$id/sync-quota").quota
    suspend fun disconnect(id: String) = api.delete<StatusResponse>("/connected-accounts/$id")
    /** Bring in every file/folder already on that Google Drive (runs on the server in the background). */
    // ---------- Free up space ----------
    suspend fun freeUpPlan(id: String) = api.get<FreeUpPlan>("/storage/free-up/$id")
    suspend fun freeUpEmptyTrash(id: String) = api.post<EmptyTrashResponse>("/storage/free-up/$id/empty-trash")
    suspend fun freeUpStart(id: String, categories: List<String>, targetBytes: Long?, destinationId: String?) =
        api.post<FreeUpJobResponse>("/storage/free-up/$id/start", jsonOf("categories" to categories, "targetBytes" to targetBytes?.toString(), "destinationId" to destinationId)).job
    suspend fun freeUpStatus(id: String) = api.get<FreeUpJobResponse>("/storage/free-up/$id/status").job
    suspend fun freeUpCancel(id: String) = api.post<FreeUpJobResponse>("/storage/free-up/$id/cancel").job
    suspend fun freeUpClear(id: String) = api.delete<StatusResponse>("/storage/free-up/$id/job")
    suspend fun freeUpPhotosGrant(id: String, code: String) = api.post<StatusResponse>("/storage/free-up/$id/photos/grant", jsonOf("code" to code))
    suspend fun freeUpPhotosSession(id: String) = api.post<PhotosSessionResponse>("/storage/free-up/$id/photos/session").session
    suspend fun freeUpPhotosSessionStatus(id: String, sessionId: String) = api.get<PhotosSessionResponse>("/storage/free-up/$id/photos/session/$sessionId").session
    suspend fun freeUpPhotosStart(id: String, sessionId: String, destinationId: String?) =
        api.post<FreeUpJobResponse>("/storage/free-up/$id/photos/start", jsonOf("sessionId" to sessionId, "destinationId" to destinationId)).job

    suspend fun importDrive(id: String) = api.post<ImportStatus>("/connected-accounts/$id/import-drive")
    suspend fun importStatus(id: String) = api.get<ImportStatus>("/connected-accounts/$id/import-status")
    suspend fun connectS3(
        name: String, bucket: String, region: String, endpoint: String?, accessKeyId: String, secret: String,
        forcePathStyle: Boolean?, quotaBytes: String?,
    ) = api.post<StatusResponse>(
        "/connected-accounts/s3",
        jsonOf(
            "name" to name, "bucket" to bucket, "region" to region,
            "endpoint" to endpoint?.takeIf { it.isNotBlank() }, "accessKeyId" to accessKeyId, "secretAccessKey" to secret,
            "forcePathStyle" to forcePathStyle, "quotaBytes" to quotaBytes?.takeIf { it.isNotBlank() },
        ),
    )

    // ---------- Folders ----------
    suspend fun folders(parentId: String?) =
        api.get<FoldersResponse>(if (parentId == null) "/folders" else "/folders?parentId=${enc(parentId)}").folders
    suspend fun allFolders() = api.get<FoldersResponse>("/folders?all=1").folders
    suspend fun recentFolders() = api.get<FoldersResponse>("/folders/recent?limit=4").folders
    suspend fun createFolder(name: String, parentId: String?, color: String) =
        api.post<FolderResponse>("/folders", jsonOf("name" to name, "parentId" to parentId, "color" to color)).folder
    suspend fun updateFolder(id: String, name: String? = null, color: String? = null, moveTo: Pair<Boolean, String?>? = null): Folder {
        val pairs = buildList {
            name?.let { add("name" to it) }; color?.let { add("color" to it) }
            moveTo?.let { add("parentId" to it.second) }
        }
        return api.patch<FolderResponse>("/folders/$id", jsonOf(*pairs.toTypedArray())).folder
    }
    suspend fun deleteFolder(id: String) = api.delete<StatusResponse>("/folders/$id")

    // ---------- Files ----------
    suspend fun files(folderId: String? = null, q: String? = null, kind: String? = null, accountId: String? = null): List<FileItem> {
        val params = buildList {
            folderId?.let { add("folderId=" + enc(it)) }
            q?.takeIf { it.isNotBlank() }?.let { add("q=" + enc(it.trim())) }
            kind?.let { add("kind=$it") }
            accountId?.let { add("accountId=" + enc(it)) }
        }
        return api.get<FilesResponse>("/files" + if (params.isEmpty()) "" else "?" + params.joinToString("&")).files
    }
    suspend fun file(id: String) = api.get<FileResponse>("/files/$id").file
    suspend fun renameFile(id: String, name: String) = api.patch<FileResponse>("/files/$id", jsonOf("name" to name)).file
    suspend fun moveFile(id: String, folderId: String?) = api.patch<FileResponse>("/files/$id", jsonOf("folderId" to folderId)).file
    suspend fun trashFile(id: String) = api.delete<StatusResponse>("/files/$id")
    suspend fun moveFiles(ids: List<String>, folderId: String?) = api.patch<BatchResult>("/files/batch", jsonOf("fileIds" to ids, "folderId" to folderId))
    suspend fun trashFiles(ids: List<String>) = api.delete<BatchResult>("/files/batch", jsonOf("fileIds" to ids))
    suspend fun trash(q: String? = null) = api.get<FilesResponse>("/files/trash" + (q?.takeIf { it.isNotBlank() }?.let { "?q=" + enc(it) } ?: "")).files
    suspend fun restore(ids: List<String>) = api.post<BatchResult>("/files/batch/restore", jsonOf("fileIds" to ids))
    suspend fun deleteForever(ids: List<String>) = api.delete<BatchResult>("/files/batch/permanent", jsonOf("fileIds" to ids))
    suspend fun syncGoogle() = api.post<SyncResponse>("/files/sync-google")

    // ---------- Sharing ----------
    suspend fun share(id: String) = api.post<ShareResponse>("/files/$id/share")
    suspend fun unshare(id: String) = api.delete<StatusResponse>("/files/$id/share")
    suspend fun sharedLinks() = api.get<SharesResponse>("/files/shared-links").shares
    suspend fun makePublicOnDrive(id: String) = api.post<UrlResponse>("/files/$id/public-permission").url
    suspend fun viewUrl(id: String) = api.get<UrlResponse>("/files/$id/view-url").url

    /** Unauthenticated, Range-capable stream URL valid for 10 minutes. */
    suspend fun previewUrl(id: String): String = api.base + api.post<PreviewToken>("/files/$id/preview-token").path
    fun downloadUrl(id: String) = "${api.base}/files/$id/download"

    // ---------- Documents (viewer + Google editing) ----------
    suspend fun sheetData(id: String) = api.get<SheetData>("/files/$id/sheet-data")
    suspend fun documentHtml(id: String): String = api.raw("GET", "/files/$id/document-html")
    suspend fun editLink(id: String) = api.get<EditLink>("/files/$id/edit-link")
    /** Opens the in-app editor (self-hosted Collabora Online) for this file. */
    suspend fun officeSession(id: String) = api.post<OfficeSession>("/files/$id/office-session")
    /** kind: document | spreadsheet | presentation | drawing | form */
    suspend fun createDocument(kind: String, name: String, folderId: String?) =
        api.post<CreatedDocument>("/files/create-document", jsonOf("kind" to kind, "name" to name.trim(), "folderId" to folderId))

    // ---------- Invites ----------
    suspend fun invites() = api.get<InvitesResponse>("/invites")
    suspend fun invite(email: String, role: String, targetType: String, targetId: String) =
        api.post<InviteResponse>("/invites", jsonOf("email" to email.trim(), "role" to role, "targetType" to targetType, "targetId" to targetId)).invite
    suspend fun revokeInvite(id: String) = api.delete<StatusResponse>("/invites/$id")

    // ---------- API keys ----------
    suspend fun apiKeys() = api.get<ApiKeysResponse>("/api-keys").apiKeys
    suspend fun createApiKey(name: String) = api.post<ApiKeyCreated>("/api-keys", jsonOf("name" to name))
    suspend fun revokeApiKey(id: String) = api.delete<StatusResponse>("/api-keys/$id")
    val publicUploadEndpoint get() = "${api.base}/api/v1/uploads"

    // ---------- Activity ----------
    suspend fun activity() = api.get<AuditResponse>("/audit-logs").logs
}
