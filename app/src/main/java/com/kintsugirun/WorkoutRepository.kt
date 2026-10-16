package com.kintsugirun

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val workoutSessionDao: WorkoutSessionDao,
    val autoBackupManager: AutoBackupManager? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val tag = "WorkoutRepository"

    val allWorkouts: Flow<List<Workout>> = workoutDao.getAllWorkouts()

    suspend fun getWorkoutById(id: Long): Workout? = withContext(ioDispatcher) {
        workoutDao.getWorkoutById(id)
    }

    suspend fun saveWorkout(workout: Workout): Long = withContext(ioDispatcher) {
        val id = workoutDao.insertOrUpdate(workout)
        coroutineScope.launch { triggerAutoBackup() }
        id
    }

    suspend fun deleteWorkout(id: Long) = withContext(ioDispatcher) {
        workoutDao.deleteById(id)
        coroutineScope.launch { triggerAutoBackup() }
    }

    suspend fun saveSession(session: WorkoutSession): Long = withContext(ioDispatcher) {
        val id = workoutSessionDao.insertSession(session)
        coroutineScope.launch { triggerAutoBackup() }
        id
    }

    suspend fun saveSessions(sessions: List<WorkoutSession>) = withContext(ioDispatcher) {
        workoutSessionDao.insertAll(sessions)
    }

    fun getLatestSessionForWorkout(workoutId: Long): Flow<WorkoutSession?> =
        workoutSessionDao.getLatestSessionForWorkout(workoutId)

    fun getAllSessions(): Flow<List<WorkoutSession>> =
        workoutSessionDao.getAllSessions()

    suspend fun getAllWorkoutsList(): List<Workout> = withContext(ioDispatcher) {
        workoutDao.getAllWorkouts().first()
    }

    suspend fun getAllSessionsList(): List<WorkoutSession> = withContext(ioDispatcher) {
        workoutSessionDao.getAllSessions().first()
    }

    suspend fun deleteAllSessions() = withContext(ioDispatcher) {
        workoutSessionDao.deleteAllSessions()
    }

    suspend fun triggerAutoBackup() {
        if (autoBackupManager?.isAutoBackupEnabled() == true) {
            try {
                val workouts = getAllWorkoutsList()
                val sessions = getAllSessionsList()
                val json = BackupHelper.exportBackup(workouts, sessions)
                autoBackupManager.writeBackup(json)
            } catch (e: Exception) {
                Log.e(tag, "Failed to perform auto-backup", e)
            }
        }
    }

    suspend fun seedDefaultWorkoutsIfEmpty() = withContext(ioDispatcher) {
        if (workoutDao.getWorkoutCount() == 0) {
            workoutDao.insertOrUpdate(
                Workout(
                    workoutName = "Couch to 5K — Week 1",
                    warmupSeconds = 300,
                    runSeconds = 60,
                    walkSeconds = 90,
                    repeats = 8,
                    cooldownSeconds = 300
                )
            )
            workoutDao.insertOrUpdate(
                Workout(
                    workoutName = "Easy Intervals",
                    warmupSeconds = 300,
                    runSeconds = 120,
                    walkSeconds = 60,
                    repeats = 5,
                    cooldownSeconds = 300
                )
            )
            Log.d(tag, "Seeded default starter workouts")
        }
    }
}
