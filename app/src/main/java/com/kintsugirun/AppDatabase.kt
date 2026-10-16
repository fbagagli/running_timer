package com.kintsugirun

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Workout::class, WorkoutSession::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workoutId` INTEGER NOT NULL,
                        `startedAtTimestamp` INTEGER NOT NULL,
                        `totalElapsedSeconds` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `interruptedPhase` TEXT,
                        `workout_id` INTEGER NOT NULL,
                        `workout_workoutName` TEXT NOT NULL,
                        `workout_warmupSeconds` INTEGER NOT NULL,
                        `workout_runSeconds` INTEGER NOT NULL,
                        `workout_walkSeconds` INTEGER NOT NULL,
                        `workout_repeats` INTEGER NOT NULL,
                        `workout_cooldownSeconds` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN totalDistanceMeters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN runDistanceMeters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN walkDistanceMeters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN runPaceSecondsPerKm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN walkPaceSecondsPerKm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN gpsRouteJson TEXT DEFAULT NULL")
            }
        }
    }
}
