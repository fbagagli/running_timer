package com.kintsugirun

import androidx.core.app.NotificationCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimerNotificationHelperTest {

    @Test
    fun `formatTime formats seconds into mm ss correctly`() {
        assertThat(TimerNotificationHelper.formatTime(0)).isEqualTo("00:00")
        assertThat(TimerNotificationHelper.formatTime(9)).isEqualTo("00:09")
        assertThat(TimerNotificationHelper.formatTime(59)).isEqualTo("00:59")
        assertThat(TimerNotificationHelper.formatTime(60)).isEqualTo("01:00")
        assertThat(TimerNotificationHelper.formatTime(125)).isEqualTo("02:05")
        assertThat(TimerNotificationHelper.formatTime(3600)).isEqualTo("60:00")
        assertThat(TimerNotificationHelper.formatTime(-10)).isEqualTo("00:00")
    }

    @Test
    fun `formatNotificationTitle handles active paused finished and empty workout names`() {
        val runningState = TimerState(
            currentPhaseName = "Run",
            remainingSecondsInPhase = 60,
            totalElapsedSeconds = 120,
            isPlaying = true
        )
        assertThat(TimerNotificationHelper.formatNotificationTitle("Morning Run", runningState))
            .isEqualTo("Morning Run")

        val pausedState = TimerState(
            currentPhaseName = "Run",
            remainingSecondsInPhase = 60,
            totalElapsedSeconds = 120,
            isPlaying = false
        )
        assertThat(TimerNotificationHelper.formatNotificationTitle("Morning Run", pausedState))
            .isEqualTo("Morning Run (Paused)")

        val finishedState = TimerState(
            currentPhaseName = "",
            remainingSecondsInPhase = 0,
            totalElapsedSeconds = 300,
            isPlaying = false,
            isFinished = true
        )
        assertThat(TimerNotificationHelper.formatNotificationTitle("Morning Run", finishedState))
            .isEqualTo("Morning Run — Completed")

        val emptyNameState = TimerState(isPlaying = true)
        assertThat(TimerNotificationHelper.formatNotificationTitle("", emptyNameState))
            .isEqualTo("Workout")
    }

    @Test
    fun `formatNotificationText formats active phases with and without repetition info`() {
        val warmupState = TimerState(
            currentPhaseName = "Warmup",
            remainingSecondsInPhase = 300,
            totalElapsedSeconds = 0,
            isPlaying = true,
            repetitionInfo = null
        )
        assertThat(TimerNotificationHelper.formatNotificationText(warmupState))
            .isEqualTo("Warmup • 05:00")

        val runState = TimerState(
            currentPhaseName = "Run",
            remainingSecondsInPhase = 120,
            totalElapsedSeconds = 300,
            isPlaying = true,
            repetitionInfo = "2 of 5"
        )
        assertThat(TimerNotificationHelper.formatNotificationText(runState))
            .isEqualTo("Run (2 of 5) • 02:00")
    }

    @Test
    fun `formatNotificationText formats paused state with PAUSED prefix`() {
        val pausedWalkState = TimerState(
            currentPhaseName = "Walk",
            remainingSecondsInPhase = 45,
            totalElapsedSeconds = 420,
            isPlaying = false,
            repetitionInfo = "3 of 5"
        )
        assertThat(TimerNotificationHelper.formatNotificationText(pausedWalkState))
            .isEqualTo("PAUSED • Walk (3 of 5) • 00:45")
    }

    @Test
    fun `formatNotificationText formats finished workout`() {
        val finishedState = TimerState(
            currentPhaseName = "",
            remainingSecondsInPhase = 0,
            totalElapsedSeconds = 600,
            isPlaying = false,
            isFinished = true
        )
        assertThat(TimerNotificationHelper.formatNotificationText(finishedState))
            .isEqualTo("Workout complete")
    }

    @Test
    fun `action constants are unique and follow reverse-dns pattern`() {
        val actions = listOf(
            TimerNotificationHelper.ACTION_PAUSE,
            TimerNotificationHelper.ACTION_RESUME,
            TimerNotificationHelper.ACTION_NEXT,
            TimerNotificationHelper.ACTION_STOP
        )
        assertThat(actions.distinct().size).isEqualTo(actions.size)
        actions.forEach { action ->
            assertThat(action).startsWith("com.kintsugirun.action.")
        }
    }

    @Test
    fun `notification visibility is configured as public for lock screen`() {
        assertThat(TimerNotificationHelper.NOTIFICATION_VISIBILITY)
            .isEqualTo(NotificationCompat.VISIBILITY_PUBLIC)
    }
}
