package com.nudge.drive.desktop.data

import com.nudge.drive.data.Repository
import com.nudge.drive.data.UrlResponse
import com.nudge.drive.data.ApiClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * "Continue with Google" for the desktop app: opens the system browser and waits for the server to
 * send the one-time hand-off token to http://127.0.0.1:<port>/google-auth (loopback, RFC 8252).
 */
object GoogleLogin {
    suspend fun signIn(api: ApiClient, repo: Repository) {
        val result = CompletableDeferred<Map<String, String>>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/google-auth") { ex ->
            val q = (ex.requestURI.rawQuery ?: "").split('&').filter { '=' in it }
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
            val ok = q["token"] != null
            val html = page(ok)
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            val bytes = html.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
            result.complete(q)
        }
        server.start()
        try {
            val port = server.address.port
            val url = api.get<UrlResponse>("/auth/google/url?desktopPort=$port").url ?: error("Google sign-in isn't set up on the server.")
            Platform.openInBrowser(url)
            val q = withTimeout(10 * 60_000) { result.await() }
            val token = q["token"] ?: error("Google sign-in was cancelled or failed.")
            repo.exchangeGoogle(token)
        } finally {
            withContext(Dispatchers.IO) { server.stop(0) }
        }
    }

    private fun page(ok: Boolean) = """
        <!doctype html><html><head><meta charset="utf-8"><title>Nudge Drive</title>
        <style>body{margin:0;height:100vh;display:grid;place-items:center;font-family:Segoe UI,system-ui,sans-serif;
        background:linear-gradient(#d9ceff,#f7f4ff);color:#1e1440}div{background:#fff;padding:40px 48px;border-radius:28px;
        box-shadow:0 20px 60px rgba(110,80,220,.18);text-align:center}h1{margin:0 0 8px;font-size:24px}p{margin:0;color:#4e4670}</style>
        </head><body><div><h1>${if (ok) "You're signed in" else "Sign-in didn't finish"}</h1>
        <p>${if (ok) "You can close this tab and go back to Nudge Drive." else "Close this tab and try again in Nudge Drive."}</p></div></body></html>
    """.trimIndent()
}
