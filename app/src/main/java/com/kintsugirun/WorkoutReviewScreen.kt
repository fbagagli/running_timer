package com.kintsugirun

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tune
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReviewScreen(
    workoutId: Long,
    viewModel: WorkoutReviewViewModel,
    onStartWorkout: (announceRepetitions: Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onEditWorkout: (workoutId: Long) -> Unit,
    audioPreferences: AudioPreferences? = null
) {
    val workout by viewModel.workout.collectAsState()
    var announceRepetitions by rememberSaveable { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val effectiveAudioPreferences = audioPreferences ?: viewModel.audioPreferences ?: remember(context) { AudioPreferences(context) }
    val audioFocusMode by effectiveAudioPreferences.audioFocusMode.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(workout?.workoutName ?: "Caricamento...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Torna indietro"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "Condividi codice QR"
                        )
                    }
                    IconButton(onClick = { onEditWorkout(workoutId) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Modifica allenamento"
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
                        text = "Durata prevista: ${formatTime(totalDuration)}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (currentWorkout.warmupSeconds > 0) {
                        WorkoutPhaseCard(
                            phaseName = "Riscaldamento",
                            durationSeconds = currentWorkout.warmupSeconds,
                            details = "Corsa lenta o camminata preparatoria"
                        )
                    }

                    WorkoutPhaseCard(
                        phaseName = "Intervalli (${currentWorkout.repeats} Ripetizioni)",
                        durationSeconds = currentWorkout.repeats * (currentWorkout.runSeconds + currentWorkout.walkSeconds),
                        details = "• Corsa: ${formatTime(currentWorkout.runSeconds)}\n• Camminata: ${formatTime(currentWorkout.walkSeconds)}"
                    )

                    if (currentWorkout.cooldownSeconds > 0) {
                        WorkoutPhaseCard(
                            phaseName = "Defaticamento",
                            durationSeconds = currentWorkout.cooldownSeconds,
                            details = "Camminata di recupero & stretching"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Opzioni corsa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { announceRepetitions = !announceRepetitions }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = announceRepetitions,
                            onCheckedChange = { announceRepetitions = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentWorkout.repeats > 1) {
                                "Annuncia numero ripetizioni (es. \"2 di ${currentWorkout.repeats}\")"
                            } else {
                                "Annuncia numero ripetizioni"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAudioSettingsDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Impostazioni audio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Musica: ${if (audioFocusMode == AudioFocusMode.DUCK) "Abbassa volume" else "Pausa"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (audioFocusMode == AudioFocusMode.DUCK)
                                            "La musica continua a basso volume in sottofondo"
                                        else
                                            "Mette in pausa per podcast e audiolibri",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { showAudioSettingsDialog = true }) {
                                Text("Modifica")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onStartWorkout(announceRepetitions) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Inizia Allenamento", style = MaterialTheme.typography.titleLarge)
                }
            }

            if (showQrDialog) {
                val qrUri = WorkoutQrHelper.encodeWorkoutToUri(currentWorkout)
                val qrBitmap = remember(qrUri) {
                    WorkoutQrHelper.generateQrBitmap(qrUri, 512)
                }

                AlertDialog(
                    onDismissRequest = { showQrDialog = false },
                    title = { Text("Condividi Allenamento") },
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
                                text = "Scansiona con KintsugiRun per importare",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showQrDialog = false }) {
                            Text("Chiudi")
                        }
                    }
                )
            }

            if (showAudioSettingsDialog) {
                AudioSettingsDialog(
                    audioPreferences = effectiveAudioPreferences,
                    onDismissRequest = { showAudioSettingsDialog = false }
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

internal fun formatPace(paceSecondsPerKm: Int): String {
    if (paceSecondsPerKm <= 0) return "--'--\" /km"
    val mins = paceSecondsPerKm / 60
    val secs = paceSecondsPerKm % 60
    return String.format(Locale.US, "%d'%02d\" /km", mins, secs)
}

internal fun formatTime(seconds: Int): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
