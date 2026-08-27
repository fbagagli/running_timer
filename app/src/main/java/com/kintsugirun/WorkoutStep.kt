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
    if (warmupSeconds > 0) {
        steps.add(WorkoutStep("Warmup", warmupSeconds, "Warm up"))
    }

    // 2. Repetitions of Run + Walk
    for (i in 0 until repeats) {
        val repInfo = if (repeats > 1) "${i + 1} of $repeats" else null
        if (runSeconds > 0) {
            steps.add(WorkoutStep("Run", runSeconds, "Run", repInfo))
        }
        if (walkSeconds > 0) {
            steps.add(WorkoutStep("Walk", walkSeconds, "Walk", repInfo))
        }
    }

    // 3. Cooldown
    if (cooldownSeconds > 0) {
        steps.add(WorkoutStep("Cooldown", cooldownSeconds, "Cool down"))
    }

    return steps
}
