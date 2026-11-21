package com.nudge.drive.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual end-to-end check against a running desktop app: acts as a phone and sends the files in
 * $NUDGE_LAN_SEND_DIR to the first computer it finds. Skipped unless that variable is set.
 */
class LanManualSend {
    @Test
    fun sendToRunningComputer() = runBlocking<Unit> {
        val dir = System.getenv("NUDGE_LAN_SEND_DIR")?.let(::File) ?: return@runBlocking
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // NUDGE_LAN_SEND_TO=phone acts as a computer sending to a phone instead.
        val to = System.getenv("NUDGE_LAN_SEND_TO") ?: "computer"
        val lan = if (to == "phone") LanShare(scope, { "Asha · TEST-PC" }, "computer", ::println) else LanShare(scope, { "Asha · Pixel 9" }, "phone", ::println)
        val events = java.util.concurrent.CopyOnWriteArrayList<LanEvent>()
        lan.startDiscovery()
        val pc = withTimeout(15_000) { lan.peers.first { l -> l.any { it.kind == to } } }.first { it.kind == to }
        println("found ${pc.name} at ${pc.host}:${pc.port}")
        val files = dir.listFiles()!!.filter { it.isFile }.sortedBy { it.name }
        lan.send(lan.newTransferId(), pc, files.map { f -> LanOutgoing(LanFileInfo(f.name, f.length(), "application/octet-stream")) { f.inputStream() } }) { _, e -> events += e; println("event: $e") }
        withTimeout(180_000) { while (events.none { it is LanEvent.Finished || it is LanEvent.Failed || it is LanEvent.Declined }) delay(100) }
        lan.stopAll()
        assertTrue(events.any { it is LanEvent.Finished }, "transfer didn't finish: ${events.lastOrNull()}")
    }
}
