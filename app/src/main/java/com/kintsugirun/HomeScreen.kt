package com.kintsugirun

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToWorkout: (Long) -> Unit,
    onCreateWorkout: () -> Unit
) {
    val workouts by viewModel.workouts.collectAsState()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsState()
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.enableAutoBackup(uri)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Auto-backup attivato")
                }
            } catch (e: Exception) {
                viewModel.enableAutoBackup(uri)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Auto-backup attivato")
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = viewModel.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.bufferedWriter().use { it.write(json) }
                    }
                    snackbarHostState.showSnackbar("Backup salvato con successo")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Errore durante il salvataggio del backup")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    } ?: ""
                    val result = viewModel.importBackupJson(jsonString)
                    if (result.isSuccess) {
                        val importData = result.getOrThrow()
                        snackbarHostState.showSnackbar("Ripristinati ${importData.workoutsCount} allenamenti e ${importData.sessionsCount} sessioni")
                    } else {
                        snackbarHostState.showSnackbar("Errore durante l'importazione del backup")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Errore durante l'importazione del backup")
                }
            }
        }
    }

    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("KintsugiRun Workouts") },
                actions = {
                    IconButton(onClick = {
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val rawValue = barcode.rawValue
                                if (rawValue != null) {
                                    val parsedWorkout = WorkoutQrHelper.parseWorkoutFromUri(rawValue)
                                    if (parsedWorkout != null) {
                                        viewModel.saveWorkout(parsedWorkout)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Imported: ${parsedWorkout.workoutName}")
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Invalid KintsugiRun QR code")
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Scan cancelled or failed")
                                }
                            }
                    }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR Code")
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Esporta backup (JSON)") },
                                onClick = {
                                    menuExpanded = false
                                    val fileName = "kintsugirun_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                                    exportLauncher.launch(fileName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Importa backup (JSON)") },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            )
                            HorizontalDivider()
                            if (!isAutoBackupEnabled) {
                                DropdownMenuItem(
                                    text = { Text("Attiva auto-backup continuo...") },
                                    onClick = {
                                        menuExpanded = false
                                        autoBackupFolderLauncher.launch(null)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Auto-backup attivo (cambia cartella)") },
                                    onClick = {
                                        menuExpanded = false
                                        autoBackupFolderLauncher.launch(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Disattiva auto-backup") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.disableAutoBackup()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Auto-backup disattivato")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
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
                    text = "No workouts found. Tap 'Create Workout' or scan a QR code.",
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
