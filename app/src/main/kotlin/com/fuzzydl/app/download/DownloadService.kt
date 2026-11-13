package com.fuzzydl.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fuzzydl.app.R
import com.fuzzydl.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Serviço em primeiro plano para a fila de downloads. */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadService::class.java),
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground(buildNotification())
        if (observer == null) observe()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        observer = null
        releaseLocks()
        super.onDestroy()
    }

    @OptIn(FlowPreview::class)
    private fun observe() {
        observer = scope.launch {
            DownloadQueue.state.sample(1_000L).collect { state ->
                val active = state.values.any {
                    it.status == DlStatus.DOWNLOADING || it.status == DlStatus.PROBING || it.status == DlStatus.VERIFYING
                }
                val queued = state.values.any { it.status == DlStatus.QUEUED }
                if (!active && !queued) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                if (active) acquireLocks() else releaseLocks()
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    private fun promoteToForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    private fun buildNotification(): Notification {
        val state = DownloadQueue.state.value
        val current = state.values.firstOrNull { it.status == DlStatus.DOWNLOADING }
        val ready = state.values.count { it.status == DlStatus.READY }
        val title = when {
            current != null -> getString(R.string.notif_downloading, current.title)
            state.values.any { it.status == DlStatus.VERIFYING } -> getString(R.string.store_verifying)
            else -> getString(R.string.downloads_title)
        }
        val totalDone = state.values.sumOf { it.done }
        val totalSize = state.values.sumOf { if (it.total > 0) it.total else it.done }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (current != null) {
            builder.setProgress(1_000, (current.progress * 1_000).toInt(), current.total <= 0)
            builder.setContentText(
                getString(
                    R.string.notif_progress,
                    formatBytes(current.done),
                    formatBytes(current.total),
                ),
            )
        } else if (totalSize > 0) {
            builder.setProgress(1_000, ((totalDone.toDouble() / totalSize) * 1_000).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        if (ready > 0) {
            builder.setContentText(getString(R.string.notif_ready_count, ready))
        }
        return builder.build()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val gib = bytes / 1_073_741_824.0
        return if (gib >= 1.0) String.format(java.util.Locale.US, "%.2f GiB", gib)
        else String.format(java.util.Locale.US, "%.0f MiB", bytes / 1_048_576.0)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = runCatching {
                power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fuzzy:download")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()
        }
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = runCatching {
                wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "fuzzy:wifi")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
    }
}