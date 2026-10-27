package com.kintsugirun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AudioSettingsDialog(
    currentMode: AudioFocusMode,
    onModeSelected: (AudioFocusMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Interazione con la musica",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AudioModeOptionItem(
                    title = "Abbassa volume (Fade-out)",
                    description = "La musica continua a basso volume in sottofondo durante gli avvisi",
                    selected = currentMode == AudioFocusMode.DUCK,
                    onClick = { onModeSelected(AudioFocusMode.DUCK) }
                )

                AudioModeOptionItem(
                    title = "Metti in pausa",
                    description = "Consigliato per podcast e audiolibri; la riproduzione riprende dopo l'avviso",
                    selected = currentMode == AudioFocusMode.PAUSE,
                    onClick = { onModeSelected(AudioFocusMode.PAUSE) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
fun AudioSettingsDialog(
    audioPreferences: AudioPreferences,
    onDismissRequest: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(audioPreferences.getAudioFocusMode()) }

    AudioSettingsDialog(
        currentMode = selectedMode,
        onModeSelected = { newMode ->
            selectedMode = newMode
            audioPreferences.setAudioFocusMode(newMode)
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun AudioModeOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
