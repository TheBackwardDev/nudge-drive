package com.nudge.drive.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class LanFileInfo(val name: String, val size: Long, val mime: String = "application/octet-stream")

/** A device found on the local network (a phone on its Receive screen, or a computer with receiving on). */
data class LanPeer(val id: String, val name: String, val kind: String, val host: InetAddress, val port: Int, val seen: Long)

/** A file to send; [open] is called when its turn comes. */
class LanOutgoing(val info: LanFileInfo, val open: () -> InputStream)

/** Where one received file is written: [stream], then [commit] (returns what the app keeps, e.g. a File or Uri) or [abort]. */
class LanTarget(val stream: OutputStream, val commit: () -> Any, val abort: () -> Unit)

fun interface LanSaver { fun create(info: LanFileInfo): LanTarget }

/** Every event carries the transfer it belongs to, so a stale transfer can be ignored. */
fun interface LanListener { fun on(transfer: Long, event: LanEvent) }

sealed interface LanEvent {
    /** Receiver: someone wants to send. Answer with [LanShare.accept] / [LanShare.decline]. */
    data class Request(val peer: String, val kind: String, val code: String, val files: List<LanFileInfo>) : LanEvent
    /** Receiver: the sender gave up (or nobody answered in time) before the request was answered. */
    data class RequestGone(val peer: String) : LanEvent
    /** Sender: connected; waiting for the other side to accept. Both sides show [code]. */
    data class Waiting(val peer: String, val code: String) : LanEvent
    data class Started(val peer: String, val files: List<LanFileInfo>) : LanEvent
    data class Progress(val index: Int, val bytes: Long) : LanEvent
    /** [saved] is what the [LanSaver] returned (receiver), or null on the sending side. */
    data class FileDone(val index: Int, val saved: Any?) : LanEvent
    data class FileFailed(val index: Int) : LanEvent
    data class Finished(val peer: String) : LanEvent
    data class Declined(val peer: String) : LanEvent
    data class Failed(val message: String) : LanEvent
}

/**
 * Wi-Fi sharing between Nudge apps on the same network (phone <-> computer, computer <-> computer,
 * phone <-> phone). No internet, no account needed.
 *
 * - Finding devices: the sender broadcasts a small UDP probe; devices that are receiving answer
 *   with their name and TCP port.
 * - Every transfer is end-to-end encrypted: an ECDH (P-256) key exchange, then AES-256-GCM frames.
 *   Both screens show a 4-digit code derived from that exchange (like Nearby share), so the user
 *   can check nobody in the middle swapped keys, and nothing is received without tapping Accept.
 */
