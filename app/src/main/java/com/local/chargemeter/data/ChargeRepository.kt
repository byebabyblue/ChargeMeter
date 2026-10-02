package com.local.chargemeter.data

import androidx.room.withTransaction
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.exp
import kotlin.math.absoluteValue

class ChargeRepository(private val database: ChargeDatabase) {
    private val dao = database.chargeDao()
    private var lastCleanupElapsed = 0L
    private val recordMutex = Mutex()

    val sessions = dao.observeSessions()
    val recentSamples = dao.observeRecentSamples()

    fun observeSession(id: Long) = dao.observeSession(id)
    fun observeSamples(id: Long) = dao.observeSamples(id)
    fun observeTemperatureSince(since: Long) = dao.observeTemperatureSince(since)
    fun observeAppPowerSamplesSince(since: Long) = dao.observeAppPowerSamplesSince(since)
    fun observeAppPowerAverages(since: Long) = dao.observeAppPowerAverages(since)

    suspend fun endOpenSession(timestamp: Long, level: Int) = recordMutex.withLock {
        database.withTransaction {
            val session = dao.getOpenSession() ?: return@withTransaction
            if (session.startedAt <= timestamp) endOpenSessionLocked(session, timestamp, level)
        }
    }

    suspend fun cleanupHistory(timestamp: Long) = recordMutex.withLock {
        val elapsed = SystemClock.elapsedRealtime()
        if (lastCleanupElapsed != 0L && elapsed - lastCleanupElapsed < 3_600_000L) return@withLock
        database.withTransaction {
            dao.deleteTemperatureBefore(timestamp - 30L * 24L * 60L * 60L * 1000L)
            dao.deleteAppPowerBefore(timestamp - 7L * 24L * 60L * 60L * 1000L)
        }
        lastCleanupElapsed = elapsed
    }

    private suspend fun recordForegroundPower(packageName: String?, reading: BatteryReading) {
        if (packageName.isNullOrBlank()) return
        dao.insertAppPowerSample(
            AppPowerSample(
                packageName = packageName,
                recordedAt = reading.timestamp,
                powerW = if (reading.isCharging) 0.0 else reading.powerW.absoluteValue,
                isCharging = reading.isCharging,
            ),
        )
    }

    suspend fun record(reading: BatteryReading, foregroundPackage: String? = null) = recordMutex.withLock {
        database.withTransaction {
            recordForegroundPower(foregroundPackage, reading)
            dao.insertTemperatureSample(
                TemperatureSample(
                    recordedAt = reading.timestamp,
                    temperatureC = reading.temperatureC,
                    powerW = reading.powerW,
                    level = reading.level,
                    isCharging = reading.isCharging,
                ),
            )
            val openSession = dao.getOpenSession()
            if (!reading.isPowerConnected) {
                if (openSession != null) endOpenSessionLocked(openSession, reading.timestamp, reading.level)
                return@withTransaction
            }
            // Keep an existing session open across brief net-discharge spikes, but do not
            // create a charging session merely because a cable is connected.
            if (openSession == null && !reading.isCharging) return@withTransaction

            var session = openSession ?: ChargeSession(
                startedAt = reading.timestamp,
                startLevel = reading.level,
                endLevel = reading.level,
                startChargeCounterMah = reading.chargeCounterMah,
                systemCycleCount = reading.cycleCount,
                designCapacityMah = reading.designCapacityMah,
            ).let { it.copy(id = dao.insertSession(it)) }

            var previous = dao.getLatestSample(session.id)
            val voltageRatio = if (previous != null && previous.voltageV > 0.0) {
                reading.voltageV / previous.voltageV
            } else 1.0
            val counterRatio = if (previous != null && previous.chargeCounterMah > 0.0 && reading.chargeCounterMah > 0.0) {
                reading.chargeCounterMah / previous.chargeCounterMah
            } else 1.0
            if (
                previous != null &&
                (voltageRatio > 1.6 || voltageRatio < 0.65 || counterRatio > 1.6 || counterRatio < 0.65)
            ) {
                endOpenSessionLocked(session, reading.timestamp, previous.level)
                session = ChargeSession(
                    startedAt = reading.timestamp,
                    startLevel = reading.level,
                    endLevel = reading.level,
                    startChargeCounterMah = reading.chargeCounterMah,
                    systemCycleCount = reading.cycleCount,
                    designCapacityMah = reading.designCapacityMah,
                ).let { it.copy(id = dao.insertSession(it)) }
                previous = null
            }
            val elapsedHours = previous?.let {
                ((reading.timestamp - it.recordedAt).coerceIn(0L, 120_000L) / 3_600_000.0)
            } ?: 0.0
            val addedEnergy = previous?.let {
                ((it.powerW.coerceAtLeast(0.0) + reading.powerW.coerceAtLeast(0.0)) / 2.0) * elapsedHours
            } ?: 0.0

            dao.insertSample(
                ChargeSample(
                    sessionId = session.id,
                    recordedAt = reading.timestamp,
                    powerW = reading.powerW,
                    level = reading.level,
                    temperatureC = reading.temperatureC,
                    voltageV = reading.voltageV,
                    currentA = reading.currentA,
                    chargeCounterMah = reading.chargeCounterMah,
                    systemCycleCount = reading.cycleCount,
                ),
            )
            val chargedMah = if (session.startChargeCounterMah > 0.0 && reading.chargeCounterMah > 0.0) {
                (reading.chargeCounterMah - session.startChargeCounterMah).coerceAtLeast(0.0)
            } else {
                session.chargedMah
            }
            val levelGain = previous?.let { (reading.level - it.level).coerceAtLeast(0) } ?: 0
            val midLevel = previous?.let { (reading.level + it.level) / 2.0 } ?: reading.level.toDouble()
            val addedEquivalentCycle = (levelGain / 100.0) * wearWeight(midLevel)
            val percentGain = (reading.level - session.startLevel).coerceAtLeast(0)
            val maxSampleGapMs = max(session.maxSampleGapMs,
                previous?.let { (reading.timestamp - it.recordedAt).coerceAtLeast(0L) } ?: 0L)
            val fullCapacityEstimate = if (percentGain >= HEALTH_ESTIMATE_MIN_GAIN && chargedMah > 0.0 && maxSampleGapMs <= 120_000L) {
                chargedMah / (percentGain / 100.0)
            } else {
                0.0
            }
            val healthEstimate = if (fullCapacityEstimate > 0.0) {
                (fullCapacityEstimate / reading.designCapacityMah * 100.0).coerceIn(50.0, 100.0)
            } else {
                0.0
            }
            dao.updateSession(
                session.copy(
                    endLevel = reading.level,
                    peakPowerW = max(session.peakPowerW, reading.powerW),
                    energyWh = session.energyWh + addedEnergy,
                    sampleCount = session.sampleCount + 1,
                    maxSampleGapMs = maxSampleGapMs,
                    chargedMah = chargedMah,
                    equivalentCycles = session.equivalentCycles + addedEquivalentCycle,
                    estimatedHealthPct = healthEstimate,
                    systemCycleCount = reading.cycleCount,
                    designCapacityMah = reading.designCapacityMah,
                ),
            )
        }
    }

