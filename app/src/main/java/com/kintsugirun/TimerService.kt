package com.kintsugirun

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

class TimerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false

    private var workoutName: String = "Workout"
    private var workoutId: Long = 0L
    private var announceRepetitions: Boolean = false
    private var lastPhaseName: String = ""

    private val timerManager: TimerManager by inject()
    private val gpsTracker: GpsTracker by inject()

    private val actionHandler by lazy {
        TimerServiceActionHandler(
            timerManager = timerManager,
            onStop = {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        startGpsTrackingIfPermitted()

        // Observe the timer state to auto-stop the service, manage wake lock, and update notification
        timerManager.timerState.onEach { state ->
            if (state.isFinished) {
                gpsTracker.stopTracking()
                ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                if (!state.isPlaying) {
                    releaseWakeLock()
                    gpsTracker.pauseTracking()
                } else {
                    acquireWakeLock()
                    gpsTracker.resumeTracking()
                }

                if (state.currentPhaseName.isNotBlank() && state.currentPhaseName != lastPhaseName) {
                    lastPhaseName = state.currentPhaseName
                    gpsTracker.updatePhase(state.currentPhaseName)
                }

                if (isForegroundStarted) {
                    val notification = TimerNotificationHelper.buildNotification(
                        context = this@TimerService,
                        workoutName = workoutName,
                        workoutId = workoutId,
                        announceRepetitions = announceRepetitions,
                        state = state
                    )
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(TimerNotificationHelper.NOTIFICATION_ID, notification)
                }
            }
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (actionHandler.handleAction(intent?.action)) {
            return START_NOT_STICKY
        }

        val newWorkoutName = intent?.getStringExtra(TimerNotificationHelper.EXTRA_WORKOUT_NAME)
            ?: intent?.getStringExtra("WORKOUT_NAME")
        if (!newWorkoutName.isNullOrBlank()) {
            workoutName = newWorkoutName
        }
        workoutId = intent?.getLongExtra(TimerNotificationHelper.EXTRA_WORKOUT_ID, workoutId) ?: workoutId
        announceRepetitions = intent?.getBooleanExtra(
            TimerNotificationHelper.EXTRA_ANNOUNCE_REPETITIONS,
            announceRepetitions
        ) ?: announceRepetitions

        startGpsTrackingIfPermitted()

        acquireWakeLock()

        val notification = TimerNotificationHelper.buildNotification(
            context = this,
            workoutName = workoutName,
            workoutId = workoutId,
            announceRepetitions = announceRepetitions,
            state = timerManager.timerState.value
        )

        if (!isForegroundStarted) {
            if (Build.VERSION.SDK_INT >= 34) {
                val serviceType = if (hasLocationPermission()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(
                    TimerNotificationHelper.NOTIFICATION_ID,
                    notification,
                    serviceType
                )
            } else {
                startForeground(TimerNotificationHelper.NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(TimerNotificationHelper.NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        gpsTracker.stopTracking()
        scope.cancel()
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startGpsTrackingIfPermitted() {
        if (hasLocationPermission() && !gpsTracker.isTracking()) {
            gpsTracker.startTracking(workoutName)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KintsugiRun::TimerWakeLock"
            )
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                TimerNotificationHelper.CHANNEL_ID,
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
