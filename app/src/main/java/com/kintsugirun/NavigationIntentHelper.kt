package com.kintsugirun

import android.content.Intent

object NavigationIntentHelper {

    fun extractActiveWorkoutRoute(intent: Intent?, timerState: TimerState): ActiveWorkoutRoute? {
        val workoutId = intent?.getLongExtra(TimerNotificationHelper.EXTRA_WORKOUT_ID, 0L) ?: 0L
        val announceReps = intent?.getBooleanExtra(
            TimerNotificationHelper.EXTRA_ANNOUNCE_REPETITIONS,
            false
        ) ?: false

        if (workoutId > 0) {
            return ActiveWorkoutRoute(workoutId = workoutId, announceRepetitions = announceReps)
        }

        if ((timerState.isPlaying || timerState.totalElapsedSeconds > 0) && timerState.workoutId > 0) {
            return ActiveWorkoutRoute(
                workoutId = timerState.workoutId,
                announceRepetitions = timerState.announceRepetitions
            )
        }

        return null
    }
}
