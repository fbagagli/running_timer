package com.kintsugirun

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class TimerState(
    val currentPhaseName: String = "",
    val remainingSecondsInPhase: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val isPlaying: Boolean = false,
    val isFinished: Boolean = false,
    val repetitionInfo: String? = null,
    val workoutId: Long = 0L,
    val announceRepetitions: Boolean = false
)

class TimerManager(
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var voicePromptManager: VoicePromptManager? = null
    private var currentWorkoutSteps: List<WorkoutStep> = emptyList()
    private var currentStepIndex = 0
    private var timerJob: Job? = null

    private var targetEndTimeMs: Long = 0
    private var totalElapsedMsAtPhaseStart: Long = 0
    private var pauseTimeMs: Long = 0

    fun setVoicePromptManager(voicePromptManager: VoicePromptManager?) {
        this.voicePromptManager = voicePromptManager
    }

    fun initTts(ttsManager: TtsManager) {
        this.voicePromptManager = ttsManager
    }

    fun start(workout: Workout, announceRepetitions: Boolean = false) {
        currentWorkoutSteps = workout.flatten(announceRepetitions)
        currentStepIndex = 0
        totalElapsedMsAtPhaseStart = 0

        if (currentWorkoutSteps.isEmpty()) return

        val firstStep = currentWorkoutSteps[currentStepIndex]

        targetEndTimeMs = timeProvider.elapsedRealtime() + (firstStep.durationSeconds * 1000L)

        _timerState.value = TimerState(
            currentPhaseName = firstStep.phaseName,
            remainingSecondsInPhase = firstStep.durationSeconds,
            totalElapsedSeconds = 0,
            isPlaying = true,
            isFinished = false,
            repetitionInfo = firstStep.repetitionInfo,
            workoutId = workout.id,
            announceRepetitions = announceRepetitions
        )

        voicePromptManager?.speak(firstStep.message)
        startTimer()
    }

    fun pause() {
        if (_timerState.value.isPlaying) {
            timerJob?.cancel()
            pauseTimeMs = timeProvider.elapsedRealtime()
            _timerState.value = _timerState.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        if (!_timerState.value.isPlaying && !_timerState.value.isFinished && currentWorkoutSteps.isNotEmpty()) {
            // Adjust target end time based on how long it was paused
            val pausedDurationMs = timeProvider.elapsedRealtime() - pauseTimeMs
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

    fun nextStep() {
        if (currentStepIndex < currentWorkoutSteps.size - 1) {
            moveToStep(currentStepIndex + 1)
        }
    }

    fun previousStep() {
        if (currentStepIndex > 0) {
            moveToStep(currentStepIndex - 1)
        }
    }

    private fun moveToStep(index: Int) {
        // Calculate new totalElapsedMsAtPhaseStart
        totalElapsedMsAtPhaseStart = 0
        for (i in 0 until index) {
            totalElapsedMsAtPhaseStart += currentWorkoutSteps[i].durationSeconds * 1000L
        }

        currentStepIndex = index
        val nextStep = currentWorkoutSteps[currentStepIndex]
        targetEndTimeMs = timeProvider.elapsedRealtime() + (nextStep.durationSeconds * 1000L)

        _timerState.value = _timerState.value.copy(
            currentPhaseName = nextStep.phaseName,
            remainingSecondsInPhase = nextStep.durationSeconds,
            totalElapsedSeconds = (totalElapsedMsAtPhaseStart / 1000).toInt(),
            repetitionInfo = nextStep.repetitionInfo,
            isFinished = false
        )
        voicePromptManager?.speak(nextStep.message)

        // If it was paused, we stay paused but at the new step
        if (!_timerState.value.isPlaying) {
            pauseTimeMs = timeProvider.elapsedRealtime()
        }
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

    internal fun tick() {
        val currentTimeMs = timeProvider.elapsedRealtime()

        while (currentTimeMs >= targetEndTimeMs && _timerState.value.isPlaying) {
            val finishedStep = currentWorkoutSteps[currentStepIndex]
            totalElapsedMsAtPhaseStart += finishedStep.durationSeconds * 1000L

            currentStepIndex++

            if (currentStepIndex < currentWorkoutSteps.size) {
                val nextStep = currentWorkoutSteps[currentStepIndex]
                targetEndTimeMs += (nextStep.durationSeconds * 1000L)

                _timerState.value = _timerState.value.copy(
                    currentPhaseName = nextStep.phaseName,
                    remainingSecondsInPhase = nextStep.durationSeconds,
                    totalElapsedSeconds = (totalElapsedMsAtPhaseStart / 1000).toInt(),
                    repetitionInfo = nextStep.repetitionInfo
                )
                voicePromptManager?.speak(nextStep.message)
            } else {
                // Workout finished
                _timerState.value = _timerState.value.copy(
                    remainingSecondsInPhase = 0,
                    totalElapsedSeconds = (totalElapsedMsAtPhaseStart / 1000).toInt(),
                    isPlaying = false,
                    isFinished = true
                )
                voicePromptManager?.speak("Workout complete")
                timerJob?.cancel()
                return
            }
        }

        if (_timerState.value.isPlaying && currentStepIndex < currentWorkoutSteps.size) {
            val remainingMs = max(0L, targetEndTimeMs - currentTimeMs)
            val remainingSeconds = ((remainingMs + 999) / 1000).toInt()

            val currentPhaseDurationMs = currentWorkoutSteps[currentStepIndex].durationSeconds * 1000L
            val elapsedInPhaseMs = currentPhaseDurationMs - remainingMs
            val totalElapsedSeconds = ((totalElapsedMsAtPhaseStart + elapsedInPhaseMs) / 1000).toInt()

            val currentState = _timerState.value
            if (currentState.remainingSecondsInPhase != remainingSeconds ||
                currentState.totalElapsedSeconds != totalElapsedSeconds) {
                _timerState.value = currentState.copy(
                    remainingSecondsInPhase = remainingSeconds,
                    totalElapsedSeconds = totalElapsedSeconds
                )
            }
        }
    }
}
