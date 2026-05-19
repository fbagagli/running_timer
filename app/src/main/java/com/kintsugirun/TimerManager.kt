package com.kintsugirun

import kotlinx.coroutines.*
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class TimerState(
    val currentPhaseName: String = "",
    val remainingSecondsInPhase: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val isPlaying: Boolean = false,
    val isFinished: Boolean = false
)

object TimerManager {

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var ttsManager: TtsManager? = null
    private var currentWorkoutSteps: List<WorkoutStep> = emptyList()
    private var currentStepIndex = 0
    private var timerJob: Job? = null

    private var targetEndTimeMs: Long = 0
    private var totalElapsedMsAtPhaseStart: Long = 0
    private var pauseTimeMs: Long = 0

    // Coroutine scope for the timer. We use a custom scope so it's not tied to any lifecycle.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initTts(ttsManager: TtsManager) {
        this.ttsManager = ttsManager
    }

    fun start(workout: Workout) {
        currentWorkoutSteps = workout.flatten()
        currentStepIndex = 0
        totalElapsedMsAtPhaseStart = 0

        if (currentWorkoutSteps.isEmpty()) return

        val firstStep = currentWorkoutSteps[currentStepIndex]

        targetEndTimeMs = SystemClock.elapsedRealtime() + (firstStep.durationSeconds * 1000L)

        _timerState.value = TimerState(
            currentPhaseName = firstStep.phaseName,
            remainingSecondsInPhase = firstStep.durationSeconds,
            totalElapsedSeconds = 0,
            isPlaying = true,
            isFinished = false
        )

        ttsManager?.speak(firstStep.message)
        startTimer()
    }

    fun pause() {
        if (_timerState.value.isPlaying) {
            timerJob?.cancel()
            pauseTimeMs = SystemClock.elapsedRealtime()
            _timerState.value = _timerState.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        if (!_timerState.value.isPlaying && !_timerState.value.isFinished && currentWorkoutSteps.isNotEmpty()) {
            // Adjust target end time based on how long it was paused
            val pausedDurationMs = SystemClock.elapsedRealtime() - pauseTimeMs
            targetEndTimeMs += pausedDurationMs

            _timerState.value = _timerState.value.copy(isPlaying = true)
            startTimer()
        }
    }

    fun stop() {
        timerJob?.cancel()
        _timerState.value = TimerState() // Reset to default
        currentWorkoutSteps = emptyList()
        currentStepIndex = 0
        targetEndTimeMs = 0
        totalElapsedMsAtPhaseStart = 0
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _timerState.value.isPlaying) {
                tick()
                delay(100) // Poll frequently to ensure UI updates are smooth and transition is precise
            }
        }
    }

    private fun tick() {
        val currentTimeMs = SystemClock.elapsedRealtime()
        val currentState = _timerState.value

        if (currentTimeMs >= targetEndTimeMs) {
            // Step finished, move to next step
            val finishedStep = currentWorkoutSteps[currentStepIndex]
            totalElapsedMsAtPhaseStart += finishedStep.durationSeconds * 1000L

            currentStepIndex++

            if (currentStepIndex < currentWorkoutSteps.size) {
                val nextStep = currentWorkoutSteps[currentStepIndex]

                // Calculate next target time. If we overshot slightly, we subtract the overshoot
                // by just adding duration to previous target.
                targetEndTimeMs += (nextStep.durationSeconds * 1000L)

                _timerState.value = currentState.copy(
                    currentPhaseName = nextStep.phaseName,
                    remainingSecondsInPhase = nextStep.durationSeconds,
                    totalElapsedSeconds = (totalElapsedMsAtPhaseStart / 1000).toInt()
                )
                ttsManager?.speak(nextStep.message)
            } else {
                // Workout finished
                _timerState.value = currentState.copy(
                    remainingSecondsInPhase = 0,
                    totalElapsedSeconds = (totalElapsedMsAtPhaseStart / 1000).toInt(),
                    isPlaying = false,
                    isFinished = true
                )
                ttsManager?.speak("Workout complete. Great job!")
                timerJob?.cancel()
            }
        } else {
            // Still in current step
            val remainingMs = targetEndTimeMs - currentTimeMs
            val remainingSeconds = (remainingMs / 1000).toInt() + 1 // Add 1 so it hits 0 exactly when transitioning

            val currentPhaseDurationMs = currentWorkoutSteps[currentStepIndex].durationSeconds * 1000L
            val elapsedInPhaseMs = currentPhaseDurationMs - remainingMs
            val totalElapsedSeconds = ((totalElapsedMsAtPhaseStart + elapsedInPhaseMs) / 1000).toInt()

            // Only update flow if value changed (avoids unnecessary recompositions)
            if (currentState.remainingSecondsInPhase != remainingSeconds ||
                currentState.totalElapsedSeconds != totalElapsedSeconds) {
                _timerState.value = currentState.copy(
                    remainingSecondsInPhase = max(0, remainingSeconds),
                    totalElapsedSeconds = totalElapsedSeconds
                )
            }
        }
    }
}
