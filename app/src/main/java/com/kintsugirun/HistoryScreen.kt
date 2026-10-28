package com.kintsugirun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    sessions: List<WorkoutSession>,
    onNavigateToSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "Nessuna corsa registrata. Seleziona un allenamento e inizia a correre!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onNavigateToSession(session.id) }
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(
    repository: WorkoutRepository,
    onNavigateToSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by repository.getAllSessions().collectAsState(initial = emptyList())
    HistoryScreen(
        sessions = sessions,
        onNavigateToSession = onNavigateToSession,
        modifier = modifier
    )
}

@Composable
fun SessionCard(
    session: WorkoutSession,
    onClick: () -> Unit
) {
    val formattedDate = remember(session.startedAtTimestamp) {
        val sdf = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        sdf.format(Date(session.startedAtTimestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val statusText = if (session.isCompleted) {
                    "Completato"
                } else if (!session.interruptedPhase.isNullOrBlank()) {
                    "Interrotto • ${session.interruptedPhase}"
                } else {
                    "Interrotto"
                }

                AssistChip(
                    onClick = onClick,
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

            Text(
                text = session.workoutSnapshot.workoutName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val summaryText = if (session.totalDistanceMeters > 0) {
                val distanceKm = String.format(Locale.US, "%.2f km", session.totalDistanceMeters / 1000.0)
                val duration = formatTime(session.totalElapsedSeconds)
                val pace = if (session.runPaceSecondsPerKm > 0) {
                    formatPace(session.runPaceSecondsPerKm)
                } else if (session.totalDistanceMeters > 10.0 && session.totalElapsedSeconds > 0) {
                    val p = (session.totalElapsedSeconds / (session.totalDistanceMeters / 1000.0)).toInt()
                    formatPace(p)
                } else {
                    "--'--\" /km"
                }
                "$distanceKm • $duration • $pace"
            } else {
                "Durata: ${formatTime(session.totalElapsedSeconds)}"
            }

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
