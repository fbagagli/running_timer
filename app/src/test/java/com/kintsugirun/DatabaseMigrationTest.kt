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
}
