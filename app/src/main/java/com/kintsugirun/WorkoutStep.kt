package com.kintsugirun

data class WorkoutStep(
    val phaseName: String,
    val durationSeconds: Int,
    val message: String,
    val repetitionInfo: String? = null
)

fun Workout.flatten(): List<WorkoutStep> {
    val steps = mutableListOf<WorkoutStep>()

    // 1. Warmup
    steps.add(WorkoutStep("Warmup", warmup.durationSeconds, warmup.message))

    // 2. Sets
    sets.forEach { set ->
        repeat(set.repeats) { repeatIndex ->
            set.intervals.forEach { interval ->
                val repInfo = if (set.repeats > 1) {
                    "${repeatIndex + 1} of ${set.repeats}"
                } else {
                    null
                }
                steps.add(WorkoutStep(interval.type, interval.durationSeconds, interval.message, repInfo))
            }
        }
    }

    // 3. Cooldown
    steps.add(WorkoutStep("Cooldown", cooldown.durationSeconds, cooldown.message))

    return steps
}
