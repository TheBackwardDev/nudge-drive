package com.nudge.drive.data

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

class ApiException(val status: Int, val code: String?, override val message: String) : Exception(message) {
    /** Server wraps validation errors in a JSON array string; pull the first human message out. */
    val friendly: String
        get() = runCatching {
            val arr = AppJson.parseToJsonElement(message) as JsonArray
            ((arr.first() as JsonObject)["message"] as JsonPrimitive).content
        }.getOrElse {
            when (code) {
                "AUTH_INVALID_CREDENTIALS" -> "Wrong email or password."
                "AUTH_EMAIL_TAKEN" -> "That email is already registered."
                "NO_ACCOUNT_WITH_ENOUGH_SPACE" -> "None of your drives has enough free space."
                else -> if (message.contains("No record was found")) "That item no longer exists." else message
            }
        }
}

/**
 * Small persistent key/value store for the session (Android: DataStore; desktop: a file in the
 * user's app-data folder, protected with the OS's per-user encryption).
 */
interface SecureStore {
    suspend fun read(): Map<String, String>
    suspend fun write(values: Map<String, String?>)
    suspend fun clear()
}

/** Persists tokens + cached user. Access token also kept in memory for the interceptor. */
class SessionStore(private val store: SecureStore) {
    private val kAccess = "access"
    private val kRefresh = "refresh"
    private val kUser = "user"

    @Volatile var accessToken: String? = null; private set
    @Volatile var refreshToken: String? = null; private set
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user
    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    /** null = still loading from disk. */
    val loggedIn: StateFlow<Boolean?> = _loggedIn

    private val loadMutex = kotlinx.coroutines.sync.Mutex()
    /** Every caller (activity, backup worker…) waits for the same single read. */
    suspend fun load() = loadMutex.withLock {
        if (_loggedIn.value != null) return@withLock
        try {
            val p = store.read()
            accessToken = p[kAccess]; refreshToken = p[kRefresh]
            _user.value = p[kUser]?.let { runCatching { AppJson.decodeFromString<User>(it) }.getOrNull() }
            _loggedIn.value = refreshToken != null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            _loggedIn.value = false
        }
    }

    suspend fun save(auth: AuthResponse) {
        accessToken = auth.accessToken; refreshToken = auth.refreshToken; _user.value = auth.user
        store.write(mapOf(kAccess to auth.accessToken, kRefresh to auth.refreshToken, kUser to AppJson.encodeToString(User.serializer(), auth.user)))
        _loggedIn.value = true
    }

    /** The server said this account still needs email verification. */
    suspend fun markUnverified() { _user.value?.let { if (it.emailVerified != false) updateUser(it.copy(emailVerified = false)) } }

    suspend fun updateUser(u: User) {
        _user.value = u
        store.write(mapOf(kUser to AppJson.encodeToString(User.serializer(), u)))
    }

    fun updateAccessBlocking(token: String) {
        accessToken = token
        runBlocking { store.write(mapOf(kAccess to token)) }
    }

    suspend fun clear() {
        accessToken = null; refreshToken = null; _user.value = null
        store.clear()
        _loggedIn.value = false
    }

    fun clearBlocking() = runBlocking { clear() }
}

private sealed interface RefreshOutcome {
    data class Refreshed(val token: String) : RefreshOutcome
    data object Rejected : RefreshOutcome
    data object Transient : RefreshOutcome
}

/** Thin JSON-over-OkHttp client with bearer auth and single-flight token refresh. */
class ApiClient(val session: SessionStore, val base: String, private val userAgent: String) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val refreshLock = Any()

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val token = session.accessToken
        val out = if (token != null && req.header("Authorization") == null && req.url.toString().startsWith(base))
            req.newBuilder().header("Authorization", "Bearer $token").build() else req
        chain.proceed(out.newBuilder().header("User-Agent", userAgent).build())
    }

    private val authenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val path = response.request.url.encodedPath
            if (path.endsWith("/auth/refresh") || path.endsWith("/auth/login")) return null
            if (responseCount(response) >= 2) return null
            val refresh = session.refreshToken ?: return null
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            synchronized(refreshLock) {
                val current = session.accessToken
                if (current != null && current != failedToken) {
                    return response.request.newBuilder().header("Authorization", "Bearer $current").build()
                }
                val body = AppJson.encodeToString(JsonObject.serializer(), buildJsonObject { put("refreshToken", JsonPrimitive(refresh)) })
                val outcome: RefreshOutcome = try {
                    plain.newCall(Request.Builder().url("$base/auth/refresh").post(body.toRequestBody(jsonType)).build()).execute().use { r ->
                        when {
                            r.isSuccessful -> RefreshOutcome.Refreshed(AppJson.decodeFromString<AccessResponse>(r.body!!.string()).accessToken)
                            r.code == 400 || r.code == 401 || r.code == 403 -> RefreshOutcome.Rejected
                            else -> RefreshOutcome.Transient
                        }
                    }
                } catch (e: java.io.IOException) { RefreshOutcome.Transient }
                val refreshed = when (outcome) {
                    is RefreshOutcome.Refreshed -> outcome.token
                    RefreshOutcome.Rejected -> { session.clearBlocking(); return null } // really signed out
                    RefreshOutcome.Transient -> return null // offline / server restarting: keep the session, the call just fails
                }
                session.updateAccessBlocking(refreshed)
                return response.request.newBuilder().header("Authorization", "Bearer $refreshed").build()
            }
        }
    }

    private fun responseCount(r: Response): Int { var n = 1; var p = r.priorResponse; while (p != null) { n++; p = p.priorResponse }; return n }

    private val plain: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /** Authenticated client (also used by Coil and ExoPlayer). */
    val http: OkHttpClient = plain.newBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .readTimeout(10, TimeUnit.MINUTES).writeTimeout(10, TimeUnit.MINUTES)
        .build()

    fun url(path: String) = if (path.startsWith("http")) path else base + path

    fun jsonBody(el: JsonElement): RequestBody = AppJson.encodeToString(JsonElement.serializer(), el).toRequestBody(jsonType)

    suspend fun raw(method: String, path: String, body: JsonElement? = null): String = withContext(Dispatchers.IO) {
        val rb = when {
            body != null -> jsonBody(body)
            method == "POST" || method == "PATCH" || method == "PUT" -> "{}".toRequestBody(jsonType)
            else -> null
        }
        val req = Request.Builder().url(url(path)).method(method, rb).build()
        http.newCall(req).execute().use { r -> readOrThrow(r) }
    }

    fun readOrThrow(r: Response): String {
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) {
            val err = runCatching { AppJson.decodeFromString<ApiError>(text) }.getOrNull()
            if (r.code == 403 && err?.code == "EMAIL_NOT_VERIFIED") runBlocking { session.markUnverified() }
            throw ApiException(r.code, err?.code, err?.message ?: "Request failed (${r.code})")
        }
        return text
    }

    suspend inline fun <reified T> get(path: String): T = AppJson.decodeFromString(raw("GET", path))
    suspend inline fun <reified T> post(path: String, body: JsonElement? = null): T = decode(raw("POST", path, body))
    suspend inline fun <reified T> patch(path: String, body: JsonElement): T = decode(raw("PATCH", path, body))
    suspend inline fun <reified T> delete(path: String, body: JsonElement? = null): T = decode(raw("DELETE", path, body))

    inline fun <reified T> decode(text: String): T =
        AppJson.decodeFromJsonElement(if (text.isBlank()) JsonObject(emptyMap()) else AppJson.parseToJsonElement(text))
}

fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to v.toJson() })

private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it.toJson() })
    else -> JsonPrimitive(toString())
}
