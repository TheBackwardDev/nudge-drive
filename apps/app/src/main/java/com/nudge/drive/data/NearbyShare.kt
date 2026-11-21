package com.nudge.drive.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.api.ApiException as GmsApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A file waiting to be sent: always a readable content:// Uri (drive files are downloaded to cache first). */
data class OutgoingItem(val uri: Uri, val name: String, val size: Long, val mime: String)

enum class NearbyFileState { Waiting, Moving, Done, Failed }
data class NearbyFile(
    val payloadId: Long, val name: String, val size: Long, val mime: String,
    val bytes: Long = 0, val state: NearbyFileState = NearbyFileState.Waiting, val savedUri: Uri? = null,
) { val progress: Float get() = if (size <= 0) (if (state == NearbyFileState.Done) 1f else 0f) else (bytes.toFloat() / size).coerceIn(0f, 1f) }

/** [kind] is "phone" or "computer" (computers are found over Wi-Fi, see [LanShare]). */
data class NearbyPeer(val id: String, val name: String, val kind: String = "phone")
data class NearbyRequest(val endpointId: String, val name: String, val code: String)

/** What the user can do about a failure; the screen shows the matching button. */
enum class NearbyFix { None, AppSettings, TurnOnLocation }

sealed interface NearbyPhase {
    data object Idle : NearbyPhase
    /** Sender: looking for phones. */
    data object Searching : NearbyPhase
    /** Receiver: visible and waiting. */
    data object Visible : NearbyPhase
    data class Connecting(val peer: String, val code: String?) : NearbyPhase
    data class Transferring(val peer: String) : NearbyPhase
    data class Done(val peer: String, val received: Boolean) : NearbyPhase
    data class Failed(val message: String, val fix: NearbyFix = NearbyFix.None) : NearbyPhase
}

@Serializable private data class ManifestEntry(val id: Long, val name: String, val size: Long, val mime: String)
@Serializable private data class TransferManifest(val v: Int = 1, val files: List<ManifestEntry>)

/**
 * Phone-to-phone sharing between Nudge apps using Google Nearby Connections. P2P_POINT_TO_POINT
 * starts over Bluetooth and upgrades to a direct Wi-Fi link for the bulk transfer, so big videos
 * move at Wi-Fi speed with no internet.
 */
class NearbyShare(private val context: Context, private val scope: CoroutineScope, private val myName: () -> String) {
    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val strategy = Strategy.P2P_POINT_TO_POINT

    val phase = MutableStateFlow<NearbyPhase>(NearbyPhase.Idle)
    val peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val request = MutableStateFlow<NearbyRequest?>(null)
    val files = MutableStateFlow<List<NearbyFile>>(emptyList())

    /** Files queued for sending (set before opening the Nearby screen). */
    val outgoing = MutableStateFlow<List<OutgoingItem>>(emptyList())
    private var sending = false
    /** Role of the last start (true = sending), so "Try again" repeats it. */
    var lastRole: Boolean? = null; private set
    /** Bumped on every start/stop; results from an older session are ignored. */
    @Volatile private var session = 0
    private var peerName = ""
    private var authCode: String? = null
    private var endpoint: String? = null
    private val incoming = mutableMapOf<Long, Payload>()
    /** Wi-Fi sharing with computers (and phones on the same network), running alongside Nearby Connections. */
    private val lan = LanShare(scope, myName, "phone") { android.util.Log.i("NudgeLan", it) }
    /**
     * Why the Bluetooth side (phones that aren't on this Wi-Fi) isn't running, with the fix to offer.
     * Wi-Fi sharing works without it, so this is a note on the screen, not a failure.
     */
    val bluetoothNote = MutableStateFlow<Pair<String, NearbyFix>?>(null)
    private val nearbyPeers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    init {
        scope.launch { combine(nearbyPeers, lan.peers) { a, b -> a + b.map { NearbyPeer(it.id, it.name, it.kind) } }.collect { peers.value = it } }
    }
    /** Updates that arrived before the file list (tiny files can finish first); replayed once it's known. */
    private val early = mutableMapOf<Long, PayloadTransferUpdate>()

    val isSending: Boolean get() = sending
    val busy: Boolean get() = phase.value is NearbyPhase.Transferring || phase.value is NearbyPhase.Connecting

    // ------------------------------------------------------------------ public API

