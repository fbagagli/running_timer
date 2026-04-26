package com.kintsugirun

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    viewModel: WorkoutEditorViewModel,
    navController: NavController
) {
    val workout by viewModel.workout.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (workout?.workoutName == "New Workout") "New Workout" else "Edit Workout"
                    Text(titleText)
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveWorkout()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save Workout")
                    }
                }
            )
        }
    ) { paddingValues ->
        workout?.let { currentWorkout ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = currentWorkout.workoutName,
                        onValueChange = { viewModel.updateWorkoutName(it) },
                        label = { Text("Workout Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(text = "Warmup", style = MaterialTheme.typography.titleMedium)
                    WarmupEditor(
                        warmup = currentWorkout.warmup,
                        onUpdate = { duration, message -> viewModel.updateWarmup(duration, message) }
                    )
                }

                itemsIndexed(currentWorkout.sets) { setIndex, set ->
                    SetEditor(
                        set = set,
                        setIndex = setIndex,
                        onUpdateName = { name -> viewModel.updateSetName(setIndex, name) },
                        onUpdateRepeats = { repeats -> viewModel.updateSetRepeats(setIndex, repeats) },
                        onUpdateInterval = { intervalIndex, type, duration, message ->
                            viewModel.updateInterval(setIndex, intervalIndex, type, duration, message)
                        },
                        onAddInterval = { viewModel.addIntervalToSet(setIndex) }
                    )
                }

                item {
                    Button(
                        onClick = { viewModel.addSet() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Set")
                        Spacer(Modifier.width(8.dp))
                        Text("Add Set")
                    }
                }

                item {
                    Text(text = "Cooldown", style = MaterialTheme.typography.titleMedium)
                    CooldownEditor(
                        cooldown = currentWorkout.cooldown,
                        onUpdate = { duration, message -> viewModel.updateCooldown(duration, message) }
                    )
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun WarmupEditor(
    warmup: Warmup,
    onUpdate: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = if (warmup.durationSeconds > 0) warmup.durationSeconds.toString() else "",
            onValueChange = {
                val newDuration = it.toIntOrNull() ?: 0
                onUpdate(newDuration, warmup.message)
            },
            label = { Text("Duration (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = warmup.message,
            onValueChange = { onUpdate(warmup.durationSeconds, it) },
            label = { Text("TTS Message") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CooldownEditor(
    cooldown: Cooldown,
    onUpdate: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = if (cooldown.durationSeconds > 0) cooldown.durationSeconds.toString() else "",
            onValueChange = {
                val newDuration = it.toIntOrNull() ?: 0
                onUpdate(newDuration, cooldown.message)
            },
            label = { Text("Duration (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = cooldown.message,
            onValueChange = { onUpdate(cooldown.durationSeconds, it) },
            label = { Text("TTS Message") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SetEditor(
    set: Set,
    setIndex: Int,
    onUpdateName: (String) -> Unit,
    onUpdateRepeats: (Int) -> Unit,
    onUpdateInterval: (Int, String, Int, String) -> Unit,
    onAddInterval: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Set ${setIndex + 1}", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = set.setName,
                onValueChange = { onUpdateName(it) },
                label = { Text("Set Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = if (set.repeats > 0) set.repeats.toString() else "",
                onValueChange = {
                    val newRepeats = it.toIntOrNull() ?: 0
                    onUpdateRepeats(newRepeats)
                },
                label = { Text("Repeats") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = "Intervals", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

            set.intervals.forEachIndexed { intervalIndex, interval ->
                IntervalEditor(
                    interval = interval,
                    onUpdate = { type, duration, message ->
                        onUpdateInterval(intervalIndex, type, duration, message)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            TextButton(
                onClick = onAddInterval,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Interval")
                Spacer(Modifier.width(8.dp))
                Text("Add Interval")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalEditor(
    interval: Interval,
    onUpdate: (String, Int, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var expanded by remember { mutableStateOf(false) }
            val options = listOf("run", "walk")

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = interval.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onUpdate(option, interval.durationSeconds, interval.message)
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = if (interval.durationSeconds > 0) interval.durationSeconds.toString() else "",
                onValueChange = {
                    val newDuration = it.toIntOrNull() ?: 0
                    onUpdate(interval.type, newDuration, interval.message)
                },
                label = { Text("Duration (seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = interval.message,
                onValueChange = { onUpdate(interval.type, interval.durationSeconds, it) },
                label = { Text("TTS Message") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
