package com.kintsugirun

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: SessionDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(session?.workoutSnapshot?.workoutName ?: "Dettaglio Corsa")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Torna indietro"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Elimina sessione",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        session?.let { currentSession ->
            val formattedDate = remember(currentSession.startedAtTimestamp) {
                val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
                sdf.format(Date(currentSession.startedAtTimestamp))
            }
            val targetDuration = currentSession.workoutSnapshot.let {
                it.warmupSeconds + it.repeats * (it.runSeconds + it.walkSeconds) + it.cooldownSeconds
            }
            val gpsPoints = remember(currentSession.gpsRouteJson) {
                if (!currentSession.gpsRouteJson.isNullOrBlank()) {
                    try {
                        Json.decodeFromString<List<GpsPoint>>(currentSession.gpsRouteJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Date and Status
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
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val statusText = if (currentSession.isCompleted) {
                            "Completato"
                        } else if (!currentSession.interruptedPhase.isNullOrBlank()) {
                            "Interrotto • ${currentSession.interruptedPhase}"
                        } else {
                            "Interrotto"
                        }

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
                                    imageVector = if (currentSession.isCompleted) Icons.Filled.Check else Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (currentSession.isCompleted) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                                labelColor = if (currentSession.isCompleted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                leadingIconContentColor = if (currentSession.isCompleted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            ),
                            border = null
                        )
                    }
                }

                // Metrics: Durata and Distance
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Statistiche Sessione",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱ Durata Effettiva",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatTime(currentSession.totalElapsedSeconds),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱ Durata Prevista",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTime(targetDuration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentSession.totalDistanceMeters > 0) {
                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 Distanza Totale",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f km", currentSession.totalDistanceMeters / 1000.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🏃 Corsa Media",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.2f km", currentSession.runDistanceMeters / 1000.0)} • ${formatPace(currentSession.runPaceSecondsPerKm)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🚶 Camminata Media",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.2f km", currentSession.walkDistanceMeters / 1000.0)} • ${formatPace(currentSession.walkPaceSecondsPerKm)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Map & GPX Export
                if (gpsPoints.isNotEmpty()) {
                    var isMapFullscreen by rememberSaveable { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Tracciato GPS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                OpenStreetMapRouteView(
                                    gpsPoints = gpsPoints,
                                    showZoomControls = false,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Click overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { isMapFullscreen = true }
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clip(CircleShape)
                                        .clickable { isMapFullscreen = true },
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shadowElevation = 2.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fullscreen,
                                        contentDescription = "Schermo intero",
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            RouteLegendRow(
                                gpsPoints = gpsPoints,
                                includeMarkers = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            val gpxExportLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
                            ) { uri ->
                                if (uri != null) {
                                    coroutineScope.launch {
                                        try {
                                            val result = GpxFileManager.saveGpxToUri(
                                                context = context,
                                                uri = uri,
                                                workoutName = currentSession.workoutSnapshot.workoutName,
                                                points = gpsPoints
                                            )
                                            if (result.isSuccess) {
                                                snackbarHostState.showSnackbar("Traccia GPX salvata con successo")
                                            } else {
                                                snackbarHostState.showSnackbar("Errore durante il salvataggio della traccia GPX")
                                            }
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar("Errore durante il salvataggio della traccia GPX")
                                        }
                                    }
                                }
                            }

                            val onExportGpx: () -> Unit = {
                                try {
                                    val defaultFileName = GpxFileManager.sanitizeFileName(currentSession.workoutSnapshot.workoutName)
                                    gpxExportLauncher.launch(defaultFileName)
                                } catch (_: Exception) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Errore durante il salvataggio della traccia GPX")
                                    }
                                }
                            }

                            if (isMapFullscreen) {
                                FullscreenRouteMapDialog(
                                    workoutName = currentSession.workoutSnapshot.workoutName,
                                    gpsPoints = gpsPoints,
                                    onDismiss = { isMapFullscreen = false },
                                    onExportGpx = onExportGpx
                                )
                            }

                            OutlinedButton(
                                onClick = onExportGpx,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Salva Traccia GPX")
                            }
                        }
                    }
                }

                // Planned Workout Snapshot
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
                        Text(
                            text = "Struttura Allenamento Eseguito",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        val snapshot = currentSession.workoutSnapshot
                        if (snapshot.warmupSeconds > 0) {
                            Text(
                                text = "• Riscaldamento: ${formatTime(snapshot.warmupSeconds)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = "• Intervalli: ${snapshot.repeats} ripetizioni (Corsa ${formatTime(snapshot.runSeconds)} / Camminata ${formatTime(snapshot.walkSeconds)})",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (snapshot.cooldownSeconds > 0) {
                            Text(
                                text = "• Defaticamento: ${formatTime(snapshot.cooldownSeconds)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Elimina Sessione") },
                    text = { Text("Sei sicuro di voler eliminare questa sessione registrata?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                viewModel.deleteSession(sessionId) {
                                    onNavigateBack()
                                }
                            }
                        ) {
                            Text("Elimina", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Annulla")
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
