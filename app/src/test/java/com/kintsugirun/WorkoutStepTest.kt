package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutStepTest {

    @Test
    fun `flatten with single repeat set produces steps with null repetitionInfo`() {
        val workout = Workout(
            workoutName = "Single Rep Run",
            warmup = Warmup(durationSeconds = 300, message = "Warm up"),
            sets = listOf(
                Set(
                    setName = "Main Set",
                    repeats = 1,
                    intervals = listOf(
                        Interval(type = "Run", durationSeconds = 120, message = "Fast run"),
                        Interval(type = "Walk", durationSeconds = 60, message = "Walk recovery")
                    )
                )
            ),
            cooldown = Cooldown(durationSeconds = 180, message = "Cool down")
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
        assertThat(steps[1].message).isEqualTo("Fast run")
        assertThat(steps[1].repetitionInfo).isNull()

        assertThat(steps[2].phaseName).isEqualTo("Walk")
        assertThat(steps[2].durationSeconds).isEqualTo(60)
        assertThat(steps[2].message).isEqualTo("Walk recovery")
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
            warmup = Warmup(durationSeconds = 120, message = "Start warmup"),
            sets = listOf(
                Set(
                    setName = "Intervals",
                    repeats = 3,
                    intervals = listOf(
                        Interval(type = "Sprint", durationSeconds = 30, message = "Sprint!"),
                        Interval(type = "Recovery", durationSeconds = 90, message = "Recover")
                    )
                )
            ),
            cooldown = Cooldown(durationSeconds = 120, message = "Cooldown")
        )

        val steps = workout.flatten()

        // 1 warmup + 3 * 2 intervals + 1 cooldown = 8 steps
        assertThat(steps).hasSize(8)

        // Warmup
        assertThat(steps[0].repetitionInfo).isNull()

        // Repetition 1
        assertThat(steps[1].phaseName).isEqualTo("Sprint")
        assertThat(steps[1].repetitionInfo).isEqualTo("1 of 3")
        assertThat(steps[2].phaseName).isEqualTo("Recovery")
        assertThat(steps[2].repetitionInfo).isEqualTo("1 of 3")

        // Repetition 2
        assertThat(steps[3].phaseName).isEqualTo("Sprint")
        assertThat(steps[3].repetitionInfo).isEqualTo("2 of 3")
        assertThat(steps[4].phaseName).isEqualTo("Recovery")
        assertThat(steps[4].repetitionInfo).isEqualTo("2 of 3")

        // Repetition 3
        assertThat(steps[5].phaseName).isEqualTo("Sprint")
        assertThat(steps[5].repetitionInfo).isEqualTo("3 of 3")
        assertThat(steps[6].phaseName).isEqualTo("Recovery")
        assertThat(steps[6].repetitionInfo).isEqualTo("3 of 3")

        // Cooldown
        assertThat(steps[7].repetitionInfo).isNull()
    }

    @Test
    fun `flatten with multiple sets handles mixed repetition counts correctly`() {
        val workout = Workout(
            workoutName = "Multi Set Workout",
            warmup = Warmup(durationSeconds = 60, message = "Warmup"),
            sets = listOf(
                Set(
                    setName = "Set 1",
                    repeats = 2,
                    intervals = listOf(
                        Interval(type = "Jog", durationSeconds = 60, message = "Jog")
                    )
                ),
                Set(
                    setName = "Set 2",
                    repeats = 1,
                    intervals = listOf(
                        Interval(type = "Walk", durationSeconds = 30, message = "Walk")
                    )
                )
            ),
            cooldown = Cooldown(durationSeconds = 60, message = "Cooldown")
        )

        val steps = workout.flatten()

        // 1 warmup + 2 (Set 1) + 1 (Set 2) + 1 cooldown = 5 steps
        assertThat(steps).hasSize(5)

        // Set 1 (repeats = 2)
        assertThat(steps[1].phaseName).isEqualTo("Jog")
        assertThat(steps[1].repetitionInfo).isEqualTo("1 of 2")
        assertThat(steps[2].phaseName).isEqualTo("Jog")
        assertThat(steps[2].repetitionInfo).isEqualTo("2 of 2")

        // Set 2 (repeats = 1)
        assertThat(steps[3].phaseName).isEqualTo("Walk")
        assertThat(steps[3].repetitionInfo).isNull()
    }

    @Test
    fun `flatten with empty sets only returns warmup and cooldown`() {
        val workout = Workout(
            workoutName = "Empty Sets",
            warmup = Warmup(durationSeconds = 60, message = "Warmup"),
            sets = emptyList(),
            cooldown = Cooldown(durationSeconds = 60, message = "Cooldown")
        )

        val steps = workout.flatten()

        assertThat(steps).hasSize(2)
        assertThat(steps[0].phaseName).isEqualTo("Warmup")
        assertThat(steps[1].phaseName).isEqualTo("Cooldown")
    }
}
