package com.nudge.drive.desktop.data

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Keeps Nudge Drive to one running copy. A second launch (Start menu, "Send to", start-up) passes
 * its arguments to the running app over a loopback socket guarded by a per-session secret, then exits.
 */
object Instance {
    private val portFile = File(Platform.dataDir, "instance")

    /** Returns true if another copy took the arguments (this process should exit). */
    fun handOff(args: List<String>): Boolean = runCatching {
        val (port, secret) = portFile.readText().trim().split(':', limit = 2)
        Socket(InetAddress.getLoopbackAddress(), port.toInt()).use { s ->
            s.soTimeout = 3000
            s.getOutputStream().bufferedWriter().apply {
                write(secret); newLine()
                args.forEach { write(it); newLine() }
                flush()
            }
            s.shutdownOutput()
            s.getInputStream().bufferedReader().readLine() == "ok"
        }
    }.getOrDefault(false)

    /** Starts listening; [onArgs] runs (on a background thread) for each later launch. */
    fun listen(onArgs: (List<String>) -> Unit) {
        val server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        val secret = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        portFile.writeText("${server.localPort}:$secret")
        Platform.ownerOnly(portFile)
        thread(isDaemon = true, name = "nudge-instance") {
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    s.use {
                        it.soTimeout = 3000
                        // Read without closing: closing a socket's stream closes the socket (and the reply).
                        val reader = it.getInputStream().bufferedReader()
                        val lines = generateSequence { reader.readLine() }.toList()
                        if (lines.firstOrNull() == secret) {
                            it.getOutputStream().write("ok\n".toByteArray())
                            onArgs(lines.drop(1))
                        }
                    }
                }
            }
        }
    }
}