    /**
     * Starts looking for devices. Wi-Fi needs no permission, so it always starts right away;
     * [withNearby] also starts Bluetooth (otherwise call [addNearby] once permissions allow it).
     */
    fun startSending(withNearby: Boolean = true) {
        if (outgoing.value.isEmpty()) return
        resetConnections()
        sending = true
        nearbyPeers.value = emptyList(); files.value = emptyList()
        lastRole = true
        phase.value = NearbyPhase.Searching
        lan.startDiscovery()
        if (withNearby) begin(discover = true)
    }

    /** Adds Bluetooth (phones that aren't on this Wi-Fi) to a search / receive that is already running. */
    fun addNearby() {
        val p = phase.value
        if (p != NearbyPhase.Searching && p != NearbyPhase.Visible) return
        bluetoothNote.value = null
        begin(discover = sending)
    }

    /** Also looks for a device at this Wi-Fi address (shown on the other device). False if it isn't an address. */
    fun addAddress(text: String): Boolean = lan.addAddress(text)

    /** This phone's Wi-Fi address(es), shown so a computer can find it by hand. */
    fun wifiAddresses(): List<String> = lan.localAddresses()

    /** Bluetooth can't run (permission refused, Location off); Wi-Fi keeps working. */
    fun nearbyUnavailable(message: String, fix: NearbyFix) {
        bluetoothNote.value = message to fix
    }

    fun sendTo(peer: NearbyPeer) {
        if (phase.value != NearbyPhase.Searching) return // one connection at a time (double taps)
        if (peer.id.startsWith("lan:")) { sendOverWifi(peer); return }
        peerName = peer.name
        phase.value = NearbyPhase.Connecting(peer.name, null)
        val sid = session
        client.requestConnection(myName(), peer.id, lifecycle).addOnFailureListener { if (sid == session) fail(it) }
    }

    fun startReceiving(withNearby: Boolean = true) {
        resetConnections()
        sending = false
        files.value = emptyList()
        lastRole = false
        phase.value = NearbyPhase.Visible
        holdMulticast(true)
        val sid = session
        lan.startReceiving(lanSaver) { _, e -> onLan(sid, e) }
        if (withNearby) begin(discover = false)
    }

    /**
     * Starts discovery or advertising, retrying the failures that are only timing:
     * - "already discovering/advertising" (8001/8002/8008): the stop from the previous attempt
     *   hasn't reached Google Play services yet, so stop again and retry.
     * - a missing-permission code although the permission IS granted: right after the user taps
     *   Allow, Play services can still see the old permission state for a moment.
     * - a one-off radio/internal error.
     */
    private fun begin(discover: Boolean) {
        val sid = session
        scope.launch(Dispatchers.Main.immediate) {
            var attempt = 0
            while (true) {
                val error = try {
                    if (discover) client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build()).await()
                    else client.startAdvertising(myName(), SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build()).await()
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
                if (error == null || sid != session) return@launch
                val code = (error as? GmsApiException)?.statusCode
                val retry = attempt < MAX_RETRIES && when (code) {
                    ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING, ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING,
                    ConnectionsStatusCodes.STATUS_ALREADY_HAVE_ACTIVE_STRATEGY, ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL -> {
                        runCatching { client.stopDiscovery() }; runCatching { client.stopAdvertising() }; true
                    }
                    ConnectionsStatusCodes.STATUS_ERROR, ConnectionsStatusCodes.STATUS_RADIO_ERROR -> attempt < 2
                    in PERMISSION_CODES -> permissionFor(code!!)?.let { granted(it) } ?: true
                    else -> false
                }
                android.util.Log.w("NudgeNearby", "start ${if (discover) "discovery" else "advertising"} failed: $code (attempt $attempt, retry=$retry)")
                if (!retry) {
                    // Only the Bluetooth side failed: Wi-Fi sharing is still running, so explain instead of failing the screen.
                    bluetoothNote.value = describeFailure(error)
                    return@launch
                }
                attempt++
                delay(400L * attempt)
                if (sid != session) return@launch
            }
        }
    }

    private fun granted(permission: String) = isGranted(context, permission)

    fun accept() {
        val r = request.value ?: return
        request.value = null
        peerName = r.name
        phase.value = NearbyPhase.Connecting(r.name, r.code)
        if (r.endpointId == LAN_REQUEST) { lan.accept(); return }
        val sid = session
        client.acceptConnection(r.endpointId, payloads).addOnFailureListener { if (sid == session) fail(it) }
    }

