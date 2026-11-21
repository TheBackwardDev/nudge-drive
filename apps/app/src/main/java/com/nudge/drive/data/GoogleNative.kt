package com.nudge.drive.data

import android.accounts.Account
import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.nudge.drive.AppContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in without leaving the app: the phone's own account picker (Credential Manager),
 * then Google's consent sheet for Drive access (AuthorizationClient). Google hands back a one-time
 * server auth code that the server exchanges for the Drive tokens, exactly like the web flow does.
 *
 * Needs an "Android" OAuth client for com.nudge.drive + the signing key's SHA-1 in the same Google
 * Cloud project as the server's web client. Without it Google answers "developer error", and the
 * callers fall back to the browser flow, so the app keeps working either way.
 */
class GoogleNative(private val activity: ComponentActivity) {
    sealed interface Result {
        data class Code(val code: String) : Result
        data object Cancelled : Result
        data class Unavailable(val why: String) : Result
    }

    private var pending: CompletableDeferred<ActivityResult>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { pending?.complete(it) }
    /** One flow at a time: a second one (double tap) would take over [pending] and leave the first waiting forever. */
    private val busy = Mutex()

    suspend fun authCode(clientId: String, scopes: List<String>): Result {
        if (!busy.tryLock()) return Result.Cancelled
        try { return pickAndAuthorize(clientId, scopes) } finally { busy.unlock() }
    }

    /** Google's consent sheet for [scopes] on a known account, without the account picker. */
    suspend fun authorize(clientId: String, scopes: List<String>, email: String): Result {
        if (!busy.tryLock()) return Result.Cancelled
        try { return authorizeAccount(clientId, scopes, email) } finally { busy.unlock() }
    }

    private suspend fun pickAndAuthorize(clientId: String, scopes: List<String>): Result {
        // 1. Which Google account? The system bottom sheet lists every account on the phone.
        val email = try {
            val request = GetCredentialRequest(listOf(GetSignInWithGoogleOption.Builder(clientId).build()))
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                return Result.Unavailable("unexpected credential ${credential.type}")
            GoogleIdTokenCredential.createFrom(credential.data).id
        } catch (e: GetCredentialCancellationException) {
            // Google reports "this app's signing key isn't registered" as a cancellation ("[16] Account
            // reauth failed") right after the user picks an account. That isn't the user backing out.
            Log.w("GoogleNative", "Account picker closed: ${e.message}")
            val reauth = e.message.orEmpty().let { it.startsWith("[16]") || it.contains("reauth", ignoreCase = true) }
            return if (reauth) Result.Unavailable("${e.type}: ${e.message}") else Result.Cancelled
        } catch (e: GetCredentialException) {
            return Result.Unavailable("${e.type}: ${e.message}")
        }

        // 2. Drive access for that account, with offline access so the server can keep syncing.
        return authorizeAccount(clientId, scopes, email)
    }

    private suspend fun authorizeAccount(clientId: String, scopes: List<String>, email: String): Result {
        val client = Identity.getAuthorizationClient(activity)
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map(::Scope))
            .setAccount(Account(email, "com.google"))
            .requestOfflineAccess(clientId, true)
            .build()
        val result = try {
            val first = client.authorize(request).await()
            if (first.hasResolution()) {
                val intent = first.pendingIntent ?: return Result.Unavailable("no consent intent")
                val done = CompletableDeferred<ActivityResult>().also { pending = it }
                launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                val answer = done.await()
                if (answer.resultCode != Activity.RESULT_OK || answer.data == null) return Result.Cancelled
                client.getAuthorizationResultFromIntent(answer.data)
            } else first
        } catch (e: ApiException) {
            return if (e.statusCode == CommonStatusCodes.CANCELED) Result.Cancelled else Result.Unavailable("authorize ${e.statusCode}: ${e.message}")
        } finally {
            pending = null
        }
        return result.serverAuthCode?.let { Result.Code(it) } ?: Result.Unavailable("no server auth code")
    }
}

/**
 * Google sign-in (connect = false) or "add another Google Drive" (connect = true) in the app.
 * Returns false when the native flow isn't available, so the caller opens the web flow instead.
 * Server errors are thrown for the caller to show.
 */
suspend fun AppContainer.googleSignIn(connect: Boolean): Boolean {
    val native = googleNative ?: return false
    val config = runCatching { repo.googleNativeConfig() }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; return false }
    return when (val r = native.authCode(config.clientId, config.scopes)) {
        GoogleNative.Result.Cancelled -> true
        is GoogleNative.Result.Unavailable -> {
            Log.w("GoogleNative", "Falling back to the browser: ${r.why}")
            toast("Continuing with Google in your browser…")
            false
        }
        is GoogleNative.Result.Code -> {
            if (connect) {
                val email = repo.googleNativeConnect(r.code).email
                toast("Google Drive connected")
                val body = if (email != null) "The free space of $email is now part of your storage pool." else "Its free space is now part of your storage pool."
                notices.post(NoticeType.Drive, "Google Drive connected", body, system = false)
                refreshBus.tryEmit(Unit)
            } else {
                val auth = repo.googleNativeLogin(r.code)
                toast("Signed in as ${auth.user.name}")
            }
            true
        }
    }
}

/** Lets Nudge read the Google Photos the user picks; also asked again if Google forgot it. */
const val PHOTOS_SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"

/**
 * Adds Google Photos access to an already connected Google account, in the app.
 * true = granted, false = the user backed out, null = native sign-in unavailable (use the browser).
 */
suspend fun AppContainer.googlePhotosAccess(accountId: String, email: String): Boolean? {
    val native = googleNative ?: return null
    val config = runCatching { repo.googleNativeConfig() }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; return null }
    return when (val r = native.authorize(config.clientId, (config.scopes + PHOTOS_SCOPE).distinct(), email)) {
        GoogleNative.Result.Cancelled -> false
        is GoogleNative.Result.Unavailable -> { Log.w("GoogleNative", "Photos access via browser: ${r.why}"); null }
        is GoogleNative.Result.Code -> { repo.freeUpPhotosGrant(accountId, r.code); true }
    }
}
