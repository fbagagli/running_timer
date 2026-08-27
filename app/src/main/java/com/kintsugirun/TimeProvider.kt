package com.kintsugirun

import android.os.SystemClock

interface TimeProvider {
    fun elapsedRealtime(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
