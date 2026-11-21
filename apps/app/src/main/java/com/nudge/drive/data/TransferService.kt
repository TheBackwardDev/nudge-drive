package com.nudge.drive.data

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nudge.drive.MainActivity
import com.nudge.drive.NudgeApp
import com.nudge.drive.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps uploads/downloads alive while the app is in the background
 * and shows a live progress notification. Stops itself when nothing is transferring.
 */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: kotlinx.coroutines.Job? = null
    /** Latest start request; stopping with it never kills a start that's still on its way. */
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Every startForegroundService() must be answered with startForeground(), or Android crashes the app.
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, build("Preparing transfer…", 0, true), type)
        if (watcher == null) {
            val transfers = (application as NudgeApp).container.transfers
            watcher = scope.launch {
                transfers.items.collectLatest { list ->
                    val active = list.filter { it.state == TransferState.Running || it.state == TransferState.Queued }
                    if (active.isEmpty()) {
                        kotlinx.coroutines.delay(400) // a new transfer may be just about to be added
                        ServiceCompat.stopForeground(this@TransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        if (stopSelfResult(lastStartId)) watcher = null
                        return@collectLatest
                    }
                    val p = (active.map { it.progress }.average() * 100).toInt()
                    val up = active.count { it.kind == TransferKind.Upload }
                    val title = if (up > 0) "Uploading ${active.size} file${if (active.size > 1) "s" else ""}" else "Downloading ${active.size} file${if (active.size > 1) "s" else ""}"
                    runCatching { NotificationManagerCompat.from(this@TransferService).notify(NOTIF_ID, build(title, p, false, active.first().name)) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, progress: Int, indeterminate: Boolean, text: String? = null) =
        NotificationCompat.Builder(this, NotificationCenter.CH_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setColor(0xFF8B72F5.toInt())
            .setContentTitle(title)
            .setContentText(text?.let { "$progress% · $it" })
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val NOTIF_ID = 4242
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java)) }
        }
    }
}
