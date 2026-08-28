package com.kintsugirun

data class WorkoutStep(
    val phaseName: String,
    val durationSeconds: Int,
    val message: String,
    val repetitionInfo: String? = null
)

fun Workout.flatten(announceRepetitions: Boolean = false): List<WorkoutStep> {
    val steps = mutableListOf<WorkoutStep>()

    // 1. Warmup
    if (warmupSeconds > 0) {
        steps.add(WorkoutStep("Warmup", warmupSeconds, "Start warm up"))
    }

    // 2. Repetitions of Run + Walk
    for (i in 0 until repeats) {
        val repInfo = if (repeats > 1) "${i + 1} of $repeats" else null
        val runMessage = if (announceRepetitions && repInfo != null) {
            "Start running, $repInfo"
        } else {
            "Start running"
        }

        if (runSeconds > 0) {
            steps.add(WorkoutStep("Run", runSeconds, runMessage, repInfo))
        }
        if (walkSeconds > 0) {
            steps.add(WorkoutStep("Walk", walkSeconds, "Start walking", repInfo))
        }
    }

    // 3. Cooldown
    if (cooldownSeconds > 0) {
        steps.add(WorkoutStep("Cooldown", cooldownSeconds, "Start cool down"))
    }

    return steps
}
