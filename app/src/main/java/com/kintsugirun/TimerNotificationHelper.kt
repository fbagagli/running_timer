package com.kintsugirun

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Locale

object TimerNotificationHelper {

    const val CHANNEL_ID = "TimerServiceChannel"
    const val NOTIFICATION_ID = 1
    const val NOTIFICATION_VISIBILITY = NotificationCompat.VISIBILITY_PUBLIC

    const val ACTION_PAUSE = "com.kintsugirun.action.PAUSE"
    const val ACTION_RESUME = "com.kintsugirun.action.RESUME"
    const val ACTION_NEXT = "com.kintsugirun.action.NEXT"
    const val ACTION_STOP = "com.kintsugirun.action.STOP"

    const val EXTRA_WORKOUT_NAME = "WORKOUT_NAME"
    const val EXTRA_WORKOUT_ID = "WORKOUT_ID"
    const val EXTRA_ANNOUNCE_REPETITIONS = "ANNOUNCE_REPETITIONS"

    const val REQUEST_CODE_CONTENT = 100
    const val REQUEST_CODE_PAUSE = 101
    const val REQUEST_CODE_RESUME = 102
    const val REQUEST_CODE_NEXT = 103
    const val REQUEST_CODE_STOP = 104

    fun formatTime(seconds: Int): String {
        val positiveSeconds = seconds.coerceAtLeast(0)
        val mins = positiveSeconds / 60
        val secs = positiveSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    fun formatNotificationTitle(workoutName: String, state: TimerState): String {
        val safeName = workoutName.ifBlank { "Workout" }
        return when {
            state.isFinished -> "$safeName — Completed"
            !state.isPlaying && state.totalElapsedSeconds > 0 -> "$safeName (Paused)"
            else -> safeName
        }
    }

    fun formatNotificationText(state: TimerState, gpsMetrics: GpsMetrics? = null): String {
        if (state.isFinished) {
            return "Workout complete"
        }

        val timeStr = formatTime(state.remainingSecondsInPhase)
        val phaseLabel = when {
            state.currentPhaseName.isNotBlank() && state.repetitionInfo != null ->
                "${state.currentPhaseName} (${state.repetitionInfo})"
            state.currentPhaseName.isNotBlank() ->
                state.currentPhaseName
            else ->
                "Timer"
        }

        val baseText = if (!state.isPlaying && state.totalElapsedSeconds > 0) {
            "PAUSED • $phaseLabel • $timeStr"
        } else {
            "$phaseLabel • $timeStr"
        }

        return if (gpsMetrics != null && gpsMetrics.totalDistanceMeters > 0) {
            val distanceStr = String.format(Locale.US, "%.2f km", gpsMetrics.totalDistanceMeters / 1000.0)
            "$baseText • $distanceStr"
        } else {
            baseText
        }
    }

    fun createServiceIntent(context: Context, action: String): Intent {
        return Intent(context, TimerService::class.java).apply {
            this.action = action
        }
    }

    fun createAppIntent(context: Context, workoutId: Long, announceRepetitions: Boolean): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORKOUT_ID, workoutId)
            putExtra(EXTRA_ANNOUNCE_REPETITIONS, announceRepetitions)
        }
    }

    fun buildNotification(
        context: Context,
        workoutName: String,
        workoutId: Long,
        announceRepetitions: Boolean,
        state: TimerState,
        gpsMetrics: GpsMetrics? = null
    ): Notification {
        val contentIntent = createAppIntent(context, workoutId, announceRepetitions)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(formatNotificationTitle(workoutName, state))
            .setContentText(formatNotificationText(state, gpsMetrics))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        if (state.isPlaying) {
            val pauseIntent = createServiceIntent(context, ACTION_PAUSE)
            val pausePendingIntent = PendingIntent.getService(
                context,
                REQUEST_CODE_PAUSE,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else if (!state.isFinished && state.totalElapsedSeconds > 0) {
            val resumeIntent = createServiceIntent(context, ACTION_RESUME)
            val resumePendingIntent = PendingIntent.getService(
                context,
                REQUEST_CODE_RESUME,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        }

        if (!state.isFinished && (state.isPlaying || state.totalElapsedSeconds > 0)) {
            val nextIntent = createServiceIntent(context, ACTION_NEXT)
            val nextPendingIntent = PendingIntent.getService(
                context,
                REQUEST_CODE_NEXT,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
        }

        val stopIntent = createServiceIntent(context, ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        return builder.build()
    }
}
