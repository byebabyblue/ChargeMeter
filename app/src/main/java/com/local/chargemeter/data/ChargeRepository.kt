package com.local.chargemeter.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.exp
import kotlin.math.absoluteValue

class ChargeRepository(private val dao: ChargeDao) {
    private val recordMutex = Mutex()

    val sessions = dao.observeSessions()
    val recentSamples = dao.observeRecentSamples()

    fun observeSession(id: Long) = dao.observeSession(id)
    fun observeSamples(id: Long) = dao.observeSamples(id)
    fun observeTemperatureSince(since: Long) = dao.observeTemperatureSince(since)
    fun observeAppPowerSamplesSince(since: Long) = dao.observeAppPowerSamplesSince(since)
    fun observeAppPowerAverages(since: Long) = dao.observeAppPowerAverages(since)

    suspend fun endOpenSession(timestamp: Long, level: Int) = recordMutex.withLock {
        val openSession = dao.getOpenSession() ?: return@withLock
        if (openSession.startedAt > timestamp) return@withLock
        val trailingNoise = dao.getLatestSamples(openSession.id, 8)
            .takeWhile { it.powerW < 0.0 }
        if (trailingNoise.isNotEmpty()) dao.deleteSamples(trailingNoise.map { it.id })
        val lastValid = dao.getLatestSample(openSession.id)
        dao.updateSession(
            openSession.copy(
                endedAt = lastValid?.recordedAt ?: timestamp,
                endLevel = lastValid?.level ?: level,
                sampleCount = (openSession.sampleCount - trailingNoise.size).coerceAtLeast(0),
            ),
        )
    }

    suspend fun recordForegroundPower(packageName: String?, reading: BatteryReading) {
        if (packageName.isNullOrBlank()) return
        dao.insertAppPowerSample(
            AppPowerSample(
                packageName = packageName,
                recordedAt = reading.timestamp,
                powerW = if (reading.isCharging) 0.0 else reading.powerW.absoluteValue,
                isCharging = reading.isCharging,
            ),
        )
        dao.deleteAppPowerBefore(reading.timestamp - 7L * 24L * 60L * 60L * 1000L)
    }

    suspend fun record(reading: BatteryReading) = recordMutex.withLock {
        dao.insertTemperatureSample(
            TemperatureSample(
                recordedAt = reading.timestamp,
                temperatureC = reading.temperatureC,
                powerW = reading.powerW,
                level = reading.level,
                isCharging = reading.isCharging,
            ),
        )
        dao.deleteTemperatureBefore(reading.timestamp - 30L * 24L * 60L * 60L * 1000L)
        val openSession = dao.getOpenSession()
        if (!reading.isPowerConnected) {
            if (openSession != null) endOpenSessionLocked(openSession, reading.timestamp, reading.level)
            return@withLock
        }
        // Keep an existing session open across brief net-discharge spikes, but do not
        // create a charging session merely because a cable is connected.
        if (openSession == null && !reading.isCharging) return@withLock

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
            dao.updateSession(
                session.copy(
                    endedAt = reading.timestamp,
                    endLevel = previous.level,
                ),
            )
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
            max(session.chargedMah, reading.chargeCounterMah - session.startChargeCounterMah)
        } else {
            session.chargedMah
        }
        val levelGain = previous?.let { (reading.level - it.level).coerceAtLeast(0) } ?: 0
        val midLevel = previous?.let { (reading.level + it.level) / 2.0 } ?: reading.level.toDouble()
        val addedEquivalentCycle = (levelGain / 100.0) * wearWeight(midLevel)
        val percentGain = (reading.level - session.startLevel).coerceAtLeast(0)
        val fullCapacityEstimate = if (percentGain >= HEALTH_ESTIMATE_MIN_GAIN && chargedMah > 0.0) {
            chargedMah / (percentGain / 100.0)
        } else {
            0.0
        }
        val healthEstimate = if (fullCapacityEstimate > 0.0) {
            (fullCapacityEstimate / reading.designCapacityMah * 100.0).coerceIn(50.0, 100.0)
        } else {
            session.estimatedHealthPct
        }
        dao.updateSession(
            session.copy(
                endLevel = reading.level,
                peakPowerW = max(session.peakPowerW, reading.powerW),
                energyWh = session.energyWh + addedEnergy,
                sampleCount = session.sampleCount + 1,
                chargedMah = chargedMah,
                equivalentCycles = session.equivalentCycles + addedEquivalentCycle,
                estimatedHealthPct = healthEstimate,
                systemCycleCount = reading.cycleCount,
                designCapacityMah = reading.designCapacityMah,
            ),
        )
    }

    private fun wearWeight(stateOfCharge: Double): Double =
        0.55 + 1.45 / (1.0 + exp(-(stateOfCharge - 80.0) / 6.0))

    private companion object {
        const val HEALTH_ESTIMATE_MIN_GAIN = 60
    }

    private suspend fun endOpenSessionLocked(openSession: ChargeSession, timestamp: Long, level: Int) {
        val trailingNoise = dao.getLatestSamples(openSession.id, 8)
            .takeWhile { it.powerW < 0.0 }
        if (trailingNoise.isNotEmpty()) dao.deleteSamples(trailingNoise.map { it.id })
        val lastValid = dao.getLatestSample(openSession.id)
        dao.updateSession(
            openSession.copy(
                endedAt = lastValid?.recordedAt ?: timestamp,
                endLevel = lastValid?.level ?: level,
                sampleCount = (openSession.sampleCount - trailingNoise.size).coerceAtLeast(0),
            ),
        )
    }
}
