package com.kintsugirun

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    viewModel: WorkoutEditorViewModel,
    navController: NavController
) {
    val workout by viewModel.workout.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (workout?.workoutName == "New Workout") "New Workout" else "Edit Workout"
                    Text(titleText)
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.saveWorkout()
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save Workout")
                    }
                }
            )
        }
    ) { paddingValues ->
        workout?.let { currentWorkout ->
            val totalSeconds = currentWorkout.warmupSeconds +
                    currentWorkout.repeats * (currentWorkout.runSeconds + currentWorkout.walkSeconds) +
                    currentWorkout.cooldownSeconds

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = currentWorkout.workoutName,
                    onValueChange = { viewModel.updateWorkoutName(it) },
                    label = { Text("Workout Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Structure & Intervals",
                            style = MaterialTheme.typography.titleMedium
                        )

                        DurationInputField(
                            label = "Warmup (seconds)",
                            value = currentWorkout.warmupSeconds,
                            onValueChange = { viewModel.updateWarmupSeconds(it) }
                        )

                        DurationInputField(
                            label = "Run Duration (seconds)",
                            value = currentWorkout.runSeconds,
                            onValueChange = { viewModel.updateRunSeconds(it) }
                        )

                        DurationInputField(
                            label = "Walk Duration (seconds)",
                            value = currentWorkout.walkSeconds,
                            onValueChange = { viewModel.updateWalkSeconds(it) }
                        )

                        // Repeats Stepper
                        Column {
                            Text(
                                text = "Repetitions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (currentWorkout.repeats > 1) {
                                            viewModel.updateRepeats(currentWorkout.repeats - 1)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease Repeats")
                                }

                                Text(
                                    text = "${currentWorkout.repeats} reps",
                                    style = MaterialTheme.typography.titleLarge
                                )

                                FilledTonalIconButton(
                                    onClick = {
                                        viewModel.updateRepeats(currentWorkout.repeats + 1)
                                    }
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase Repeats")
                                }
                            }
                        }

                        DurationInputField(
                            label = "Cooldown (seconds)",
                            value = currentWorkout.cooldownSeconds,
                            onValueChange = { viewModel.updateCooldownSeconds(it) }
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Total Time:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = formatDuration(totalSeconds),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DurationInputField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    OutlinedTextField(
        value = if (value > 0) value.toString() else "",
        onValueChange = {
            val parsed = it.toIntOrNull() ?: 0
            onValueChange(parsed)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
