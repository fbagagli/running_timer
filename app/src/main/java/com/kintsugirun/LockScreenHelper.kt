package com.kintsugirun

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.WindowManager

object LockScreenHelper {

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    fun setLockScreenVisibility(
        activity: Activity,
        show: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT
    ) {
        if (sdkInt >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(show)
            activity.setTurnScreenOn(show)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (show) {
                activity.window?.addFlags(flags)
            } else {
                activity.window?.clearFlags(flags)
            }
        }
    }
}
