package com.kintsugirun

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReviewScreen(
    workoutId: Long,
    viewModel: WorkoutReviewViewModel,
    onStartWorkout: (announceRepetitions: Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onEditWorkout: (workoutId: Long) -> Unit
) {
    val workout by viewModel.workout.collectAsState()
    val latestSession by viewModel.latestSession.collectAsState()
    var announceRepetitions by rememberSaveable { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
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
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "Share QR Code"
                        )
                    }
                    IconButton(onClick = { onEditWorkout(workoutId) }) {
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

                    latestSession?.let { session ->
                        LatestSessionCard(session = session)
                    }

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

                if (currentWorkout.repeats > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = announceRepetitions,
                            onCheckedChange = { announceRepetitions = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Announce repetition numbers (e.g. \"2 of ${currentWorkout.repeats}\")",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { onStartWorkout(announceRepetitions) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("Start Workout", style = MaterialTheme.typography.titleLarge)
                }
            }

            if (showQrDialog) {
                val qrUri = WorkoutQrHelper.encodeWorkoutToUri(currentWorkout)
                val qrBitmap = remember(qrUri) {
                    WorkoutQrHelper.generateQrBitmap(qrUri, 512)
                }

                AlertDialog(
                    onDismissRequest = { showQrDialog = false },
                    title = { Text("Share Workout") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentWorkout.workoutName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Workout QR Code",
                                modifier = Modifier.size(240.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan with KintsugiRun to import",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showQrDialog = false }) {
                            Text("Close")
                        }
                    }
                )
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

@Composable
fun LatestSessionCard(session: WorkoutSession) {
    val formattedDate = remember(session.startedAtTimestamp) {
        val sdf = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        sdf.format(Date(session.startedAtTimestamp))
    }
    val targetDuration = session.workoutSnapshot.let {
        it.warmupSeconds + it.repeats * (it.runSeconds + it.walkSeconds) + it.cooldownSeconds
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ultima esecuzione",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = if (session.isCompleted) {
                    "Completato"
                } else if (!session.interruptedPhase.isNullOrBlank()) {
                    "Interrotto • ${session.interruptedPhase}"
                } else {
                    "Interrotto"
                }

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = statusText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (session.isCompleted) Icons.Filled.Check else Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (session.isCompleted) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            labelColor = if (session.isCompleted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            leadingIconContentColor = if (session.isCompleted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        ),
                        border = null
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Durata: ${formatTime(session.totalElapsedSeconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Prevista: ${formatTime(targetDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
