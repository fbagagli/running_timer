package com.kintsugirun

data class WorkoutStep(
    val phaseName: String,
    val durationSeconds: Int,
    val message: String
)

fun Workout.flatten(): List<WorkoutStep> {
    val steps = mutableListOf<WorkoutStep>()

    // 1. Warmup
    steps.add(WorkoutStep("Warmup", warmup.durationSeconds, warmup.message))

    // 2. Sets
    sets.forEach { set ->
        repeat(set.repeats) {
            set.intervals.forEach { interval ->
                // The phaseName could be the interval type (e.g., Run, Walk) or something like "Set 1 - Run"
                // For simplicity, we use the interval type.
                steps.add(WorkoutStep(interval.type, interval.durationSeconds, interval.message))
            }
        }
    }

    // 3. Cooldown
    steps.add(WorkoutStep("Cooldown", cooldown.durationSeconds, cooldown.message))

    return steps
}

fun Workout.getTotalDurationSeconds(): Int {
    return this.flatten().sumOf { it.durationSeconds }
}
