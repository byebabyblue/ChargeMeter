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
    private lateinit var fluidCloudPublisher: FluidCloudPublisher
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        reader = BatteryReader(this)
        appUsageReader = AppUsageReader(applicationContext)
        fluidCloudPublisher = FluidCloudPublisher(applicationContext)
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
                updateNotifications(disconnected)
                fluidCloudPublisher.publish(disconnected)
            }
        }
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch {
                val repository = (application as ChargeMeterApplication).repository
                var lastRecordedAt = 0L
                var lastFluidCloudAt = 0L
                while (isActive) {
                    val reading = reader.read()
                    updateNotifications(reading)
                    if (reading.timestamp - lastRecordedAt >= SAMPLE_INTERVAL_MS) {
                        runCatching {
                            repository.record(reading)
                            repository.recordForegroundPower(appUsageReader.currentForegroundPackage(), reading)
                        }
                        lastRecordedAt = reading.timestamp
                    }
                    if (reading.timestamp - lastFluidCloudAt >= FLUID_CLOUD_INTERVAL_MS) {
                        fluidCloudPublisher.publish(reading)
                        lastFluidCloudAt = reading.timestamp
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
        if (Build.VERSION.SDK_INT >= 36) {
            getSystemService(NotificationManager::class.java).cancel(LIVE_NOTIFICATION_ID)
        }
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
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        if (Build.VERSION.SDK_INT >= 36) {
            manager.createNotificationChannel(NotificationChannel(
                LIVE_CHANNEL_ID, "充电实时状态", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "实时更新充电状态与状态栏胶囊"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            })
        }
    }

    private fun notification(
        title: String,
        text: String,
        reading: com.local.chargemeter.data.BatteryReading,
    ): Notification {
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

    private fun updateNotifications(reading: com.local.chargemeter.data.BatteryReading) {
        val manager = getSystemService(NotificationManager::class.java)
        // A transient notification failure must not terminate the sampling coroutine.
        runCatching { manager.notify(NOTIFICATION_ID, batteryNotification(reading)) }
            .onFailure { android.util.Log.e("ChargeMeterMonitor", "Monitor notification update failed", it) }
        if (Build.VERSION.SDK_INT < 36) return
        val preferences = getSharedPreferences("charge_settings", MODE_PRIVATE)
        if (!fluidCloudEnabled() || !reading.isPowerConnected) {
            manager.cancel(LIVE_NOTIFICATION_ID)
            preferences.edit().putString(FluidCloudPublisher.KEY_LAST_STATUS,
                if (fluidCloudEnabled()) "等待连接电源" else "已关闭").apply()
            return
        }
        runCatching {
            val openIntent = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val power = kotlin.math.abs(reading.powerW)
            val chip = if (power >= 100) String.format(Locale.US, "%.0fW", power)
                else String.format(Locale.US, "%.1fW", power)
            val status = if (reading.isCharging) "充电中" else "已连接 · 放电中"
            val notification = Notification.Builder(this, LIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$status · $chip")
                .setContentText(String.format(Locale.getDefault(), "%d%% · %.1f°C · %.2fV · %.2fA",
                    reading.level, reading.temperatureC, reading.voltageV, kotlin.math.abs(reading.currentA)))
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setWhen(reading.timestamp)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setColor(android.graphics.Color.rgb(18, 216, 90))
                .setStyle(Notification.ProgressStyle()
                    .addProgressSegment(Notification.ProgressStyle.Segment(100)
                        .setColor(android.graphics.Color.rgb(18, 216, 90)))
                    .setStyledByProgress(true)
                    .setProgressTrackerIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification))
                    .setProgress(reading.level.coerceIn(0, 100)))
                .setShortCriticalText(chip)
                .setRequestPromotedOngoing(true)
                .build()
            // Keep one stable live-notification ID, separate from the foreground-service notification.
            manager.notify(LIVE_NOTIFICATION_ID, notification)
            val statusText = if (manager.canPostPromotedNotifications()) {
                "实时状态已更新 · " + java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(reading.timestamp))
            } else "请允许系统实时通知权限"
            preferences.edit().putString(FluidCloudPublisher.KEY_LAST_STATUS, statusText)
                .putLong(FluidCloudPublisher.KEY_LAST_STATUS_AT, reading.timestamp).apply()
        }.onFailure {
            android.util.Log.e("ChargeMeterMonitor", "Live notification update failed", it)
            preferences.edit().putString(FluidCloudPublisher.KEY_LAST_STATUS, "实时状态更新失败，请重新开启").apply()
        }
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
        return notification(title, text, reading)
    }

    private fun monitorEnabled(): Boolean =
        getSharedPreferences("charge_settings", MODE_PRIVATE)
            .getBoolean("monitor_notification_enabled", true)

    private fun fluidCloudEnabled(): Boolean =
        getSharedPreferences("charge_settings", MODE_PRIVATE)
            .getBoolean("fluid_cloud_enabled", true)

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
        private const val LIVE_NOTIFICATION_ID = 38
        private const val LIVE_CHANNEL_ID = "charge_live_status"
        private const val RESTART_REQUEST_CODE = 38
        private const val RESTART_AFTER_TASK_REMOVED_MS = 1_500L
        private const val RESTART_AFTER_DESTROYED_MS = 4_000L
        private const val WATCHDOG_INTERVAL_MS = 10L * 60L * 1000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val SAMPLE_INTERVAL_MS = 30_000L
        private const val FLUID_CLOUD_INTERVAL_MS = 30_000L
    }
}
