package com.kintsugirun

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TimerService : Service() {

    private val CHANNEL_ID = "TimerServiceChannel"
    private val NOTIFICATION_ID = 1
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Observe the timer state to auto-stop the service when the workout finishes
        TimerManager.timerState.onEach { state ->
            if (state.isFinished) {
                stopSelf()
            }
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workoutName = intent?.getStringExtra("WORKOUT_NAME") ?: "Workout"

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KintsugiRun")
            .setContentText(workoutName)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using a default icon for now
            .setOngoing(true)
            .build()

        // Start foreground service
        if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            // Android 14 (API 34) requires foregroundServiceType for FOREGROUND_SERVICE_SPECIAL_USE
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We are not binding to this service, using TimerManager singleton
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
