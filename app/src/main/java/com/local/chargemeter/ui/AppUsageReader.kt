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

    private var lastEventAt = 0L
    private var foregroundPackage: String? = null

    fun readLast24Hours(): List<AppUsageRow> {
        if (!hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val start = now - 24L * 60L * 60L * 1000L
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents((start - 24L * 60L * 60L * 1000L).coerceAtLeast(0L), now) ?: return emptyList()
        val foreground = mutableMapOf<String, Long>()
        val services = mutableMapOf<String, Long>()
        val activeActivities = mutableMapOf<String, MutableSet<String>>()
        val activeServices = mutableMapOf<String, MutableSet<String>>()
        val foregroundTotals = mutableMapOf<String, Long>()
        val serviceTotals = mutableMapOf<String, Long>()
        fun close(pkg: String, at: Long, active: MutableMap<String, Long>, totals: MutableMap<String, Long>) {
            active.remove(pkg)?.let { from ->
                totals[pkg] = (totals[pkg] ?: 0L) + (at.coerceAtMost(now) - from.coerceAtLeast(start)).coerceAtLeast(0L)
            }
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // Screen and shutdown events need not carry a package name.
            if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN) {
                foreground.keys.toList().forEach { close(it, event.timeStamp, foreground, foregroundTotals) }
                activeActivities.clear()
                if (event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN) {
                    services.keys.toList().forEach { close(it, event.timeStamp, services, serviceTotals) }
                    activeServices.clear()
                }
                continue
            }
            val pkg = event.packageName ?: continue
            val component = event.className ?: pkg
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (activeActivities.getOrPut(pkg) { mutableSetOf() }.add(component)) foreground.putIfAbsent(pkg, event.timeStamp)
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    activeActivities[pkg]?.remove(component)
                    if (activeActivities[pkg].isNullOrEmpty()) close(pkg, event.timeStamp, foreground, foregroundTotals)
                }
                UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                    if (activeServices.getOrPut(pkg) { mutableSetOf() }.add(component)) services.putIfAbsent(pkg, event.timeStamp)
                }
                UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                    activeServices[pkg]?.remove(component)
                    if (activeServices[pkg].isNullOrEmpty()) close(pkg, event.timeStamp, services, serviceTotals)
                }
            }
        }
        foreground.keys.toList().forEach { close(it, now, foreground, foregroundTotals) }
        services.keys.toList().forEach { close(it, now, services, serviceTotals) }
        return (foregroundTotals.keys + serviceTotals.keys).map { pkg ->
            AppUsageRow(pkg, AppAssets.label(context, pkg), foregroundTotals[pkg] ?: 0L, serviceTotals[pkg] ?: 0L)
        }.filter { it.foregroundMs > 0 || it.backgroundServiceMs > 0 }
            .sortedByDescending { it.foregroundMs }.take(50)
    }

    fun currentForegroundPackage(): String? {
        if (!hasPermission()) {
            lastEventAt = 0L
            foregroundPackage = null
            return null
        }
        val now = System.currentTimeMillis()
        if (lastEventAt > now) { lastEventAt = 0L; foregroundPackage = null }
        val from = if (lastEventAt == 0L) now - 24L * 60L * 60L * 1000L else lastEventAt
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, now) ?: return null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundPackage = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (foregroundPackage == event.packageName) foregroundPackage = null
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> foregroundPackage = null
            }
        }
        lastEventAt = now
        return foregroundPackage.takeIf { context.getSystemService(android.os.PowerManager::class.java).isInteractive }
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
