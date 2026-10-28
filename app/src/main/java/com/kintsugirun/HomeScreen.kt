package com.kintsugirun

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onCreateWorkout: () -> Unit,
    onNavigateToSession: (Long) -> Unit = {},
    audioPreferences: AudioPreferences? = null
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val workouts by viewModel.workouts.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsState()
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAudioSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val effectiveAudioPreferences = audioPreferences ?: viewModel.audioPreferences ?: remember(context) { AudioPreferences(context) }

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
            } catch (_: Exception) {
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
                } catch (_: Exception) {
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
                } catch (_: Exception) {
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
                title = {
                    Text(if (selectedTab == 0) "Allenamenti" else "Cronologia")
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = {
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawValue = barcode.rawValue
                                    if (rawValue != null) {
                                        val parsedWorkout = WorkoutQrHelper.parseWorkoutFromUri(rawValue)
                                        if (parsedWorkout != null) {
                                            viewModel.saveWorkout(parsedWorkout)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Importato: ${parsedWorkout.workoutName}")
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Codice QR non valido per KintsugiRun")
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Scansione annullata o non riuscita")
                                    }
                                }
                        }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scansiona QR Code")
                        }
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Altre opzioni")
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
                            DropdownMenuItem(
                                text = { Text("Impostazioni audio...") },
                                onClick = {
                                    menuExpanded = false
                                    showAudioSettingsDialog = true
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Allenamenti") },
                    label = { Text("Allenamenti") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.History, contentDescription = "Cronologia") },
                    label = { Text("Cronologia") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onCreateWorkout,
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Crea Allenamento") },
                    text = { Text("Crea Allenamento") }
                )
            }
        }
    ) { paddingValues ->
        if (selectedTab == 0) {
            if (workouts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun allenamento trovato. Tocca 'Crea Allenamento' o scansiona un codice QR.",
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
        } else {
            HistoryScreen(
                sessions = sessions,
                onNavigateToSession = onNavigateToSession,
                modifier = Modifier.padding(paddingValues)
            )
        }

        workoutToDelete?.let { workout ->
            AlertDialog(
                onDismissRequest = { workoutToDelete = null },
                title = { Text("Elimina Allenamento") },
                text = { Text("Sei sicuro di voler eliminare \"${workout.workoutName}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteWorkout(workout.id)
                            workoutToDelete = null
                        }
                    ) {
                        Text("Elimina", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workoutToDelete = null }) {
                        Text("Annulla")
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
                    text = "${workout.repeats} rip • Corsa ${formatTime(workout.runSeconds)} / Camminata ${formatTime(workout.walkSeconds)} • ${formatTime(totalSeconds)} totale",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Elimina Allenamento",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
