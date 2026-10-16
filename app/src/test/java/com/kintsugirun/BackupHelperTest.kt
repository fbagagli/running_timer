package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupHelperTest {

    private val sampleWorkout1 = Workout(
        id = 1L,
        workoutName = "Morning 5K",
        warmupSeconds = 300,
        runSeconds = 180,
        walkSeconds = 60,
        repeats = 6,
        cooldownSeconds = 300
    )

    private val sampleWorkout2 = Workout(
        id = 2L,
        workoutName = "Sprint Intervals",
        warmupSeconds = 240,
        runSeconds = 60,
        walkSeconds = 90,
        repeats = 8,
        cooldownSeconds = 240
    )

    private val sampleSession = WorkoutSession(
        id = 10L,
        workoutId = 1L,
        startedAtTimestamp = 1700000000000L,
        totalElapsedSeconds = 1740,
        isCompleted = true,
        interruptedPhase = null,
        workoutSnapshot = sampleWorkout1
    )

    @Test
    fun `exportBackup and parseBackup roundtrip preserves workouts and sessions`() {
        val workouts = listOf(sampleWorkout1, sampleWorkout2)
        val sessions = listOf(sampleSession)

        val json = BackupHelper.exportBackup(workouts, sessions)

        assertThat(json).isNotEmpty()
        val restored = BackupHelper.parseBackup(json)
        assertThat(restored).isNotNull()
        assertThat(restored!!.version).isEqualTo(1)
        assertThat(restored.workouts).isEqualTo(workouts)
        assertThat(restored.sessions).isEqualTo(sessions)
    }

    @Test
    fun `exportBackup handles empty workouts and sessions lists`() {
        val json = BackupHelper.exportBackup(emptyList(), emptyList())

        assertThat(json).isNotEmpty()
        val restored = BackupHelper.parseBackup(json)
        assertThat(restored).isNotNull()
        assertThat(restored!!.workouts).isEmpty()
        assertThat(restored.sessions).isEmpty()
    }

    @Test
    fun `parseBackup returns null on invalid json syntax`() {
        val invalidJson = "{ invalid json content ... "
        val result = BackupHelper.parseBackup(invalidJson)
        assertThat(result).isNull()
    }

    @Test
    fun `parseBackup returns null on incompatible schema`() {
        val incompatibleJson = """{"version": "not-an-int", "workouts": "not-a-list"}"""
        val result = BackupHelper.parseBackup(incompatibleJson)
        assertThat(result).isNull()
    }

    @Test
    fun `parseBackup ignores unknown json keys`() {
        val jsonWithUnknownKeys = """
            {
                "version": 1,
                "exportedAtTimestamp": 1700000000000,
                "workouts": [],
                "sessions": [],
                "extraField": "unknown_value",
                "nestedExtra": {"foo": "bar"}
            }
        """.trimIndent()

        val result = BackupHelper.parseBackup(jsonWithUnknownKeys)
        assertThat(result).isNotNull()
        assertThat(result!!.workouts).isEmpty()
        assertThat(result.sessions).isEmpty()
    }
}
