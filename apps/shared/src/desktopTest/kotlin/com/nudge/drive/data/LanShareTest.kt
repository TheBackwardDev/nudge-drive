package com.nudge.drive.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanShareTest {
    private class Side(name: String, kind: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val lan = LanShare(scope, { name }, kind)
        val events = CopyOnWriteArrayList<LanEvent>()
        val saved = ConcurrentHashMap<String, ByteArray>()
        val saver = LanSaver { info ->
            val buf = ByteArrayOutputStream()
            LanTarget(buf, commit = { saved[info.name] = buf.toByteArray(); info.name }, abort = {})
        }
        suspend fun waitFor(timeout: Long = 15_000, test: (LanEvent) -> Boolean): LanEvent = withTimeout(timeout) {
            while (true) { events.firstOrNull(test)?.let { return@withTimeout it }; delay(20) }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    @Test
    fun findsSendsAndReceives() = runBlocking<Unit> {
        val phone = Side("Alex · Pixel", "phone")
        val pc = Side("Alex · DESKTOP", "computer")
        try {
            phone.lan.startReceiving(phone.saver) { _, e -> phone.events += e; if (e is LanEvent.Request) phone.lan.accept() }
            pc.lan.startDiscovery()
            val peer = withTimeout(10_000) { pc.lan.peers.first { it.isNotEmpty() } }.first()
            assertEquals("Alex · Pixel", peer.name)
            assertEquals("phone", peer.kind)

            val big = Random(1).nextBytes(3 * 1024 * 1024 + 123)
            val small = "hello".toByteArray()
            val files = listOf(
                LanOutgoing(LanFileInfo("../../evil/video.mp4", big.size.toLong(), "video/mp4")) { ByteArrayInputStream(big) },
                LanOutgoing(LanFileInfo("note.txt", small.size.toLong(), "text/plain")) { ByteArrayInputStream(small) },
                LanOutgoing(LanFileInfo("empty.bin", 0, "application/octet-stream")) { ByteArrayInputStream(ByteArray(0)) },
            )
            pc.lan.send(pc.lan.newTransferId(), peer, files) { _, e -> pc.events += e }

            val waiting = pc.waitFor { it is LanEvent.Waiting } as LanEvent.Waiting
            val request = phone.waitFor { it is LanEvent.Request } as LanEvent.Request
            assertEquals(waiting.code, request.code, "both sides must show the same code")
            assertEquals("Alex · DESKTOP", request.peer)
            assertEquals(listOf("video.mp4", "note.txt", "empty.bin"), request.files.map { it.name }, "names are cleaned of folders")

            pc.waitFor { it is LanEvent.Finished }
            phone.waitFor { it is LanEvent.Finished }
            assertContentEquals(big, phone.saved["video.mp4"])
            assertContentEquals(small, phone.saved["note.txt"])
            assertContentEquals(ByteArray(0), phone.saved["empty.bin"])
            assertEquals(3, pc.events.count { it is LanEvent.FileDone })
            assertEquals(3, phone.events.count { it is LanEvent.FileDone })
        } finally {
            phone.lan.stopAll(); pc.lan.stopAll(); phone.scope.cancel(); pc.scope.cancel()
        }
    }

    @Test
    fun declineIsReported() = runBlocking<Unit> {
        val phone = Side("Asha · Galaxy", "phone")
        val pc = Side("Alex · MAC", "computer")
        try {
            phone.lan.startReceiving(phone.saver) { _, e -> phone.events += e; if (e is LanEvent.Request) phone.lan.decline() }
            pc.lan.startDiscovery()
            val peer = withTimeout(10_000) { pc.lan.peers.first { it.isNotEmpty() } }.first()
            pc.lan.send(pc.lan.newTransferId(), peer, listOf(LanOutgoing(LanFileInfo("a.txt", 1)) { ByteArrayInputStream(byteArrayOf(1)) })) { _, e -> pc.events += e }
            val d = pc.waitFor { it is LanEvent.Declined || it is LanEvent.Failed }
            assertTrue(d is LanEvent.Declined, "got $d")
            assertTrue(phone.saved.isEmpty())
        } finally {
            phone.lan.stopAll(); pc.lan.stopAll(); phone.scope.cancel(); pc.scope.cancel()
        }
    }

    @Test
    fun senderGivingUpWithdrawsTheRequest() = runBlocking<Unit> {
        val phone = Side("Asha · Galaxy", "phone")
        val pc = Side("Alex · PC", "computer")
        try {
            phone.lan.startReceiving(phone.saver) { _, e -> phone.events += e } // never answers
            pc.lan.startDiscovery()
            val peer = withTimeout(10_000) { pc.lan.peers.first { it.isNotEmpty() } }.first()
            pc.lan.send(pc.lan.newTransferId(), peer, listOf(LanOutgoing(LanFileInfo("a.txt", 1)) { ByteArrayInputStream(byteArrayOf(1)) })) { _, e -> pc.events += e }
            phone.waitFor { it is LanEvent.Request }
            pc.lan.cancel()
            phone.waitFor { it is LanEvent.RequestGone }
        } finally {
            phone.lan.stopAll(); pc.lan.stopAll(); phone.scope.cancel(); pc.scope.cancel()
        }
    }

    @Test
    fun cleanName() {
        assertEquals("passwd", LanShare.cleanName("../../etc/passwd"))
        assertEquals("a.txt", LanShare.cleanName("C:\\Users\\x\\a.txt"))
        assertEquals("file", LanShare.cleanName(".."))
        assertEquals("bad name.txt", LanShare.cleanName("bad<>:|?* name.txt"))
    }
}
