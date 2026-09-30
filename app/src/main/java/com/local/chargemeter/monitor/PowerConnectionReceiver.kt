package com.local.chargemeter.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enabled = context.getSharedPreferences("charge_settings", Context.MODE_PRIVATE)
            .getBoolean("monitor_notification_enabled", true)
        if (!enabled) return
        val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
            action = intent.action ?: BatteryMonitorService.ACTION_RESTART_MONITOR
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
