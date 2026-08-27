package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutStepTest {

    @Test
    fun `flatten with single repeat produces steps with null repetitionInfo`() {
        val workout = Workout(
            workoutName = "Single Rep Run",
            warmupSeconds = 300,
            runSeconds = 120,
            walkSeconds = 60,
            repeats = 1,
            cooldownSeconds = 180
        )

        val steps = workout.flatten()

        assertThat(steps).hasSize(4)

        // Warmup
        assertThat(steps[0].phaseName).isEqualTo("Warmup")
        assertThat(steps[0].durationSeconds).isEqualTo(300)
        assertThat(steps[0].message).isEqualTo("Warm up")
        assertThat(steps[0].repetitionInfo).isNull()

        // Intervals (repeats = 1 -> repetitionInfo should be null)
        assertThat(steps[1].phaseName).isEqualTo("Run")
        assertThat(steps[1].durationSeconds).isEqualTo(120)
        assertThat(steps[1].message).isEqualTo("Run")
        assertThat(steps[1].repetitionInfo).isNull()

        assertThat(steps[2].phaseName).isEqualTo("Walk")
        assertThat(steps[2].durationSeconds).isEqualTo(60)
        assertThat(steps[2].message).isEqualTo("Walk")
        assertThat(steps[2].repetitionInfo).isNull()

        // Cooldown
        assertThat(steps[3].phaseName).isEqualTo("Cooldown")
        assertThat(steps[3].durationSeconds).isEqualTo(180)
        assertThat(steps[3].message).isEqualTo("Cool down")
        assertThat(steps[3].repetitionInfo).isNull()
    }

    @Test
    fun `flatten with multiple repeats generates correct repetitionInfo`() {
        val workout = Workout(
            workoutName = "Interval Repeats",
            warmupSeconds = 120,
            runSeconds = 30,
            walkSeconds = 90,
            repeats = 3,
            cooldownSeconds = 120
        )

        val steps = workout.flatten()

        // 1 warmup + 3 * (1 run + 1 walk) + 1 cooldown = 8 steps
        assertThat(steps).hasSize(8)

        // Warmup
        assertThat(steps[0].repetitionInfo).isNull()

        // Repetition 1
        assertThat(steps[1].phaseName).isEqualTo("Run")
        assertThat(steps[1].repetitionInfo).isEqualTo("1 of 3")
        assertThat(steps[2].phaseName).isEqualTo("Walk")
        assertThat(steps[2].repetitionInfo).isEqualTo("1 of 3")

        // Repetition 2
        assertThat(steps[3].phaseName).isEqualTo("Run")
        assertThat(steps[3].repetitionInfo).isEqualTo("2 of 3")
        assertThat(steps[4].phaseName).isEqualTo("Walk")
        assertThat(steps[4].repetitionInfo).isEqualTo("2 of 3")

        // Repetition 3
        assertThat(steps[5].phaseName).isEqualTo("Run")
        assertThat(steps[5].repetitionInfo).isEqualTo("3 of 3")
        assertThat(steps[6].phaseName).isEqualTo("Walk")
        assertThat(steps[6].repetitionInfo).isEqualTo("3 of 3")

        // Cooldown
        assertThat(steps[7].repetitionInfo).isNull()
    }

    @Test
    fun `flatten with zero warmup and cooldown only produces intervals`() {
        val workout = Workout(
            workoutName = "Only Intervals",
            warmupSeconds = 0,
            runSeconds = 60,
            walkSeconds = 30,
            repeats = 2,
            cooldownSeconds = 0
        )

        val steps = workout.flatten()

        // 2 * (1 run + 1 walk) = 4 steps
        assertThat(steps).hasSize(4)
        assertThat(steps[0].phaseName).isEqualTo("Run")
        assertThat(steps[0].repetitionInfo).isEqualTo("1 of 2")
        assertThat(steps[1].phaseName).isEqualTo("Walk")
        assertThat(steps[1].repetitionInfo).isEqualTo("1 of 2")
        assertThat(steps[2].phaseName).isEqualTo("Run")
        assertThat(steps[2].repetitionInfo).isEqualTo("2 of 2")
        assertThat(steps[3].phaseName).isEqualTo("Walk")
        assertThat(steps[3].repetitionInfo).isEqualTo("2 of 2")
    }
}
