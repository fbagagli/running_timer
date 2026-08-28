package com.kintsugirun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToWorkout: (Long) -> Unit,
    onCreateWorkout: () -> Unit
) {
    val workouts by viewModel.workouts.collectAsState()
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KintsugiRun Workouts") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateWorkout,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Create Workout") },
                text = { Text("Create Workout") }
            )
        }
    ) { paddingValues ->
        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No workouts found. Tap 'Create Workout' to get started.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutRow(
                        workout = workout,
                        onClick = { onNavigateToWorkout(workout.id) },
                        onDelete = { workoutToDelete = workout }
                    )
                }
            }
        }

        workoutToDelete?.let { workout ->
            AlertDialog(
                onDismissRequest = { workoutToDelete = null },
                title = { Text("Delete Workout") },
                text = { Text("Are you sure you want to delete \"${workout.workoutName}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteWorkout(workout.id)
                            workoutToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workoutToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun WorkoutRow(
    workout: Workout,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val totalSeconds = workout.warmupSeconds +
            workout.repeats * (workout.runSeconds + workout.walkSeconds) +
            workout.cooldownSeconds

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.workoutName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${workout.repeats} reps • Run ${formatTime(workout.runSeconds)} / Walk ${formatTime(workout.walkSeconds)} • ${formatTime(totalSeconds)} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete Workout",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