    fun decline() {
        val r = request.value ?: return
        request.value = null
        if (r.endpointId == LAN_REQUEST) lan.decline() else client.rejectConnection(r.endpointId)
    }

    /** Stops everything and returns to the start screen. */
    fun stop() {
        resetConnections()
        phase.value = NearbyPhase.Idle
        nearbyPeers.value = emptyList(); request.value = null
    }

    fun clearOutgoing() { outgoing.value = emptyList() }

    // ------------------------------------------------------------------ callbacks

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) nearbyPeers.update { list -> (list.filter { it.id != id } + NearbyPeer(id, info.endpointName)) }
        }
        override fun onEndpointLost(id: String) { nearbyPeers.update { list -> list.filter { it.id != id } } }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            authCode = info.authenticationDigits
            if (sending) {
                phase.value = NearbyPhase.Connecting(info.endpointName, info.authenticationDigits)
                client.acceptConnection(id, payloads).addOnFailureListener { fail(it) }
            } else {
                request.value = NearbyRequest(id, info.endpointName, info.authenticationDigits)
            }
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (phase.value !is NearbyPhase.Connecting) { runCatching { client.disconnectFromEndpoint(id) }; return } // stopped meanwhile
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    endpoint = id
                    if (sending) { runCatching { client.stopDiscovery() }; scope.launch { sendAll(id) } }
                    else { runCatching { client.stopAdvertising() }; phase.value = NearbyPhase.Transferring(peerName) }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    phase.value = NearbyPhase.Failed(if (sending) "$peerName declined the files." else "Connection declined.")
                else -> phase.value = NearbyPhase.Failed("Couldn't connect. Keep both phones close together and try again.")
            }
        }

        override fun onDisconnected(id: String) {
            endpoint = null
            // Any in-between state ends here; Done was already set by checkFinished before we hung up.
            val p = phase.value
            if (p is NearbyPhase.Transferring || p is NearbyPhase.Connecting) {
                files.update { l -> l.map { if (it.state == NearbyFileState.Done) it else it.copy(state = NearbyFileState.Failed) } }
                phase.value = NearbyPhase.Failed(
                    if (p is NearbyPhase.Connecting) "The other phone stopped before connecting. Try again." else "The connection dropped. Move the phones closer and try again.",
                )
            }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> runCatching {
                    val m = AppJson.decodeFromString(TransferManifest.serializer(), String(payload.asBytes()!!, Charsets.UTF_8))
                    files.value = m.files.map { NearbyFile(it.id, it.name, it.size, it.mime) }
                    phase.value = NearbyPhase.Transferring(peerName)
                    early.values.toList().also { early.clear() }.forEach { u -> onPayloadTransferUpdate(id, u) }
                }
                Payload.Type.FILE -> incoming[payload.id] = payload
                else -> {}
            }
        }

        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {
            if (files.value.none { it.payloadId == u.payloadId }) {
                if (!sending && u.payloadId in incoming) early[u.payloadId] = u
                return
            }
            when (u.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> setFile(u.payloadId) { it.copy(bytes = u.bytesTransferred, state = NearbyFileState.Moving) }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (sending) { setFile(u.payloadId) { it.copy(bytes = it.size, state = NearbyFileState.Done) }; checkFinished() }
                    else scope.launch {
                        val saved = runCatching { saveIncoming(u.payloadId) }.getOrNull()
                        setFile(u.payloadId) { it.copy(bytes = it.size, state = if (saved != null) NearbyFileState.Done else NearbyFileState.Failed, savedUri = saved) }
                        checkFinished()
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    setFile(u.payloadId) { it.copy(state = NearbyFileState.Failed) }; checkFinished()
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun sendAll(id: String) = withContext(Dispatchers.IO) {
        try {
            val prepared = outgoing.value.mapNotNull { item ->
                val pfd = runCatching { context.contentResolver.openFileDescriptor(item.uri, "r") }.getOrNull() ?: return@mapNotNull null
                Payload.fromFile(pfd) to item
            }
            if (prepared.isEmpty()) {
                phase.value = NearbyPhase.Failed("Those files couldn't be read.")
                runCatching { client.disconnectFromEndpoint(id) } // let the other phone know too
                return@withContext
            }
            files.value = prepared.map { (p, it) -> NearbyFile(p.id, it.name, it.size, it.mime) }
            val manifest = TransferManifest(files = prepared.map { (p, it) -> ManifestEntry(p.id, it.name, it.size, it.mime) })
            client.sendPayload(id, Payload.fromBytes(AppJson.encodeToString(TransferManifest.serializer(), manifest).toByteArray()))
            phase.value = NearbyPhase.Transferring(peerName)
            prepared.forEach { (p, _) -> client.sendPayload(id, p) }
        } catch (e: Exception) {
            phase.value = NearbyPhase.Failed("Sending failed: ${e.message ?: "unknown error"}")
            runCatching { client.disconnectFromEndpoint(id) }
        }
    }

    private fun checkFinished() {
        val list = files.value
        if (list.isEmpty() || list.any { it.state == NearbyFileState.Waiting || it.state == NearbyFileState.Moving }) return
        phase.value = if (list.all { it.state == NearbyFileState.Failed }) NearbyPhase.Failed("Nothing was transferred. Try again.")
        else NearbyPhase.Done(peerName, received = !sending)
        // Let the last acknowledgements through before hanging up.
        val sid = session
        scope.launch { delay(1500); if (sid == session) { endpoint?.let { runCatching { client.disconnectFromEndpoint(it) } }; endpoint = null } }
    }

    private fun setFile(id: Long, change: (NearbyFile) -> NearbyFile) = files.update { l -> l.map { if (it.payloadId == id) change(it) else it } }

    /** Moves a received payload into Downloads/Nudge Drive under its real name. */
    private suspend fun saveIncoming(payloadId: Long): Uri? = withContext(Dispatchers.IO) {
        val payload = incoming.remove(payloadId) ?: return@withContext null
        val meta = files.value.firstOrNull { it.payloadId == payloadId } ?: return@withContext null
        val pf = payload.asFile() ?: return@withContext null
        val name = meta.name.replace('/', '_').ifBlank { "nearby-$payloadId" }
        val src = if (Build.VERSION.SDK_INT >= 29) pf.asUri() else null
        val input = when {
            src != null -> context.contentResolver.openInputStream(src)
            else -> @Suppress("DEPRECATION") pf.asJavaFile()?.inputStream()
        } ?: return@withContext null
        val out: Uri = input.use { inp ->
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, meta.mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nudge Drive")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                context.contentResolver.openOutputStream(uri)!!.use { inp.copyTo(it, 256 * 1024) }
                uri
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Nudge Drive").apply { mkdirs() }
                val f = File(dir, name)
                f.outputStream().use { inp.copyTo(it, 256 * 1024) }
                FileProvider.getUriForFile(context, context.packageName + ".files", f)
            }
        }
        if (src != null) runCatching { context.contentResolver.delete(src, null, null) }
        else @Suppress("DEPRECATION") runCatching { pf.asJavaFile()?.delete() }
        out
    }

    // ------------------------------------------------------------------ Wi-Fi (computers)

    private fun sendOverWifi(peer: NearbyPeer) {
        val target = lan.peers.value.firstOrNull { it.id == peer.id } ?: return
        peerName = peer.name
        phase.value = NearbyPhase.Connecting(peer.name, null)
        val items = outgoing.value.map { item ->
            LanOutgoing(LanFileInfo(item.name, item.size, item.mime)) {
                context.contentResolver.openInputStream(item.uri) ?: throw java.io.IOException("Can't read ${item.name}")
            }
        }
        val sid = session
        lan.send(lan.newTransferId(), target, items) { _, e -> onLan(sid, e) }
    }

    /** Wi-Fi transfer events, mapped onto the same screen states as Nearby Connections. */
    private fun onLan(sid: Int, e: LanEvent) {
        if (sid != session) return
        when (e) {
            is LanEvent.Request -> request.value = NearbyRequest(LAN_REQUEST, e.peer, e.code)
            is LanEvent.RequestGone -> {
                if (request.value?.endpointId == LAN_REQUEST) request.value = null
                if (phase.value is NearbyPhase.Connecting) phase.value = NearbyPhase.Failed("${e.peer} cancelled before sending.")
            }
            is LanEvent.Waiting -> phase.value = NearbyPhase.Connecting(e.peer, e.code)
            is LanEvent.Started -> {
                peerName = e.peer
                // The Wi-Fi link is up: stop Bluetooth searching / advertising so it doesn't slow the transfer.
                runCatching { client.stopDiscovery() }; runCatching { client.stopAdvertising() }
                lan.stopDiscovery()
                files.value = e.files.mapIndexed { i, f -> NearbyFile(i.toLong(), f.name, f.size, f.mime) }
                phase.value = NearbyPhase.Transferring(e.peer)
            }
            is LanEvent.Progress -> setFile(e.index.toLong()) { it.copy(bytes = e.bytes, state = NearbyFileState.Moving) }
            is LanEvent.FileDone -> setFile(e.index.toLong()) { it.copy(bytes = maxOf(it.bytes, it.size), state = NearbyFileState.Done, savedUri = e.saved as? Uri) }
            is LanEvent.FileFailed -> setFile(e.index.toLong()) { it.copy(state = NearbyFileState.Failed) }
            is LanEvent.Finished -> {
                files.update { l -> l.map { if (it.state == NearbyFileState.Done) it else it.copy(state = NearbyFileState.Failed) } }
                phase.value = if (files.value.none { it.state == NearbyFileState.Done }) NearbyPhase.Failed("Nothing was transferred. Try again.")
                else NearbyPhase.Done(e.peer, received = !sending)
            }
            is LanEvent.Declined -> phase.value = NearbyPhase.Failed("${e.peer} declined the files.")
            is LanEvent.Failed -> {
                files.update { l -> l.map { if (it.state == NearbyFileState.Done) it else it.copy(state = NearbyFileState.Failed) } }
                phase.value = NearbyPhase.Failed(e.message)
            }
        }
    }

    /** Received Wi-Fi files go straight into Downloads/Nudge Drive (hidden from other apps until complete). */
    private val lanSaver = LanSaver { info ->
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, info.name)
                put(MediaStore.Downloads.MIME_TYPE, info.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nudge Drive")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("Can't save ${info.name}")
            val stream = resolver.openOutputStream(uri) ?: run { resolver.delete(uri, null, null); throw java.io.IOException("Can't save ${info.name}") }
            LanTarget(
                java.io.BufferedOutputStream(stream, 256 * 1024),
                commit = { resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null); uri },
                abort = { runCatching { resolver.delete(uri, null, null) } },
            )
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Nudge Drive").apply { mkdirs() }
            val stem = info.name.substringBeforeLast('.')
            val ext = info.name.substringAfterLast('.', "").let { if (it.isEmpty() || it == info.name) "" else ".$it" }
            var f = File(dir, info.name)
            var n = 1
            while (f.exists()) { f = File(dir, "$stem ($n)$ext"); n++ }
            val target = f
            LanTarget(
                java.io.BufferedOutputStream(target.outputStream(), 256 * 1024),
                commit = { FileProvider.getUriForFile(context, context.packageName + ".files", target) },
                abort = { target.delete() },
            )
        }
    }

    /** Some phones drop broadcast packets unless an app asks for them; held only while receiving. */
    private fun holdMulticast(on: Boolean) {
        runCatching {
            if (on) {
                if (multicastLock?.isHeld == true) return
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                multicastLock = wifi.createMulticastLock("nudge-share").apply { setReferenceCounted(false); acquire() }
            } else {
                multicastLock?.takeIf { it.isHeld }?.release()
                multicastLock = null
            }
        }
    }

    private fun resetConnections() {
        session++
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        lan.stopAll()
        holdMulticast(false)
        bluetoothNote.value = null
        endpoint = null; authCode = null; incoming.clear(); early.clear()
    }

    private fun fail(e: Exception) {
        val (message, fix) = describeFailure(e)
        phase.value = NearbyPhase.Failed(message, fix)
    }

    private fun describeFailure(e: Exception): Pair<String, NearbyFix> {
        val code = (e as? GmsApiException)?.statusCode
        return when (code) {
            ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                "Turn on Bluetooth and Wi-Fi, then try again." to NearbyFix.None
            ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON ->
                "Nearby share needs Location turned on (with location accuracy) to find phones around you." to NearbyFix.TurnOnLocation
            in PERMISSION_CODES -> {
                val missing = permissionFor(code!!)?.takeIf { !granted(it) }
                if (missing != null) explain(listOf(missing)) to NearbyFix.AppSettings
                else ("Google Play services still sees this permission as blocked. Tap Try again. If it keeps happening, open app settings › Permissions " +
                    "and set Location and Nearby devices to Allow (some phones also have their own extra permission switch there).") to NearbyFix.AppSettings
            }
            ConnectionsStatusCodes.API_CONNECTION_FAILED_ALREADY_IN_USE ->
                "Another app is using Nearby right now. Close it and try again." to NearbyFix.None
            ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING, ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING,
            ConnectionsStatusCodes.STATUS_ALREADY_HAVE_ACTIVE_STRATEGY ->
                "Nearby was still busy with the last attempt. Tap Try again." to NearbyFix.None
            else -> "Nearby share isn't available right now (${code ?: e.message}). Make sure Bluetooth and Wi-Fi are on." to NearbyFix.None
        }
    }

    companion object {
        const val SERVICE_ID = "com.nudge.drive.nearby"
        private const val LAN_REQUEST = "lan:request"
        private const val MAX_RETRIES = 4

        private val PERMISSION_CODES = setOf(
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION, ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH, ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN, ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT, ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE, ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE,
        )

        /** The runtime permission behind a MISSING_PERMISSION_* code (null for install-time ones). */
        private fun permissionFor(code: Int): String? = when (code) {
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else null
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_ADVERTISE else null
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else null
            else -> null
        }

        /**
         * Permissions Nearby Connections really needs on this Android version (Nearby Connections
         * docs, plus what testing on Android 16 showed: searching fails with 8036 unless location
         * is PRECISE; approximate is not enough):
         *  - Android 9 and older: location
         *  - Android 10 to 12L: precise location
         *  - Android 13+: precise location for searching only
         *  - Android 12+: Bluetooth scan/advertise/connect; Android 13+: Nearby Wi-Fi devices
         */
        fun required(sending: Boolean): List<String> {
            val list = mutableListOf<String>()
            when {
                Build.VERSION.SDK_INT <= 28 -> list += Manifest.permission.ACCESS_COARSE_LOCATION
                Build.VERSION.SDK_INT <= 32 || sending -> list += Manifest.permission.ACCESS_FINE_LOCATION
            }
            if (Build.VERSION.SDK_INT >= 31) list += listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.NEARBY_WIFI_DEVICES
            return list
        }

        /** What to ask for: the required ones, with both location levels together (Android 12+ needs that). */
        fun permissions(sending: Boolean = true): Array<String> {
            val list = required(sending).toMutableList()
            if (list.any { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION })
                list += listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            return list.distinct().toTypedArray()
        }

        fun missing(context: Context, sending: Boolean): List<String> = required(sending).filter { !isGranted(context, it) }

        /**
         * Granted AND not blocked by app-ops. Some phones (MIUI, ColorOS, Funtouch…) keep their own
         * permission switch: Android reports Location as granted, but the app-op is denied, and
         * Nearby then fails with "missing location permission" although Settings looks fine.
         */
        fun isGranted(context: Context, permission: String): Boolean =
            PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

        /** A clear sentence about what's missing and where to turn it on. */
        fun explain(missing: List<String>): String = when {
            Manifest.permission.ACCESS_FINE_LOCATION in missing && Build.VERSION.SDK_INT >= 31 ->
                "Nearby share needs precise location on this Android version. In Settings › Permissions › Location, choose Allow and turn on \"Use precise location\"."
            Manifest.permission.ACCESS_FINE_LOCATION in missing || Manifest.permission.ACCESS_COARSE_LOCATION in missing ->
                "Nearby share needs the Location permission to find phones around you. Open app settings › Permissions › Location and choose Allow (some phones also have their own extra permission switch there)."
            else -> "Nearby share needs the Nearby devices permission. In Settings › Permissions, allow Nearby devices."
        }

        /** Whether Location must be switched on before starting (searching always scans; old Android also needs it to be found). */
        fun needsLocationOn(sending: Boolean) = sending || Build.VERSION.SDK_INT <= 32

        /** Reads display name / size / type of a shared content Uri. */
        fun describe(context: Context, uri: Uri): OutgoingItem {
            var name = uri.lastPathSegment ?: "file"
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
            return OutgoingItem(uri, name, size, mime)
        }
    }
}

/** Task.await without pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
