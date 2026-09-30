package com.local.chargemeter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChargeSession::class, ChargeSample::class, TemperatureSample::class, AppPowerSample::class],
    version = 10,
    exportSchema = false,
)
abstract class ChargeDatabase : RoomDatabase() {
    abstract fun chargeDao(): ChargeDao

    companion object {
        @Volatile private var instance: ChargeDatabase? = null

        fun get(context: Context): ChargeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChargeDatabase::class.java,
                "charge-meter.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            ).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN startChargeCounterMah REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN chargedMah REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN equivalentCycles REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN estimatedHealthPct REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN systemCycleCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN designCapacityMah INTEGER NOT NULL DEFAULT 6700")
                db.execSQL("ALTER TABLE charge_samples ADD COLUMN chargeCounterMah REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charge_samples ADD COLUMN systemCycleCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS temperature_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "recordedAt INTEGER NOT NULL, temperatureC REAL NOT NULL, " +
                        "powerW REAL NOT NULL, level INTEGER NOT NULL, isCharging INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_temperature_samples_recordedAt " +
                        "ON temperature_samples(recordedAt)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS app_power_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "packageName TEXT NOT NULL, recordedAt INTEGER NOT NULL, " +
                        "powerW REAL NOT NULL, isCharging INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_power_samples_packageName " +
                        "ON app_power_samples(packageName)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_power_samples_recordedAt " +
                        "ON app_power_samples(recordedAt)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN healthStartLevel INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE charge_sessions ADD COLUMN healthStartChargeCounterMah REAL NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE charge_sessions SET " +
                        "healthStartLevel = CASE WHEN startLevel >= 20 THEN startLevel ELSE -1 END, " +
                        "healthStartChargeCounterMah = CASE WHEN startLevel >= 20 THEN startChargeCounterMah ELSE 0 END, " +
                        "estimatedHealthPct = CASE WHEN startLevel >= 20 THEN estimatedHealthPct ELSE 0 END",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val latestCounter =
                    "COALESCE((SELECT chargeCounterMah FROM charge_samples " +
                        "WHERE sessionId = charge_sessions.id ORDER BY recordedAt DESC LIMIT 1), 0)"
                db.execSQL(
                    "UPDATE charge_sessions SET estimatedHealthPct = CASE " +
                        "WHEN endLevel - startLevel >= 20 AND startChargeCounterMah > 0 " +
                        "AND $latestCounter > startChargeCounterMah THEN " +
                        "MAX(50.0, MIN(105.0, " +
                        "(($latestCounter - startChargeCounterMah) * 10000.0) / " +
                        "((endLevel - startLevel) * designCapacityMah))) " +
                        "ELSE 0 END",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // A previous build incorrectly applied the dual-cell ×2 display
                // conversion to the system charge counter. Repair only sessions whose
                // stored starting charge is physically inconsistent with their SOC.
                val doubledSessions =
                    "SELECT id FROM charge_sessions WHERE startLevel > 0 " +
                        "AND startChargeCounterMah * 100.0 / startLevel > designCapacityMah * 1.30"
                db.execSQL(
                    "UPDATE charge_samples SET chargeCounterMah = chargeCounterMah / 2.0 " +
                        "WHERE sessionId IN ($doubledSessions)",
                )
                db.execSQL(
                    "UPDATE charge_sessions SET " +
                        "startChargeCounterMah = startChargeCounterMah / 2.0, " +
                        "chargedMah = chargedMah / 2.0, " +
                        "healthStartChargeCounterMah = healthStartChargeCounterMah / 2.0, " +
                        "estimatedHealthPct = CASE " +
                        "WHEN endLevel - startLevel >= 60 AND chargedMah > 0 THEN " +
                        "MAX(50.0, MIN(100.0, " +
                        "((chargedMah / 2.0) * 10000.0) / " +
                        "((endLevel - startLevel) * designCapacityMah))) " +
                        "ELSE 0 END " +
                        "WHERE id IN ($doubledSessions)",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1.0.1 adopts the AccuBattery 2.0 sample rule. Preserve
                // every session, while clearing health values for samples below 60%.
                db.execSQL(
                    "UPDATE charge_sessions SET estimatedHealthPct = CASE " +
                        "WHEN endLevel - startLevel >= 60 AND chargedMah > 0 THEN " +
                        "MAX(0.0, MIN(100.0, " +
                        "(chargedMah * 10000.0) / " +
                        "((endLevel - startLevel) * designCapacityMah))) " +
                        "ELSE 0 END",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove legacy negative samples that only appeared at the unplug
                // boundary, then align the session end with its last real sample.
                db.execSQL(
                    "DELETE FROM charge_samples WHERE powerW < 0 AND EXISTS (" +
                        "SELECT 1 FROM charge_sessions s WHERE s.id = charge_samples.sessionId " +
                        "AND s.endedAt IS NOT NULL AND charge_samples.recordedAt >= s.endedAt - 45000 " +
                        "AND charge_samples.recordedAt <= s.endedAt)",
                )
                db.execSQL(
                    "UPDATE charge_sessions SET " +
                        "endedAt = COALESCE((SELECT MAX(recordedAt) FROM charge_samples WHERE sessionId = charge_sessions.id), endedAt), " +
                        "endLevel = COALESCE((SELECT level FROM charge_samples WHERE sessionId = charge_sessions.id ORDER BY recordedAt DESC LIMIT 1), endLevel), " +
                        "sampleCount = (SELECT COUNT(*) FROM charge_samples WHERE sessionId = charge_sessions.id) " +
                        "WHERE endedAt IS NOT NULL",
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM charge_samples WHERE EXISTS (" +
                        "SELECT 1 FROM charge_sessions s WHERE s.id = charge_samples.sessionId " +
                        "AND s.endedAt IS NOT NULL AND charge_samples.recordedAt > COALESCE(" +
                        "(SELECT MAX(ok.recordedAt) FROM charge_samples ok " +
                        "WHERE ok.sessionId = charge_samples.sessionId AND ok.powerW >= 0), -1))",
                )
                db.execSQL(
                    "UPDATE charge_sessions SET " +
                        "endedAt = COALESCE((SELECT MAX(recordedAt) FROM charge_samples WHERE sessionId = charge_sessions.id), endedAt), " +
                        "endLevel = COALESCE((SELECT level FROM charge_samples WHERE sessionId = charge_sessions.id ORDER BY recordedAt DESC LIMIT 1), endLevel), " +
                        "sampleCount = (SELECT COUNT(*) FROM charge_samples WHERE sessionId = charge_sessions.id) " +
                        "WHERE endedAt IS NOT NULL",
                )
            }
        }
    }
}
