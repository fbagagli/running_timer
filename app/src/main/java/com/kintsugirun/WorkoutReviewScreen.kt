package com.kintsugirun

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReviewScreen(
    fileName: String,
    viewModel: WorkoutReviewViewModel,
    onStartWorkout: () -> Unit,
    onNavigateBack: () -> Unit,
    onEditWorkout: () -> Unit
) {
    val workout by viewModel.workout.collectAsState()

    LaunchedEffect(fileName) {
        viewModel.loadWorkout(fileName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.workoutName ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEditWorkout) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Workout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        workout?.let { currentWorkout ->
            val totalDuration = currentWorkout.warmupSeconds +
                    currentWorkout.repeats * (currentWorkout.runSeconds + currentWorkout.walkSeconds) +
                    currentWorkout.cooldownSeconds

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Total Duration: ${formatTime(totalDuration)}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (currentWorkout.warmupSeconds > 0) {
                        WorkoutPhaseCard(
                            phaseName = "Warmup",
                            durationSeconds = currentWorkout.warmupSeconds,
                            details = "Gentle preparation pace"
                        )
                    }

                    WorkoutPhaseCard(
                        phaseName = "Intervals (${currentWorkout.repeats} Repetitions)",
                        durationSeconds = currentWorkout.repeats * (currentWorkout.runSeconds + currentWorkout.walkSeconds),
                        details = "• Run: ${formatTime(currentWorkout.runSeconds)}\n• Walk: ${formatTime(currentWorkout.walkSeconds)}"
                    )

                    if (currentWorkout.cooldownSeconds > 0) {
                        WorkoutPhaseCard(
                            phaseName = "Cooldown",
                            durationSeconds = currentWorkout.cooldownSeconds,
                            details = "Recovery walking & stretching"
                        )
                    }
                }

                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("Start Workout", style = MaterialTheme.typography.titleLarge)
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun WorkoutPhaseCard(phaseName: String, durationSeconds: Int, details: String = "") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = phaseName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatTime(durationSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
