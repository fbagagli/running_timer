package com.kintsugirun

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    // Coroutine scope for the timer. We use a custom scope so it's not tied to any lifecycle.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initTts(ttsManager: TtsManager) {
        this.ttsManager = ttsManager
    }

    fun start(workout: Workout) {
        currentWorkoutSteps = workout.flatten()
        currentStepIndex = 0

        if (currentWorkoutSteps.isEmpty()) return

        val firstStep = currentWorkoutSteps[currentStepIndex]
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
            _timerState.value = _timerState.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        if (!_timerState.value.isPlaying && !_timerState.value.isFinished && currentWorkoutSteps.isNotEmpty()) {
            _timerState.value = _timerState.value.copy(isPlaying = true)
            startTimer()
        }
    }

    fun stop() {
        timerJob?.cancel()
        _timerState.value = TimerState() // Reset to default
        currentWorkoutSteps = emptyList()
        currentStepIndex = 0
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _timerState.value.isPlaying) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        val currentState = _timerState.value

        if (currentState.remainingSecondsInPhase > 1) {
            // Still in current step
            _timerState.value = currentState.copy(
                remainingSecondsInPhase = currentState.remainingSecondsInPhase - 1,
                totalElapsedSeconds = currentState.totalElapsedSeconds + 1
            )
        } else {
            // Step finished, move to next step
            currentStepIndex++
            val totalElapsed = currentState.totalElapsedSeconds + 1

            if (currentStepIndex < currentWorkoutSteps.size) {
                val nextStep = currentWorkoutSteps[currentStepIndex]
                _timerState.value = currentState.copy(
                    currentPhaseName = nextStep.phaseName,
                    remainingSecondsInPhase = nextStep.durationSeconds,
                    totalElapsedSeconds = totalElapsed
                )
                ttsManager?.speak(nextStep.message)
            } else {
                // Workout finished
                _timerState.value = currentState.copy(
                    remainingSecondsInPhase = 0,
                    totalElapsedSeconds = totalElapsed,
                    isPlaying = false,
                    isFinished = true
                )
                ttsManager?.speak("Workout complete. Great job!")
                timerJob?.cancel()
            }
        }
    }
}