    private fun wearWeight(stateOfCharge: Double): Double =
        0.55 + 1.45 / (1.0 + exp(-(stateOfCharge - 80.0) / 6.0))

    private companion object {
        const val HEALTH_ESTIMATE_MIN_GAIN = 60
    }

    private suspend fun endOpenSessionLocked(openSession: ChargeSession, timestamp: Long, level: Int) {
        val all = dao.getSamples(openSession.id)
        val samples = all.dropLastWhile { it.powerW < 0.0 }
        val removed = all.drop(samples.size)
        removed.chunked(500).forEach { batch -> dao.deleteSamples(batch.map { it.id }) }
        val last = samples.lastOrNull()
        var energy = 0.0
        var cycles = 0.0
        var gap = 0L
        samples.zipWithNext { first, second ->
            val delta = (second.recordedAt - first.recordedAt).coerceAtLeast(0L)
            gap = max(gap, delta)
            energy += (first.powerW.coerceAtLeast(0.0) + second.powerW.coerceAtLeast(0.0)) / 2.0 * delta.coerceAtMost(120_000L) / 3_600_000.0
            cycles += (second.level - first.level).coerceAtLeast(0) / 100.0 * wearWeight((first.level + second.level) / 2.0)
        }
        val charged = if (openSession.startChargeCounterMah > 0 && last != null && last.chargeCounterMah > 0) {
            (last.chargeCounterMah - openSession.startChargeCounterMah).coerceAtLeast(0.0)
        } else 0.0
        val endLevel = last?.level ?: level
        val gain = endLevel - openSession.startLevel
        val health = if (samples.size >= 2 && gap <= 120_000L && gain >= HEALTH_ESTIMATE_MIN_GAIN && charged > 0) {
            (charged * 10000.0 / (gain * openSession.designCapacityMah)).coerceIn(50.0, 100.0)
        } else 0.0
        dao.updateSession(openSession.copy(
            endedAt = last?.recordedAt ?: timestamp,
            endLevel = endLevel, sampleCount = samples.size,
            chargedMah = charged, energyWh = energy, equivalentCycles = cycles,
            peakPowerW = samples.maxOfOrNull { it.powerW.coerceAtLeast(0.0) } ?: 0.0,
            maxSampleGapMs = gap, estimatedHealthPct = health,
        ))
    }
}
