package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class TimerServiceActionHandlerTest {

    private lateinit var timerManager: TimerManager
    private var stopCalled = false
    private lateinit var actionHandler: TimerServiceActionHandler

    @Before
    fun setup() {
        timerManager = mockk(relaxed = true)
        stopCalled = false
        actionHandler = TimerServiceActionHandler(
            timerManager = timerManager,
            onStop = { stopCalled = true }
        )
    }

    @Test
    fun `handleAction with ACTION_PAUSE invokes timerManager pause`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_PAUSE)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.pause() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_RESUME invokes timerManager resume`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_RESUME)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.resume() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_NEXT invokes timerManager nextStep`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_NEXT)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.nextStep() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_STOP invokes timerManager stop and onStop callback`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_STOP)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.stop() }
        assertThat(stopCalled).isTrue()
    }

    @Test
    fun `handleAction with unknown action or null returns false without invoking actions`() {
        val nullResult = actionHandler.handleAction(null)
        val unknownResult = actionHandler.handleAction("com.unknown.ACTION")

        assertThat(nullResult).isFalse()
        assertThat(unknownResult).isFalse()
        verify(exactly = 0) { timerManager.pause() }
        verify(exactly = 0) { timerManager.resume() }
        verify(exactly = 0) { timerManager.nextStep() }
        verify(exactly = 0) { timerManager.stop() }
        assertThat(stopCalled).isFalse()
    }
}
