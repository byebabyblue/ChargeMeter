package com.local.chargemeter.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.os.Build
import android.os.Process

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val backgroundServiceMs: Long,
)

class AppUsageReader(private val context: Context) {
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        val now = System.currentTimeMillis()
        return context.getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60L * 60L * 1000L, now)
            .orEmpty()
            .isNotEmpty()
    }

    fun readLast24Hours(): List<AppUsageRow> {
        if (!hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val usageManager = context.getSystemService(UsageStatsManager::class.java)
        return usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 24L * 60L * 60L * 1000L,
            now,
        ).orEmpty()
            .asSequence()
            .filter { it.totalTimeInForeground > 0L || (Build.VERSION.SDK_INT >= 29 && it.totalTimeForegroundServiceUsed > 0L) }
            .map { stats ->
                val label = AppAssets.label(context, stats.packageName)
                AppUsageRow(
                    packageName = stats.packageName,
                    label = label,
                    foregroundMs = stats.totalTimeInForeground,
                    backgroundServiceMs = if (Build.VERSION.SDK_INT >= 29) stats.totalTimeForegroundServiceUsed else 0L,
                )
            }
            .groupBy { it.packageName }
            .map { (_, rows) ->
                AppUsageRow(
                    packageName = rows.first().packageName,
                    label = rows.first().label,
                    foregroundMs = rows.sumOf { it.foregroundMs },
                    backgroundServiceMs = rows.sumOf { it.backgroundServiceMs },
                )
            }
            .sortedByDescending { it.foregroundMs }
            .take(50)
            .toList()
    }

    fun currentForegroundPackage(): String? {
        if (!hasPermission()) return null
        val usageManager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usageManager.queryEvents(now - 10L * 60L * 1000L, now)
        val event = UsageEvents.Event()
        var foregroundPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundPackage = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (foregroundPackage == event.packageName) foregroundPackage = null
                }
            }
        }
        return foregroundPackage
    }

    fun screenOnDuration(startAt: Long, endAt: Long): Long {
        if (!hasPermission() || endAt <= startAt) return 0L
        val usageManager = context.getSystemService(UsageStatsManager::class.java)
        val events = usageManager.queryEvents((startAt - 24L * 60L * 60L * 1000L).coerceAtLeast(0L), endAt)
        val event = UsageEvents.Event()
        var screenOn = false
        var onAt = startAt
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!screenOn) {
                        screenOn = true
                        onAt = event.timeStamp.coerceAtLeast(startAt)
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (screenOn && event.timeStamp >= startAt) {
                        total += (event.timeStamp.coerceAtMost(endAt) - onAt).coerceAtLeast(0L)
                    }
                    screenOn = false
                    onAt = startAt
                }
            }
        }
        if (screenOn) {
            total += (endAt - onAt).coerceAtLeast(0L)
        }
        return total.coerceAtMost(endAt - startAt)
    }
}
