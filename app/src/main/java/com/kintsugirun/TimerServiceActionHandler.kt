package com.kintsugirun

class TimerServiceActionHandler(
    private val timerManager: TimerManager,
    private val onStop: () -> Unit
) {
    fun handleAction(action: String?): Boolean {
        return when (action) {
            TimerNotificationHelper.ACTION_PAUSE -> {
                timerManager.pause()
                true
            }
            TimerNotificationHelper.ACTION_RESUME -> {
                timerManager.resume()
                true
            }
            TimerNotificationHelper.ACTION_NEXT -> {
                timerManager.nextStep()
                true
            }
            TimerNotificationHelper.ACTION_STOP -> {
                timerManager.stop()
                onStop()
                true
            }
            else -> false
        }
    }
}
