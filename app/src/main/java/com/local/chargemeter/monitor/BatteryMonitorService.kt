package com.local.chargemeter.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.local.chargemeter.ChargeMeterApplication
import com.local.chargemeter.MainActivity
import com.local.chargemeter.R
import com.local.chargemeter.ui.AppUsageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class BatteryMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var reader: BatteryReader
    private lateinit var appUsageReader: AppUsageReader
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        reader = BatteryReader(this)
        appUsageReader = AppUsageReader(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, batteryNotification(reader.read()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!monitorEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        cancelPendingRestart()
        scheduleRestart(WATCHDOG_INTERVAL_MS)
        if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
            scope.launch {
                val reading = reader.read()
                (application as ChargeMeterApplication).repository.endOpenSession(
                    timestamp = reading.timestamp,
                    level = reading.level,
                )
                val disconnected = reading.copy(
                    isPowerConnected = false,
                    isCharging = false,
                    currentA = 0.0,
                    powerW = 0.0,
                    plugType = "未连接",
                )
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, batteryNotification(disconnected))
            }
        }
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch {
                val repository = (application as ChargeMeterApplication).repository
                var lastRecordedAt = 0L
                while (isActive) {
                    val reading = reader.read()
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, batteryNotification(reading))
                    if (reading.timestamp - lastRecordedAt >= SAMPLE_INTERVAL_MS) {
                        runCatching {
                            repository.record(reading)
                            repository.recordForegroundPower(appUsageReader.currentForegroundPackage(), reading)
                        }
                        lastRecordedAt = reading.timestamp
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        // This is a continuous monitor rather than a finite command. Replaying every
        // old start Intent causes delivery counts and restart backoff to accumulate.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!monitorEnabled()) {
            super.onTaskRemoved(rootIntent)
            return
        }
        runCatching {
            val keepAliveIntent = Intent(this, BatteryMonitorService::class.java)
                .setAction(ACTION_KEEP_ALIVE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(keepAliveIntent)
            } else {
                startService(keepAliveIntent)
            }
        }
        scheduleRestart(RESTART_AFTER_TASK_REMOVED_MS)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.launch {
            (application as ChargeMeterApplication).repository.record(reader.read())
        }
        if (monitorEnabled()) scheduleRestart(RESTART_AFTER_DESTROYED_MS)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "充电记录",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "持续记录本次充电的功率与电池状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun batteryNotification(reading: com.local.chargemeter.data.BatteryReading): Notification {
        val title = if (reading.isCharging) {
            String.format(Locale.getDefault(), "充电中 · %.1f W", reading.powerW)
        } else if (reading.isPowerConnected) {
            String.format(Locale.getDefault(), "已连接 · 放电中 · %.1f W", kotlin.math.abs(reading.powerW))
        } else {
            String.format(Locale.getDefault(), "放电中 · %.1f W", kotlin.math.abs(reading.powerW))
        }
        val text = String.format(
            Locale.getDefault(),
            "%.2f V  ·  %.2f A  ·  %.1f°C",
            reading.voltageV,
            kotlin.math.abs(reading.currentA),
            reading.temperatureC,
        )
        return notification(title, text)
    }

    private fun monitorEnabled(): Boolean =
        getSharedPreferences("charge_settings", MODE_PRIVATE)
            .getBoolean("monitor_notification_enabled", true)

    private fun restartPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        RESTART_REQUEST_CODE,
        Intent(this, PowerConnectionReceiver::class.java).setAction(ACTION_RESTART_MONITOR),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleRestart(delayMs: Long) {
        runCatching {
            getSystemService(AlarmManager::class.java).set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + delayMs,
                restartPendingIntent(),
            )
        }
    }

    private fun cancelPendingRestart() {
        runCatching {
            getSystemService(AlarmManager::class.java).cancel(restartPendingIntent())
        }
    }

    companion object {
        const val ACTION_RESTART_MONITOR = "com.local.chargemeter.action.RESTART_MONITOR"
        private const val ACTION_KEEP_ALIVE = "com.local.chargemeter.action.KEEP_ALIVE"
        private const val CHANNEL_ID = "charge_monitor"
        private const val NOTIFICATION_ID = 37
        private const val RESTART_REQUEST_CODE = 38
        private const val RESTART_AFTER_TASK_REMOVED_MS = 1_500L
        private const val RESTART_AFTER_DESTROYED_MS = 4_000L
        private const val WATCHDOG_INTERVAL_MS = 10L * 60L * 1000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val SAMPLE_INTERVAL_MS = 30_000L
    }
}
