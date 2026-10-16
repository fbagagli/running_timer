package com.kintsugirun

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 executes create table for workout_sessions`() {
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)

        AppDatabase.MIGRATION_1_2.migrate(mockDb)

        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `workout_sessions`") })
        }
    }

    @Test
    fun `migration 2 to 3 executes alter table statements for gps and metrics columns`() {
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)

        AppDatabase.MIGRATION_2_3.migrate(mockDb)

        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE workout_sessions ADD COLUMN totalDistanceMeters REAL NOT NULL DEFAULT 0.0") })
        }
        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE workout_sessions ADD COLUMN runDistanceMeters REAL NOT NULL DEFAULT 0.0") })
        }
        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE workout_sessions ADD COLUMN walkDistanceMeters REAL NOT NULL DEFAULT 0.0") })
        }
        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE workout_sessions ADD COLUMN runPaceSecondsPerKm INTEGER NOT NULL DEFAULT 0") })
        }
        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE workout_sessions ADD COLUMN walkPaceSecondsPerKm INTEGER NOT NULL DEFAULT 0") })
        }
        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE workout_sessions ADD COLUMN gpsRouteJson TEXT DEFAULT NULL") })
        }
    }
}
