package com.local.chargemeter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeDao {
    @Query("SELECT * FROM charge_samples WHERE sessionId = :sessionId ORDER BY recordedAt ASC, id ASC")
    suspend fun getSamples(sessionId: Long): List<ChargeSample>

    @Insert
    suspend fun insertSession(session: ChargeSession): Long

    @Update
    suspend fun updateSession(session: ChargeSession)

    @Insert
    suspend fun insertSample(sample: ChargeSample)

    @Insert
    suspend fun insertTemperatureSample(sample: TemperatureSample)

    @Insert
    suspend fun insertAppPowerSample(sample: AppPowerSample)

    @Query("SELECT * FROM charge_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpenSession(): ChargeSession?

    @Query("SELECT * FROM charge_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: Long): Flow<ChargeSession?>

    @Query("SELECT * FROM charge_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<ChargeSession>>

    @Query("SELECT * FROM charge_samples WHERE sessionId = :sessionId ORDER BY recordedAt ASC")
    fun observeSamples(sessionId: Long): Flow<List<ChargeSample>>

    @Query("SELECT * FROM charge_samples WHERE sessionId = :sessionId ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatestSample(sessionId: Long): ChargeSample?

    @Query("SELECT * FROM charge_samples WHERE sessionId = :sessionId ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getLatestSamples(sessionId: Long, limit: Int): List<ChargeSample>

    @Query("DELETE FROM charge_samples WHERE id IN (:ids)")
    suspend fun deleteSamples(ids: List<Long>)

    @Query("SELECT * FROM charge_samples ORDER BY recordedAt DESC LIMIT :limit")
    fun observeRecentSamples(limit: Int = 240): Flow<List<ChargeSample>>

    @Query("SELECT * FROM temperature_samples WHERE recordedAt >= :since ORDER BY recordedAt ASC")
    fun observeTemperatureSince(since: Long): Flow<List<TemperatureSample>>

    @Query("SELECT * FROM app_power_samples WHERE recordedAt >= :since ORDER BY recordedAt ASC")
    fun observeAppPowerSamplesSince(since: Long): Flow<List<AppPowerSample>>

    @Query("DELETE FROM temperature_samples WHERE recordedAt < :before")
    suspend fun deleteTemperatureBefore(before: Long)

    @Query("DELETE FROM app_power_samples WHERE recordedAt < :before")
    suspend fun deleteAppPowerBefore(before: Long)

    @Query(
        "SELECT packageName, AVG(powerW) AS averagePowerW, COUNT(*) AS sampleCount " +
            "FROM app_power_samples WHERE recordedAt >= :since AND isCharging = 0 " +
            "GROUP BY packageName",
    )
    fun observeAppPowerAverages(since: Long): Flow<List<AppPowerAverage>>
}
