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
import com.fuzzydl.app.R
import com.fuzzydl.app.data.PartStage
import com.fuzzydl.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Serviço em primeiro plano (dataSync). Mantém o processo vivo durante o
 * download, segura WakeLock/WifiLock e publica o progresso a 1 Hz.
 */
class DownloadService : Service() {

    companion object {
        const val ACTION_START = "com.fuzzydl.app.action.START"
        const val ACTION_PAUSE = "com.fuzzydl.app.action.PAUSE"
        const val ACTION_RESUME = "com.fuzzydl.app.action.RESUME"
        const val ACTION_CANCEL = "com.fuzzydl.app.action.CANCEL"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> DownloadEngine.pause()
            ACTION_RESUME -> DownloadEngine.start()
            ACTION_CANCEL -> DownloadEngine.cancel()
        }
        promoteToForeground(buildNotification(DownloadEngine.state.value))
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
            DownloadEngine.state.sample(1_000L).collect { state ->
                if (!state.running && !state.paused) {
                    if (state.finished) {
                        notifyFinished()
                    } else {
                        stopSelfCleanly()
                    }
                    return@collect
                }
                if (state.running) acquireLocks() else releaseLocks()
                val notification = buildNotification(state)
                val manager = getSystemService(NotificationManager::class.java)
                if (manager != null && state.running) {
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    promoteToForeground(notification)
                }
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

    private fun buildNotification(state: EngineState): Notification {
        val part = state.parts.firstOrNull { it.index == state.currentIndex }
        val total = state.parts.size.coerceAtLeast(1)
        val title = when {
            state.notice == EngineNotice.NOT_WIFI -> getString(R.string.warn_not_wifi)
            state.paused -> getString(R.string.stage_paused)
            part != null -> getString(
                R.string.notif_progress,
                part.index,
                total,
                stageLabel(part.stage),
            )
            else -> getString(R.string.notif_preparing)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (part != null && (state.running || state.paused)) {
            val (done, max, indeterminate) = when (part.stage) {
                PartStage.DOWNLOADING -> Triple(part.bytesDone, part.bytesTotal, part.bytesTotal <= 0)
                PartStage.EXTRACTING -> Triple(part.extractDone, part.extractTotal, part.extractTotal <= 0)
                PartStage.VERIFYING -> Triple(1L, 1L, false)
                else -> Triple(0L, 0L, true)
            }
            builder.setProgress(1_000, if (indeterminate) 0 else progressPermille(done, max), indeterminate)
            builder.setContentText(contentText(state, part))
        }

        if (state.paused) {
            builder.addAction(
                0,
                getString(R.string.notif_resume),
                serviceIntent(ACTION_RESUME, 2),
            )
        } else if (state.running) {
            builder.addAction(
                0,
                getString(R.string.notif_pause),
                serviceIntent(ACTION_PAUSE, 1),
            )
        } else if (state.finished) {
            builder.setOngoing(false)
        }
        if (state.running || state.paused) {
            builder.addAction(
                0,
                getString(R.string.notif_cancel),
                serviceIntent(ACTION_CANCEL, 3),
            )
        }
        return builder.build()
    }

    private fun contentText(state: EngineState, part: PartUiState): String {
        val error = state.lastError
        if (state.paused && !error.isNullOrBlank()) return error
        return when (part.stage) {
            PartStage.DOWNLOADING -> {
                val speed = formatSpeed(part.speedBps)
                val eta = formatEta(part.etaSecs)
                "$speed · $eta"
            }
            PartStage.EXTRACTING -> getString(
                R.string.stage_extracting,
            ) + " · " + part.filesExtracted + " " + getString(R.string.label_files)
            PartStage.VERIFYING -> getString(R.string.stage_verifying)
            PartStage.RESOLVING -> getString(R.string.stage_resolving)
            PartStage.PAUSED -> getString(R.string.stage_paused)
            PartStage.FAILED -> part.error ?: getString(R.string.stage_failed)
            else -> getString(R.string.notif_preparing)
        }
    }

    private fun stageLabel(stage: PartStage): String = getString(
        when (stage) {
            PartStage.PENDING -> R.string.stage_pending
            PartStage.RESOLVING -> R.string.stage_resolving
            PartStage.DOWNLOADING -> R.string.stage_downloading
            PartStage.VERIFYING -> R.string.stage_verifying
            PartStage.EXTRACTING -> R.string.stage_extracting
            PartStage.PAUSED -> R.string.stage_paused
            PartStage.DONE -> R.string.stage_done
            PartStage.FAILED -> R.string.stage_failed
        },
    )

    private fun progressPermille(done: Long, max: Long): Int {
        if (max <= 0) return 0
        return ((done.toDouble() / max.toDouble()) * 1_000.0).toInt().coerceIn(0, 1_000)
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "—"
        val mib = bytesPerSecond / 1_048_576.0
        return String.format(Locale.US, "%.2f MiB/s", mib)
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "ETA —"
        return String.format(
            Locale.US,
            "ETA %02d:%02d:%02d",
            seconds / 3600,
            seconds % 3600 / 60,
            seconds % 60,
        )
    }

    private fun notifyFinished() {
        if (finished) return
        finished = true
        releaseLocks()
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_done))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        scope.launch {
            delay(1_500)
            stopSelf()
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
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
                power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fuzzydl:download")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()
        }
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = runCatching {
                wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "fuzzydl:wifi")?.apply {
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