class LanShare(
    private val scope: CoroutineScope,
    private val deviceName: () -> String,
    /** "phone" or "computer": picks the icon on the other side. */
    private val kind: String,
    /** Diagnostics (never file contents): where each app keeps its log. */
    private val log: (String) -> Unit = {},
) {
    private val instanceId = UUID.randomUUID().toString().replace("-", "").take(16)
    private val transferIds = AtomicLong(0)

    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers

    private var discovery: Job? = null
    private var receiver: Job? = null
    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    /** The one transfer that may run at a time (either direction). */
    @Volatile private var active: Socket? = null
    @Volatile private var decision: CompletableDeferred<Boolean>? = null

    val isReceiving: Boolean get() = receiver?.isActive == true

    // ------------------------------------------------------------------ finding devices (sender)

    fun startDiscovery() {
        if (discovery?.isActive == true) return
        _peers.value = emptyList()
        discovery = scope.launch(Dispatchers.IO) {
            val sock = DatagramSocket()
            try {
                sock.broadcast = true
                sock.soTimeout = 400
                val probe = (PROBE + instanceId).toByteArray()
                val buf = ByteArray(2048)
                var lastProbe = 0L
                var round = 0
                log("discovery started")
                while (isActive) {
                    val now = System.currentTimeMillis()
                    if (now - lastProbe > 1500) {
                        lastProbe = now
                        val broadcasts = broadcastTargets()
                        if (round == 0) log("probing ${broadcasts.joinToString { it.hostAddress ?: "?" }}")
                        for (target in broadcasts + extraTargets) runCatching { sock.send(DatagramPacket(probe, probe.size, target, DISCOVERY_PORT)) }
                        // Many routers and phones drop broadcasts: every few rounds, also ask each address on the home network directly.
                        if (round % 3 == 0) {
                            val hosts = sweepTargets()
                            if (round == 0) log("also asking ${hosts.size} addresses directly")
                            for (target in hosts) runCatching { sock.send(DatagramPacket(probe, probe.size, target, DISCOVERY_PORT)) }
                        }
                        round++
                        _peers.update { l -> l.filter { now - it.seen < 6000 } }
                    }
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (e: IOException) {
                        // Windows reports "port unreachable" from a device that isn't listening as an error on the next receive.
                        if (sock.isClosed) break
                        continue
                    }
                    val text = String(p.data, p.offset, p.length, Charsets.UTF_8)
                    if (!text.startsWith(REPLY)) continue
                    val a = runCatching { AppJson.decodeFromString(Announce.serializer(), text.removePrefix(REPLY)) }.getOrNull() ?: continue
                    if (a.id == instanceId || a.port !in 1..65535) continue
                    val peer = LanPeer("lan:" + a.id.take(40), a.name.take(80).ifBlank { "Device" }, a.kind.take(20), p.address, a.port, System.currentTimeMillis())
                    if (_peers.value.none { it.id == peer.id }) log("found ${a.kind} at ${p.address.hostAddress}:${a.port}")
                    _peers.update { l ->
                        val old = l.firstOrNull { it.id == peer.id }
                        // A device on this same machine answers on loopback too; keep its network address.
                        val keep = if (old != null && peer.host.isLoopbackAddress && !old.host.isLoopbackAddress) old.copy(seen = peer.seen) else peer
                        (l.filter { it.id != peer.id } + keep).sortedBy { it.name.lowercase() }
                    }
                }
            } finally {
                sock.close()
            }
        }
    }

    fun stopDiscovery() {
        discovery?.cancel(); discovery = null
        _peers.value = emptyList()
    }

    private val extraTargets = java.util.concurrent.CopyOnWriteArraySet<InetAddress>()

    /**
     * Also asks this IPv4 address directly while searching: for networks that drop discovery
     * messages, or a device on another part of the network. Returns false if it isn't an address.
     */
    fun addAddress(text: String): Boolean {
        val t = text.trim()
        val parts = t.split('.')
        if (parts.size != 4 || parts.any { p -> p.isEmpty() || p.length > 3 || p.any { !it.isDigit() } || p.toInt() > 255 }) return false
        extraTargets += InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
        return true
    }

    /** This device's addresses on its home networks, to show to the user ("192.168.1.7"). */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isPointToPoint && MOBILE_OR_VPN.none { p -> it.name.lowercase().startsWith(p) } }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    private fun broadcastTargets(): List<InetAddress> {
        val out = linkedSetOf(InetAddress.getByName("255.255.255.255"), InetAddress.getByName("127.0.0.1"))
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (ni.isUp && !ni.isLoopback) ni.interfaceAddresses.forEach { ia -> ia.broadcast?.let { out += it } }
            }
        }
        return out.toList()
    }

    /** Every other address on this device's home networks (private IPv4 only, at most a /24 around our own address). */
    private fun sweepTargets(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback || ni.isPointToPoint) return@forEach
                // Never mobile data or VPN links: only the Wi-Fi / wired network the devices share.
                if (MOBILE_OR_VPN.any { ni.name.lowercase().startsWith(it) }) return@forEach
                ni.interfaceAddresses.forEach { ia ->
                    val a = ia.address as? java.net.Inet4Address ?: return@forEach
                    if (!a.isSiteLocalAddress || ia.networkPrefixLength < 8 || ia.networkPrefixLength > 30) return@forEach
                    val prefix = maxOf(ia.networkPrefixLength.toInt(), 24)
                    val b = a.address
                    val ip = ((b[0].toInt() and 0xff) shl 24) or ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
                    val mask = -1 shl (32 - prefix)
                    val net = ip and mask
                    for (h in 1 until (1 shl (32 - prefix)) - 1) {
                        val x = net or h
                        if (x == ip) continue
                        out += InetAddress.getByAddress(byteArrayOf((x ushr 24).toByte(), (x ushr 16).toByte(), (x ushr 8).toByte(), x.toByte()))
                    }
                }
            }
        }
        return out.take(1024)
    }

    // ------------------------------------------------------------------ receiving

    /** Makes this device visible and accepts incoming requests until [stopReceiving]. */
    fun startReceiving(saver: LanSaver, listener: LanListener) {
        if (receiver?.isActive == true) return
        receiver = scope.launch(Dispatchers.IO) {
            val ss = runCatching { ServerSocket().apply { bind(InetSocketAddress(PREFERRED_TCP_PORT)) } }.getOrElse { ServerSocket(0) }
            var u: DatagramSocket? = null
            for (attempt in 0 until 6) {
                u = runCatching { DatagramSocket(null).apply { broadcast = true; bind(InetSocketAddress(DISCOVERY_PORT)) } }.getOrNull()
                if (u != null || !isActive) break
                delay(300) // the previous receiver may still be letting go of the port
            }
            log("receiving on tcp ${ss.localPort}, udp ${if (u != null) DISCOVERY_PORT else "unavailable (port busy)"}")
            synchronized(this@LanShare) { server = ss; udp = u }
            try {
                if (!isActive) return@launch
                u?.let { sock -> launch { answerProbes(sock, ss.localPort) } }
                while (isActive) {
                    val s = ss.accept()
                    launch { handleIncoming(s, saver, listener) }
                }
            } catch (_: IOException) {
                // closed by stopReceiving
            } finally {
                runCatching { ss.close() }
                runCatching { u?.close() }
            }
        }
    }

    fun stopReceiving() {
        val j = receiver
        receiver = null
        j?.cancel()
        synchronized(this) {
            runCatching { server?.close() }; runCatching { udp?.close() }
            server = null; udp = null
        }
        decision?.complete(false)
    }

    fun accept() { decision?.complete(true) }
    fun decline() { decision?.complete(false) }

    /** Stops the running transfer (either direction). */
    fun cancel() {
        decision?.complete(false)
        runCatching { active?.close() }
    }

    fun stopAll() { stopDiscovery(); stopReceiving(); cancel() }

    private fun answerProbes(sock: DatagramSocket, port: Int) {
        val buf = ByteArray(512)
        while (true) {
            val p = DatagramPacket(buf, buf.size)
            try { sock.receive(p) } catch (_: IOException) { return }
            val text = String(p.data, p.offset, p.length, Charsets.UTF_8)
            if (!text.startsWith(PROBE) || text.removePrefix(PROBE) == instanceId) continue
            val reply = (REPLY + AppJson.encodeToString(Announce.serializer(), Announce(instanceId, deviceName(), kind, port))).toByteArray()
            runCatching { sock.send(DatagramPacket(reply, reply.size, p.socketAddress)) }
        }
    }

    private suspend fun handleIncoming(s: Socket, saver: LanSaver, listener: LanListener) = withContext(Dispatchers.IO) {
        val tid = transferIds.incrementAndGet()
        val emit = { e: LanEvent -> listener.on(tid, e) }
        var claimed = false
        var requested = false
        var peer = "Device"
        try {
            s.tcpNoDelay = true
            s.soTimeout = 15_000
            val inp = DataInputStream(BufferedInputStream(s.getInputStream(), BUFFER))
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), BUFFER))
            val hello = readHello(inp) ?: return@withContext
            peer = hello.name.take(80).ifBlank { "Device" }
            log("incoming from ${hello.kind} at ${s.inetAddress?.hostAddress}")
            claimed = claim(s)
            if (!claimed) { writeHello(out, Hello(name = deviceName(), kind = kind, busy = true)); return@withContext }
            val keys = newKeyPair()
            writeHello(out, Hello(name = deviceName(), kind = kind, key = b64(keys.public.encoded)))
            val ch = SecureChannel.establish(keys, hello.key, sender = false)

            val offer = ch.readControl(inp)
            if (offer.t != "offer") throw IOException("unexpected ${offer.t}")
            val files = offer.files.orEmpty().take(MAX_FILES).map { it.copy(name = cleanName(it.name), mime = it.mime.take(100)) }
            if (files.isEmpty()) return@withContext

            // Ask the user, while watching for the sender giving up.
            val d = CompletableDeferred<Boolean>()
            decision = d
            requested = true
            emit(LanEvent.Request(peer, hello.kind, ch.code, files))
            s.soTimeout = 400
            val deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS
            while (!d.isCompleted) {
                if (System.currentTimeMillis() > deadline) { d.complete(false); break }
                try {
                    if (inp.read() == -1) { emit(LanEvent.RequestGone(peer)); return@withContext }
                    throw IOException("unexpected data")
                } catch (_: SocketTimeoutException) {}
            }
            decision = null
            if (!d.getCompleted()) {
                if (System.currentTimeMillis() > deadline) emit(LanEvent.RequestGone(peer))
                runCatching { ch.writeControl(out, Ctl("decline")); out.flush() }
                return@withContext
            }
            requested = false
            ch.writeControl(out, Ctl("accept")); out.flush()
            s.soTimeout = 60_000
            emit(LanEvent.Started(peer, files))
            receiveFiles(ch, inp, out, files, saver, emit)
            emit(LanEvent.Finished(peer))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("incoming transfer ended: ${e.javaClass.simpleName}: ${e.message}")
            if (requested) emit(LanEvent.RequestGone(peer))
            else if (claimed) emit(LanEvent.Failed(describe(e, peer)))
        } finally {
            if (requested) decision = null
            if (claimed) release(s)
            runCatching { s.close() }
        }
    }

    private fun receiveFiles(ch: SecureChannel, inp: DataInputStream, out: DataOutputStream, files: List<LanFileInfo>, saver: LanSaver, emit: (LanEvent) -> Unit) {
        var current: LanTarget? = null
        var index = -1
        var got = 0L
        var lastEmit = 0L
        try {
            while (true) {
                val (type, data) = ch.read(inp)
                if (type == T_DATA) {
                    val t = current ?: continue // couldn't create the file: drop its data, it's reported failed at the end
                    t.stream.write(data)
                    got += data.size
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 120) { lastEmit = now; emit(LanEvent.Progress(index, got)) }
                    continue
                }
                val m = ch.decodeControl(type, data)
                when (m.t) {
                    "file" -> {
                        current?.let { runCatching { it.stream.close() }; it.abort() }
                        index = m.i
                        if (index !in files.indices) throw IOException("bad index")
                        got = 0
                        current = runCatching { saver.create(files[index]) }.getOrNull()
                        emit(LanEvent.Progress(index, 0))
                    }
                    "end" -> {
                        val t = current
                        current = null
                        val saved = if (t != null && got == m.n) runCatching { t.stream.close(); t.commit() }.getOrNull()
                        else { t?.let { runCatching { it.stream.close() }; it.abort() }; null }
                        if (saved != null) {
                            emit(LanEvent.Progress(index, got)); emit(LanEvent.FileDone(index, saved))
                            ch.writeControl(out, Ctl("ok", i = index))
                        } else {
                            if (t != null) runCatching { t.abort() }
                            emit(LanEvent.FileFailed(index))
                            ch.writeControl(out, Ctl("fail", i = index))
                        }
                        out.flush()
                    }
                    "skip" -> if (m.i in files.indices) emit(LanEvent.FileFailed(m.i))
                    "bye" -> return
                    else -> throw IOException("unexpected ${m.t}")
                }
            }
        } catch (e: Exception) {
            current?.let { runCatching { it.stream.close() }; runCatching { it.abort() } }
            throw e
        }
    }

    // ------------------------------------------------------------------ sending

    /** A fresh transfer id: take one, remember it, then pass it to [send] (so no event can arrive before it's known). */
    fun newTransferId(): Long = transferIds.incrementAndGet()

    /** Sends [files] to [peer]; its events carry [tid]. */
    fun send(tid: Long, peer: LanPeer, files: List<LanOutgoing>, listener: LanListener) {
        val emit = { e: LanEvent -> listener.on(tid, e) }
        scope.launch(Dispatchers.IO) {
            val s = Socket()
            if (!claim(s)) { emit(LanEvent.Failed("Another transfer is still running. Wait for it to finish.")); return@launch }
            try {
                log("sending ${files.size} file(s) to ${peer.kind} at ${peer.host.hostAddress}:${peer.port}")
                s.connect(InetSocketAddress(peer.host, peer.port), 6000)
                s.tcpNoDelay = true
                s.soTimeout = 15_000
                val inp = DataInputStream(BufferedInputStream(s.getInputStream(), BUFFER))
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), BUFFER))
                val keys = newKeyPair()
                writeHello(out, Hello(name = deviceName(), kind = kind, key = b64(keys.public.encoded)))
                val hello = readHello(inp) ?: throw IOException("no answer")
                if (hello.busy) { emit(LanEvent.Failed("${peer.name} is busy with another transfer. Try again in a moment.")); return@launch }
                val ch = SecureChannel.establish(keys, hello.key, sender = true)
                val infos = files.map { it.info }
                ch.writeControl(out, Ctl("offer", files = infos)); out.flush()
                emit(LanEvent.Waiting(peer.name, ch.code))

                s.soTimeout = (REQUEST_TIMEOUT_MS + 10_000).toInt()
                val answer = ch.readControl(inp)
                if (answer.t != "accept") { emit(LanEvent.Declined(peer.name)); return@launch }
                s.soTimeout = 60_000
                emit(LanEvent.Started(peer.name, infos))

                val buf = ByteArray(CHUNK)
                files.forEachIndexed { i, f ->
                    ensureActive()
                    val input = runCatching { f.open() }.getOrNull()
                    if (input == null) {
                        ch.writeControl(out, Ctl("skip", i = i)); out.flush()
                        emit(LanEvent.FileFailed(i))
                        return@forEachIndexed
                    }
                    ch.writeControl(out, Ctl("file", i = i))
                    var sent = 0L
                    var lastEmit = 0L
                    input.use { stream ->
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            if (n == 0) continue
                            ch.write(out, T_DATA, buf, 0, n)
                            sent += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 120) { lastEmit = now; emit(LanEvent.Progress(i, sent)) }
                        }
                    }
                    ch.writeControl(out, Ctl("end", i = i, n = sent)); out.flush()
                    emit(LanEvent.Progress(i, sent))
                    val ack = ch.readControl(inp)
                    if (ack.t == "ok") emit(LanEvent.FileDone(i, null)) else emit(LanEvent.FileFailed(i))
                }
                ch.writeControl(out, Ctl("bye")); out.flush()
                emit(LanEvent.Finished(peer.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("send failed: ${e.javaClass.simpleName}: ${e.message}")
                emit(LanEvent.Failed(describe(e, peer.name)))
            } finally {
                release(s)
                runCatching { s.close() }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    @Synchronized private fun claim(s: Socket): Boolean { if (active != null) return false; active = s; return true }
    @Synchronized private fun release(s: Socket) { if (active === s) active = null }

    private fun describe(e: Exception, peer: String): String = when (e) {
        is ConnectException, is NoRouteToHostException -> "Couldn't reach $peer. Make sure both devices are on the same Wi-Fi and $peer is still on its receive screen."
        is SocketTimeoutException -> "$peer stopped responding. Try again."
        is java.security.GeneralSecurityException -> "The connection couldn't be verified, so it was stopped. Try again."
        else -> "The connection to $peer dropped. Try again."
    }

    @Serializable private data class Announce(val id: String, val name: String, val kind: String, val port: Int)
    @Serializable private data class Hello(val v: Int = 1, val name: String = "", val kind: String = "phone", val key: String = "", val busy: Boolean = false)
    @Serializable private data class Ctl(val t: String, val files: List<LanFileInfo>? = null, val i: Int = -1, val n: Long = 0)

    private fun writeHello(out: DataOutputStream, h: Hello) {
        out.write((MAGIC + AppJson.encodeToString(Hello.serializer(), h) + "\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun readHello(inp: DataInputStream): Hello? {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = inp.read()
            if (b == -1) return null
            if (b == '\n'.code) break
            line.write(b)
            if (line.size() > 4096) return null
        }
        val text = line.toString("UTF-8")
        if (!text.startsWith(MAGIC)) return null
        return runCatching { AppJson.decodeFromString(Hello.serializer(), text.removePrefix(MAGIC)) }.getOrNull()
    }

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    /** AES-256-GCM frames: [u32 length][ciphertext]; plaintext = [type][payload]; nonce = per-direction counter. */
    private class SecureChannel(private val sendKey: SecretKeySpec, private val recvKey: SecretKeySpec, val code: String) {
        private var sent = 0L
        private var received = 0L
        private val enc = Cipher.getInstance("AES/GCM/NoPadding")
        private val dec = Cipher.getInstance("AES/GCM/NoPadding")

        private fun nonce(counter: Long) = ByteArray(12).also { for (k in 0 until 8) it[11 - k] = (counter ushr (8 * k)).toByte() }

        fun write(out: DataOutputStream, type: Int, data: ByteArray, off: Int, len: Int) {
            val plain = ByteArray(len + 1)
            plain[0] = type.toByte()
            System.arraycopy(data, off, plain, 1, len)
            enc.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(128, nonce(sent++)))
            val ct = enc.doFinal(plain)
            out.writeInt(ct.size)
            out.write(ct)
        }

        fun read(inp: DataInputStream): Pair<Int, ByteArray> {
            val len = inp.readInt()
            if (len < 17 || len > CHUNK + 64) throw IOException("bad frame")
            val ct = ByteArray(len)
            inp.readFully(ct)
            dec.init(Cipher.DECRYPT_MODE, recvKey, GCMParameterSpec(128, nonce(received++)))
            val plain = dec.doFinal(ct)
            return (plain[0].toInt() and 0xff) to plain.copyOfRange(1, plain.size)
        }

        fun writeControl(out: DataOutputStream, c: Ctl) {
            val b = AppJson.encodeToString(Ctl.serializer(), c).toByteArray(Charsets.UTF_8)
            write(out, T_CTL, b, 0, b.size)
        }

        fun readControl(inp: DataInputStream): Ctl { val (t, d) = read(inp); return decodeControl(t, d) }

        fun decodeControl(type: Int, data: ByteArray): Ctl {
            if (type != T_CTL) throw IOException("unexpected frame")
            return AppJson.decodeFromString(Ctl.serializer(), String(data, Charsets.UTF_8))
        }

        companion object {
            fun establish(mine: KeyPair, theirsB64: String, sender: Boolean): SecureChannel {
                val theirsRaw = Base64.getDecoder().decode(theirsB64)
                val theirs = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(theirsRaw))
                val secret = KeyAgreement.getInstance("ECDH").run { init(mine.private); doPhase(theirs, true); generateSecret() }
                val senderPub = if (sender) mine.public.encoded else theirsRaw
                val receiverPub = if (sender) theirsRaw else mine.public.encoded
                fun derive(label: String): ByteArray = MessageDigest.getInstance("SHA-256").run {
                    update(secret); update(label.toByteArray()); update(senderPub); update(receiverPub); digest()
                }
                val s2r = SecretKeySpec(derive("nudge-lan s2r"), "AES")
                val r2s = SecretKeySpec(derive("nudge-lan r2s"), "AES")
                val c = derive("nudge-lan code")
                val n = ((c[0].toInt() and 0x7f) shl 24) or ((c[1].toInt() and 0xff) shl 16) or ((c[2].toInt() and 0xff) shl 8) or (c[3].toInt() and 0xff)
                val code = (n % 10000).toString().padStart(4, '0')
                return if (sender) SecureChannel(s2r, r2s, code) else SecureChannel(r2s, s2r, code)
            }
        }
    }

    companion object {
        const val DISCOVERY_PORT = 47321
        const val PREFERRED_TCP_PORT = 47322
        private const val PROBE = "NUDGE-LAN1?"
        private const val REPLY = "NUDGE-LAN1!"
        private const val MAGIC = "NUDGE-LAN1 "
        private const val T_CTL = 1
        private const val T_DATA = 2
        private const val CHUNK = 64 * 1024
        private const val BUFFER = 256 * 1024
        private const val MAX_FILES = 5000
        private val MOBILE_OR_VPN = listOf("rmnet", "ccmni", "pdp", "ppp", "v4-", "tun", "utun", "ipsec", "clat", "dummy", "tailscale", "wg")
        private const val REQUEST_TIMEOUT_MS = 120_000L

        /** A safe file name from whatever the other device sent (no folders, no reserved characters). */
        fun cleanName(raw: String): String {
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
            val c = base.filter { it >= ' ' && it !in "<>:\"|?*" }.trim().trimStart('.').trimEnd('.', ' ').take(180)
            return c.ifBlank { "file" }
        }
    }
}
