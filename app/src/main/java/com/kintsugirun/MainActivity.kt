package com.kintsugirun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val timerManager: TimerManager by inject()
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ttsManager = TtsManager(this)
        timerManager.setVoicePromptManager(ttsManager)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KintsugiRunApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerManager.setVoicePromptManager(null)
        ttsManager.shutdown()
    }
}
