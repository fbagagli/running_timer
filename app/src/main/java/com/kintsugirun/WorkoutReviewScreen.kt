package com.kintsugirun

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        if (workout == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val totalDuration = workout!!.flatten().sumOf { it.durationSeconds }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Total Duration: ${formatTime(totalDuration)}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Workout Summary:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    item {
                        WorkoutPhaseCard(
                            phaseName = "Warmup",
                            durationSeconds = workout!!.warmup.durationSeconds,
                            details = workout!!.warmup.message
                        )
                    }

                    items(workout!!.sets.withIndex().toList()) { (index, set) ->
                        WorkoutPhaseCard(
                            phaseName = "Set ${index + 1}: ${set.setName}",
                            durationSeconds = set.repeats * set.intervals.sumOf { it.durationSeconds },
                            details = "${set.repeats} repeats of:\n" + set.intervals.joinToString("\n") {
                                "  • ${it.type} (${formatTime(it.durationSeconds)})"
                            }
                        )
                    }

                    item {
                        WorkoutPhaseCard(
                            phaseName = "Cooldown",
                            durationSeconds = workout!!.cooldown.durationSeconds,
                            details = workout!!.cooldown.message
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("Start Workout", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
fun WorkoutPhaseCard(phaseName: String, durationSeconds: Int, details: String = "") {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
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
