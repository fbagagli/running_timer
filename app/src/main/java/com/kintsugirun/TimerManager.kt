package com.kintsugirun

import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val repository: WorkoutRepository? = null,
    private val gpsTracker: GpsTracker? = null
) {

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var voicePromptManager: VoicePromptManager? = null
    private var currentWorkoutSteps: List<WorkoutStep> = emptyList()
    private var currentStepIndex = 0
    private var timerJob: Job? = null
    private var halfwayAnnounced = false
    private var oneMinuteAnnounced = false

    private var targetEndTimeMs: Long = 0
    private var totalElapsedMsAtPhaseStart: Long = 0
    private var pauseTimeMs: Long = 0

    internal var currentWorkout: Workout? = null
    internal var workoutStartTimeMs: Long = 0L
    internal var sessionSaved: Boolean = false

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
        halfwayAnnounced = false
        oneMinuteAnnounced = false

        if (currentWorkoutSteps.isEmpty()) return

        currentWorkout = workout
        workoutStartTimeMs = System.currentTimeMillis()
        sessionSaved = false

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

        val workout = currentWorkout
        val state = _timerState.value
        if (!sessionSaved && workout != null && state.totalElapsedSeconds >= 30) {
            sessionSaved = true
            val phase = if (state.repetitionInfo != null) {
                "${state.currentPhaseName} (${state.repetitionInfo})"
            } else {
                state.currentPhaseName
            }
            val startTime = if (workoutStartTimeMs != 0L) {
                workoutStartTimeMs
            } else {
                System.currentTimeMillis() - (state.totalElapsedSeconds * 1000L)
            }
            val session = createSession(
                workout = workout,
                startTime = startTime,
                totalElapsedSeconds = state.totalElapsedSeconds,
                isCompleted = false,
                interruptedPhase = phase
            )
            scope.launch {
                repository?.saveSession(session)
            }
        }

        _timerState.value = TimerState() // Reset to default
        currentWorkoutSteps = emptyList()
        currentStepIndex = 0
        targetEndTimeMs = 0
        totalElapsedMsAtPhaseStart = 0
        sessionSaved = false
        currentWorkout = null
        workoutStartTimeMs = 0L
        halfwayAnnounced = false
        oneMinuteAnnounced = false
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
        halfwayAnnounced = false
        oneMinuteAnnounced = false
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
            halfwayAnnounced = false
            oneMinuteAnnounced = false

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
                val finalElapsedSeconds = (totalElapsedMsAtPhaseStart / 1000).toInt()
                _timerState.value = _timerState.value.copy(
                    remainingSecondsInPhase = 0,
                    totalElapsedSeconds = finalElapsedSeconds,
                    isPlaying = false,
                    isFinished = true
                )
                voicePromptManager?.speak("Workout complete")
                timerJob?.cancel()

                val workout = currentWorkout
                if (!sessionSaved && workout != null) {
                    sessionSaved = true
                    val startTime = if (workoutStartTimeMs != 0L) {
                        workoutStartTimeMs
                    } else {
                        System.currentTimeMillis() - (finalElapsedSeconds * 1000L)
                    }
                    val session = createSession(
                        workout = workout,
                        startTime = startTime,
                        totalElapsedSeconds = finalElapsedSeconds,
                        isCompleted = true,
                        interruptedPhase = null
                    )
                    scope.launch {
                        repository?.saveSession(session)
                    }
                }
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

            val currentStep = currentWorkoutSteps[currentStepIndex]
            if (currentStep.phaseName.equals("Run", ignoreCase = true)) {
                if (currentStep.durationSeconds > 120 && !halfwayAnnounced && remainingSeconds <= currentStep.durationSeconds / 2) {
                    halfwayAnnounced = true
                    val paceSec = gpsTracker?.metrics?.value?.currentIntervalPaceSecPerKm ?: 0
                    if (paceSec in 60..1200) {
                        val mins = paceSec / 60
                        val secs = paceSec % 60
                        val paceFormatted = String.format(Locale.US, "%d:%02d", mins, secs)
                        voicePromptManager?.speak("Halfway, pace $paceFormatted")
                    } else {
                        voicePromptManager?.speak("Halfway")
                    }
                }
                if (currentStep.durationSeconds >= 180 && !oneMinuteAnnounced && remainingSeconds <= 60) {
                    oneMinuteAnnounced = true
                    voicePromptManager?.speak("One minute left")
                }
            }
        }
    }

    internal fun calculateRunAndWalkSeconds(): Pair<Int, Int> {
        var runSec = 0
        var walkSec = 0
        val maxIndex = minOf(currentStepIndex, currentWorkoutSteps.size)
        for (i in 0 until maxIndex) {
            val step = currentWorkoutSteps[i]
            if (step.phaseName.equals("Run", ignoreCase = true)) {
                runSec += step.durationSeconds
            } else if (step.phaseName.equals("Walk", ignoreCase = true)) {
                walkSec += step.durationSeconds
            }
        }
        if (currentStepIndex < currentWorkoutSteps.size) {
            val currentStep = currentWorkoutSteps[currentStepIndex]
            val remainingSec = _timerState.value.remainingSecondsInPhase
            val elapsedInPhaseSec = max(0, currentStep.durationSeconds - remainingSec)
            if (currentStep.phaseName.equals("Run", ignoreCase = true)) {
                runSec += elapsedInPhaseSec
            } else if (currentStep.phaseName.equals("Walk", ignoreCase = true)) {
                walkSec += elapsedInPhaseSec
            }
        }
        return Pair(runSec, walkSec)
    }

    internal fun createSession(
        workout: Workout,
        startTime: Long,
        totalElapsedSeconds: Int,
        isCompleted: Boolean,
        interruptedPhase: String?
    ): WorkoutSession {
        val (runSec, walkSec) = calculateRunAndWalkSeconds()
        val gps = gpsTracker?.metrics?.value
        val totalDist = gps?.totalDistanceMeters ?: 0.0
        val runDist = gps?.runDistanceMeters ?: 0.0
        val walkDist = gps?.walkDistanceMeters ?: 0.0
        val runPace = if (runDist > 10.0 && runSec > 0) {
            (runSec / (runDist / 1000.0)).toInt()
        } else {
            0
        }
        val walkPace = if (walkDist > 10.0 && walkSec > 0) {
            (walkSec / (walkDist / 1000.0)).toInt()
        } else {
            0
        }
        val points = gps?.points.orEmpty()
        val routeJson = if (points.isNotEmpty()) {
            Json.encodeToString(points)
        } else {
            null
        }

        return WorkoutSession(
            workoutId = workout.id,
            startedAtTimestamp = startTime,
            totalElapsedSeconds = totalElapsedSeconds,
            isCompleted = isCompleted,
            interruptedPhase = interruptedPhase,
            workoutSnapshot = workout,
            totalDistanceMeters = totalDist,
            runDistanceMeters = runDist,
            walkDistanceMeters = walkDist,
            runPaceSecondsPerKm = runPace,
            walkPaceSecondsPerKm = walkPace,
            gpsRouteJson = routeJson
        )
    }
}
