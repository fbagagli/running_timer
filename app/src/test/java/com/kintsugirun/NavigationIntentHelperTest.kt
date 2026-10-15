package com.kintsugirun

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class NavigationIntentHelperTest {

    @Test
    fun `extractActiveWorkoutRoute returns route from intent extras when present`() {
        val intent = mockk<Intent>()
        every { intent.getLongExtra(TimerNotificationHelper.EXTRA_WORKOUT_ID, 0L) } returns 42L
        every { intent.getBooleanExtra(TimerNotificationHelper.EXTRA_ANNOUNCE_REPETITIONS, false) } returns true

        val route = NavigationIntentHelper.extractActiveWorkoutRoute(intent, TimerState())

        assertThat(route).isNotNull()
        assertThat(route?.workoutId).isEqualTo(42L)
        assertThat(route?.announceRepetitions).isTrue()
    }

    @Test
    fun `extractActiveWorkoutRoute returns route from running timerState when intent has no workout id`() {
        val intent = mockk<Intent>()
        every { intent.getLongExtra(TimerNotificationHelper.EXTRA_WORKOUT_ID, 0L) } returns 0L
        every { intent.getBooleanExtra(TimerNotificationHelper.EXTRA_ANNOUNCE_REPETITIONS, false) } returns false

        val activeState = TimerState(
            isPlaying = true,
            workoutId = 15L,
            announceRepetitions = true,
            totalElapsedSeconds = 30
        )

        val route = NavigationIntentHelper.extractActiveWorkoutRoute(intent, activeState)

        assertThat(route).isNotNull()
        assertThat(route?.workoutId).isEqualTo(15L)
        assertThat(route?.announceRepetitions).isTrue()
    }

    @Test
    fun `extractActiveWorkoutRoute returns route from paused timerState with elapsed time`() {
        val pausedState = TimerState(
            isPlaying = false,
            workoutId = 99L,
            announceRepetitions = false,
            totalElapsedSeconds = 120
        )

        val route = NavigationIntentHelper.extractActiveWorkoutRoute(null, pausedState)

        assertThat(route).isNotNull()
        assertThat(route?.workoutId).isEqualTo(99L)
        assertThat(route?.announceRepetitions).isFalse()
    }

    @Test
    fun `extractActiveWorkoutRoute returns null when neither intent nor timerState indicate active workout`() {
        val idleState = TimerState(
            isPlaying = false,
            workoutId = 0L,
            totalElapsedSeconds = 0
        )

        val routeFromNull = NavigationIntentHelper.extractActiveWorkoutRoute(null, idleState)
        assertThat(routeFromNull).isNull()

        val intent = mockk<Intent>()
        every { intent.getLongExtra(TimerNotificationHelper.EXTRA_WORKOUT_ID, 0L) } returns 0L
        every { intent.getBooleanExtra(TimerNotificationHelper.EXTRA_ANNOUNCE_REPETITIONS, false) } returns false

        val routeFromZeroIntent = NavigationIntentHelper.extractActiveWorkoutRoute(intent, idleState)
        assertThat(routeFromZeroIntent).isNull()
    }
}
