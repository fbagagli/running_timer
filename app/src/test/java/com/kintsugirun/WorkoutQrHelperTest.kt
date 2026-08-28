package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutQrHelperTest {

    @Test
    fun `encodeWorkoutToUri produces valid kintsugirun URI with query parameters`() {
        val workout = Workout(
            id = 12L,
            workoutName = "5K Intervals",
            warmupSeconds = 300,
            runSeconds = 120,
            walkSeconds = 60,
            repeats = 5,
            cooldownSeconds = 300
        )

        val uri = WorkoutQrHelper.encodeWorkoutToUri(workout)

        assertThat(uri).startsWith("kintsugirun://workout?")
        assertThat(uri).contains("name=5K+Intervals")
        assertThat(uri).contains("w=300")
        assertThat(uri).contains("r=120")
        assertThat(uri).contains("k=60")
        assertThat(uri).contains("n=5")
        assertThat(uri).contains("c=300")
    }

    @Test
    fun `parseWorkoutFromUri correctly parses valid QR URI into Workout object`() {
        val rawUri = "kintsugirun://workout?name=Tempo+Run&w=180&r=240&k=90&n=4&c=180"

        val workout = WorkoutQrHelper.parseWorkoutFromUri(rawUri)

        assertThat(workout).isNotNull()
        assertThat(workout?.workoutName).isEqualTo("Tempo Run")
        assertThat(workout?.warmupSeconds).isEqualTo(180)
        assertThat(workout?.runSeconds).isEqualTo(240)
        assertThat(workout?.walkSeconds).isEqualTo(90)
        assertThat(workout?.repeats).isEqualTo(4)
        assertThat(workout?.cooldownSeconds).isEqualTo(180)
        assertThat(workout?.id).isEqualTo(0L) // Id should be 0 for new database insert
    }

    @Test
    fun `parseWorkoutFromUri with invalid scheme returns null`() {
        val invalidUri = "https://example.com/workout?name=Test&w=60&r=60&k=60&n=1&c=60"

        val workout = WorkoutQrHelper.parseWorkoutFromUri(invalidUri)

        assertThat(workout).isNull()
    }

    @Test
    fun `parseWorkoutFromUri with missing values uses safe fallbacks`() {
        val minimalUri = "kintsugirun://workout?name=Quick+Run"

        val workout = WorkoutQrHelper.parseWorkoutFromUri(minimalUri)

        assertThat(workout).isNotNull()
        assertThat(workout?.workoutName).isEqualTo("Quick Run")
        assertThat(workout?.warmupSeconds).isEqualTo(0)
        assertThat(workout?.runSeconds).isEqualTo(60)
        assertThat(workout?.walkSeconds).isEqualTo(60)
        assertThat(workout?.repeats).isEqualTo(1)
        assertThat(workout?.cooldownSeconds).isEqualTo(0)
    }

    @Test
    fun `parseWorkoutFromUri with malformed string returns null`() {
        val malformed = "not-a-valid-uri"

        val workout = WorkoutQrHelper.parseWorkoutFromUri(malformed)

        assertThat(workout).isNull()
    }
}
