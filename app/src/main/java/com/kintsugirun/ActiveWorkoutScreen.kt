package com.kintsugirun

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    fileName: String,
    viewModel: ActiveWorkoutViewModel,
    onNavigateBack: () -> Unit
) {
    val workout by viewModel.workout.collectAsState()
    val timerState by viewModel.timerState.collectAsState()

    // Instead of local state, check if the timer is already playing or has elapsed time
    val hasStarted = timerState.isPlaying || timerState.totalElapsedSeconds > 0

    LaunchedEffect(fileName) {
        viewModel.loadWorkout(fileName)
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
                            viewModel.startTimer()
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

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = formatTime(timerState.remainingSecondsInPhase),
                        fontSize = 80.sp,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.displayLarge
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
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
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
