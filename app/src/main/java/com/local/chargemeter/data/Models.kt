package com.local.chargemeter.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "charge_sessions")
data class ChargeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startLevel: Int,
    val endLevel: Int = startLevel,
    val peakPowerW: Double = 0.0,
    val energyWh: Double = 0.0,
    val sampleCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val startChargeCounterMah: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val chargedMah: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val equivalentCycles: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val estimatedHealthPct: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val systemCycleCount: Int = 0,
    @ColumnInfo(defaultValue = "6700") val designCapacityMah: Int = 6700,
    @ColumnInfo(defaultValue = "-1") val healthStartLevel: Int = -1,
    @ColumnInfo(defaultValue = "0") val healthStartChargeCounterMah: Double = 0.0,
)

@Entity(
    tableName = "charge_samples",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("recordedAt")],
)
data class ChargeSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val recordedAt: Long,
    val powerW: Double,
    val level: Int,
    val temperatureC: Double,
    val voltageV: Double,
    val currentA: Double,
    @ColumnInfo(defaultValue = "0") val chargeCounterMah: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val systemCycleCount: Int = 0,
)

@Entity(
    tableName = "temperature_samples",
    indices = [Index("recordedAt")],
)
data class TemperatureSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: Long,
    val temperatureC: Double,
    val powerW: Double,
    val level: Int,
    val isCharging: Boolean,
)

@Entity(
    tableName = "app_power_samples",
    indices = [Index("packageName"), Index("recordedAt")],
)
data class AppPowerSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val recordedAt: Long,
    val powerW: Double,
    val isCharging: Boolean,
)

data class AppPowerAverage(
    val packageName: String,
    val averagePowerW: Double,
    val sampleCount: Int,
)

data class BatteryReading(
    val timestamp: Long = System.currentTimeMillis(),
    val isPresent: Boolean = true,
    val isPowerConnected: Boolean = false,
    val isCharging: Boolean = false,
    val level: Int = 0,
    val temperatureC: Double = 0.0,
    val voltageV: Double = 0.0,
    val currentA: Double = 0.0,
    val powerW: Double = 0.0,
    val health: String = "未知",
    val plugType: String = "未连接",
    val chargeCounterMah: Double = 0.0,
    val cycleCount: Int = 0,
    val designCapacityMah: Int = 6700,
)
