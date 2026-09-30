package com.local.chargemeter.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.local.chargemeter.data.BatteryReading
import kotlin.math.pow

enum class DualCellMode { Voltage, Current }

class BatteryReader(private val context: Context) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val preferences = context.getSharedPreferences("charge_settings", Context.MODE_PRIVATE)

    fun read(): BatteryReading {
        val timestamp = System.currentTimeMillis()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryReading(isPresent = false)

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val standardVoltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val vendorBatteryVoltageMv = intent.getIntExtra("battery_now_voltage_type", 0)
        val temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val rawCurrent = batteryManager
            .getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeUnless { it == Long.MIN_VALUE } ?: 0L
        val chargeCounterMicroAh = batteryManager
            .getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeUnless { it == Long.MIN_VALUE } ?: 0L

        val usesOplusUnits = vendorBatteryVoltageMv in 1000..10_000 && standardVoltageMv < 1000
        val voltageV = vendorBatteryVoltageMv.takeIf { it in 1000..10_000 }?.div(1000.0)
            ?: standardVoltageMv.takeIf { it >= 1000 }?.div(1000.0)
            ?: 0.0
        val reportedCurrentA = if (usesOplusUnits) {
            rawCurrent / 1000.0
        } else {
            rawCurrent / 1_000_000.0
        }
        // Android defines positive as current entering the battery. This Oplus battery
        // interface reports the opposite sign, so normalize before using the value.
        val normalizedCurrentA = if (usesOplusUnits) -reportedCurrentA else reportedCurrentA
        val currentDirection = if (preferences.getBoolean("current_direction_inverted", false)) -1.0 else 1.0
        val currentScaleExponent = preferences.getInt("current_scale_exponent", 0).coerceIn(-6, 6)
        val correctedCurrentA = normalizedCurrentA * currentDirection * 10.0.pow(currentScaleExponent)
        val powerConnected = plugged != 0
        val netChargeCurrentA = currentAfterConnectionTransition(
            correctedCurrentA,
            powerConnected,
            timestamp,
        )
        val dualCellEnabled = preferences.getBoolean("dual_cell_enabled", Build.MODEL.equals("PLC110", ignoreCase = true))
        val dualCellMode = runCatching {
            DualCellMode.valueOf(preferences.getString("dual_cell_mode", DualCellMode.Current.name) ?: DualCellMode.Current.name)
        }.getOrDefault(DualCellMode.Current)
        val designCapacityMah = preferences.getInt("rated_capacity_mah", designCapacityForDevice())
            .coerceIn(1_000, 20_000)
        val displayedVoltageV = voltageV * if (dualCellEnabled && dualCellMode == DualCellMode.Voltage) 2.0 else 1.0
        val displayedCurrentA = netChargeCurrentA * if (dualCellEnabled && dualCellMode == DualCellMode.Current) 2.0 else 1.0
        // BATTERY_PROPERTY_CHARGE_COUNTER already represents the battery pack's
        // remaining charge. Dual-cell display conversion must not be applied here.
        val equivalentChargeCounterMah = (chargeCounterMicroAh / 1000.0).coerceAtLeast(0.0)
        val systemAcceptsCharge = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val charging = powerConnected && systemAcceptsCharge && netChargeCurrentA > CURRENT_DIRECTION_DEADBAND_A

        return BatteryReading(
            timestamp = timestamp,
            isPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true),
            isPowerConnected = powerConnected,
            isCharging = charging,
            level = (level * 100 / scale).coerceIn(0, 100),
            temperatureC = temperatureTenths / 10.0,
            voltageV = displayedVoltageV,
            currentA = displayedCurrentA,
            powerW = displayedVoltageV * displayedCurrentA,
            health = healthLabel(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)),
            plugType = plugLabel(plugged),
            chargeCounterMah = equivalentChargeCounterMah,
            cycleCount = intent.getIntExtra("android.os.extra.CYCLE_COUNT", 0),
            designCapacityMah = designCapacityMah,
        )
    }

    private fun healthLabel(value: Int) = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "温度过高"
        BatteryManager.BATTERY_HEALTH_DEAD -> "需更换"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "电压过高"
        BatteryManager.BATTERY_HEALTH_COLD -> "温度过低"
        else -> "未知"
    }

    private fun plugLabel(value: Int) = when (value) {
        BatteryManager.BATTERY_PLUGGED_AC -> "充电器"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "底座"
        else -> "未连接"
    }

    private fun designCapacityForDevice(): Int = when (Build.MODEL.uppercase()) {
        "PLC110" -> 6700
        else -> 5000
    }

    private companion object {
        private val connectionStateLock = Any()
        private var lastPowerConnected: Boolean? = null
        private var lastPowerTransitionAt: Long = 0L
        private const val CURRENT_DIRECTION_DEADBAND_A = 0.02
        private const val CONNECTION_TRANSITION_IGNORE_MS = 1_500L

        private fun currentAfterConnectionTransition(
            currentA: Double,
            powerConnected: Boolean,
            timestamp: Long,
        ): Double = synchronized(connectionStateLock) {
            val previous = lastPowerConnected
            if (previous != null && previous != powerConnected) {
                lastPowerTransitionAt = timestamp
            }
            lastPowerConnected = powerConnected
            if (timestamp - lastPowerTransitionAt < CONNECTION_TRANSITION_IGNORE_MS) 0.0 else currentA
        }
    }

}
