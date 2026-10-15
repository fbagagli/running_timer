package com.kintsugirun

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class LockScreenHelperTest {

    @Test
    fun `setLockScreenVisibility on API 27 or higher sets activity showWhenLocked and turnScreenOn to true`() {
        val activity = mockk<Activity>(relaxed = true)

        LockScreenHelper.setLockScreenVisibility(
            activity = activity,
            show = true,
            sdkInt = Build.VERSION_CODES.O_MR1
        )

        verify(exactly = 1) { activity.setShowWhenLocked(true) }
        verify(exactly = 1) { activity.setTurnScreenOn(true) }
    }

    @Test
    fun `setLockScreenVisibility on API 27 or higher sets activity showWhenLocked and turnScreenOn to false`() {
        val activity = mockk<Activity>(relaxed = true)

        LockScreenHelper.setLockScreenVisibility(
            activity = activity,
            show = false,
            sdkInt = 34
        )

        verify(exactly = 1) { activity.setShowWhenLocked(false) }
        verify(exactly = 1) { activity.setTurnScreenOn(false) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `setLockScreenVisibility on API lower than 27 adds flags to window when show is true`() {
        val activity = mockk<Activity>()
        val window = mockk<Window>(relaxed = true)
        every { activity.window } returns window

        LockScreenHelper.setLockScreenVisibility(
            activity = activity,
            show = true,
            sdkInt = Build.VERSION_CODES.N
        )

        val expectedFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        verify(exactly = 1) { window.addFlags(expectedFlags) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `setLockScreenVisibility on API lower than 27 clears flags on window when show is false`() {
        val activity = mockk<Activity>()
        val window = mockk<Window>(relaxed = true)
        every { activity.window } returns window

        LockScreenHelper.setLockScreenVisibility(
            activity = activity,
            show = false,
            sdkInt = 24
        )

        val expectedFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        verify(exactly = 1) { window.clearFlags(expectedFlags) }
    }
}
