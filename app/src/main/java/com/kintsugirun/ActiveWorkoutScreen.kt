package com.kintsugirun

import android.Manifest
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    announceRepetitions: Boolean = false,
    viewModel: ActiveWorkoutViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val workout by viewModel.workout.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val gpsMetrics by viewModel.gpsMetrics.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Instead of local state, check if the timer is already playing or has elapsed time
    val hasStarted = timerState.isPlaying || timerState.totalElapsedSeconds > 0

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    // Auto-navigate back when finished
    LaunchedEffect(timerState.isFinished) {
        if (timerState.isFinished) {
            viewModel.stopTimer()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.workoutName ?: "Loading...") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (workout == null) {
                CircularProgressIndicator()
            } else {
                if (!hasStarted) {
                    Text(
                        text = "Ready to start",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            viewModel.startTimer(announceRepetitions)
                        },
                        modifier = Modifier.size(120.dp, 60.dp)
                    ) {
                        Text("Start", fontSize = 24.sp)
                    }
                } else {
                    Text(
                        text = timerState.currentPhaseName,
                        style = MaterialTheme.typography.displaySmall
                    )

                    timerState.repetitionInfo?.let {
                        Text(
                            text = "Repetition $it",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = formatTime(timerState.remainingSecondsInPhase),
                        fontSize = 80.sp,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.displayLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val distanceKm = gpsMetrics.totalDistanceMeters / 1000.0
                            Text(
                                text = "${String.format(Locale.US, "%.2f", distanceKm)} km",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Distance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val paceSec = gpsMetrics.currentIntervalPaceSecPerKm
                            val paceText = if (paceSec > 0) {
                                val mins = paceSec / 60
                                val secs = paceSec % 60
                                "${mins}'${String.format(Locale.US, "%02d", secs)}\" /km"
                            } else {
                                "--'--\" /km"
                            }
                            Text(
                                text = paceText,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Interval Pace",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { viewModel.previousStep() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Step", modifier = Modifier.size(32.dp))
                        }

                        if (timerState.isPlaying) {
                            FloatingActionButton(
                                onClick = { viewModel.pauseTimer() },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pause", modifier = Modifier.size(48.dp))
                            }
                        } else {
                            FloatingActionButton(
                                onClick = { viewModel.resumeTimer() },
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(48.dp))
                            }
                        }

                        IconButton(
                            onClick = { viewModel.nextStep() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next Step", modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    FloatingActionButton(
                        onClick = {
                            viewModel.stopTimer()
                            onNavigateBack()
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
