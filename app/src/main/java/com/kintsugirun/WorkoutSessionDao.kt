package com.kintsugirun

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<WorkoutSession>)

    @Query("SELECT * FROM workout_sessions WHERE workoutId = :workoutId ORDER BY startedAtTimestamp DESC LIMIT 1")
    fun getLatestSessionForWorkout(workoutId: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAtTimestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()
}
