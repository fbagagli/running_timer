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

    @Test
    fun `exportBackup and parseBackup roundtrip preserves session gps metrics and route json`() {
        val sessionWithGps = sampleSession.copy(
            id = 20L,
            totalDistanceMeters = 3542.5,
            runDistanceMeters = 2450.0,
            walkDistanceMeters = 1092.5,
            runPaceSecondsPerKm = 315,
            walkPaceSecondsPerKm = 640,
            gpsRouteJson = """[{"latitude":45.123,"longitude":9.456,"timestamp":1700000010000,"phaseName":"Run"}]"""
        )

        val json = BackupHelper.exportBackup(listOf(sampleWorkout1), listOf(sessionWithGps))
        val restored = BackupHelper.parseBackup(json)

        assertThat(restored).isNotNull()
        val restoredSession = restored!!.sessions.single()
        assertThat(restoredSession.totalDistanceMeters).isEqualTo(3542.5)
        assertThat(restoredSession.runDistanceMeters).isEqualTo(2450.0)
        assertThat(restoredSession.walkDistanceMeters).isEqualTo(1092.5)
        assertThat(restoredSession.runPaceSecondsPerKm).isEqualTo(315)
        assertThat(restoredSession.walkPaceSecondsPerKm).isEqualTo(640)
        assertThat(restoredSession.gpsRouteJson).isEqualTo("""[{"latitude":45.123,"longitude":9.456,"timestamp":1700000010000,"phaseName":"Run"}]""")
    }

    @Test
    fun `parseBackup handles legacy backup json without gps metrics using defaults`() {
        val legacyJson = """
            {
                "version": 1,
                "exportedAtTimestamp": 1700000000000,
                "workouts": [],
                "sessions": [
                    {
                        "id": 10,
                        "workoutId": 1,
                        "startedAtTimestamp": 1700000000000,
                        "totalElapsedSeconds": 1740,
                        "isCompleted": true,
                        "interruptedPhase": null,
                        "workoutSnapshot": {
                            "id": 1,
                            "workoutName": "Morning 5K",
                            "warmupSeconds": 300,
                            "runSeconds": 180,
                            "walkSeconds": 60,
                            "repeats": 6,
                            "cooldownSeconds": 300
                        }
                    }
                ]
            }
        """.trimIndent()

        val restored = BackupHelper.parseBackup(legacyJson)
        assertThat(restored).isNotNull()
        val session = restored!!.sessions.single()
        assertThat(session.id).isEqualTo(10L)
        assertThat(session.totalDistanceMeters).isEqualTo(0.0)
        assertThat(session.runDistanceMeters).isEqualTo(0.0)
        assertThat(session.walkDistanceMeters).isEqualTo(0.0)
        assertThat(session.runPaceSecondsPerKm).isEqualTo(0)
        assertThat(session.walkPaceSecondsPerKm).isEqualTo(0)
        assertThat(session.gpsRouteJson).isNull()
    }
}
