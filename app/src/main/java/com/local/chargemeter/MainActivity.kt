package com.local.chargemeter

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.local.chargemeter.monitor.BatteryMonitorService
import com.local.chargemeter.ui.ChargeMeterApp

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent { ChargeMeterApp() }
    }

    override fun onStart() {
        super.onStart()
        startMonitor()
    }

    override fun onStop() {
        val hideFromRecents = getSharedPreferences("charge_settings", MODE_PRIVATE)
            .getBoolean("hide_from_recents", true)
        getSystemService(ActivityManager::class.java).appTasks.forEach { task ->
            task.setExcludeFromRecents(hideFromRecents)
        }
        super.onStop()
    }

    private fun startMonitor() {
        val enabled = getSharedPreferences("charge_settings", MODE_PRIVATE)
            .getBoolean("monitor_notification_enabled", true)
        if (!enabled) return
        val intent = Intent(this, BatteryMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
