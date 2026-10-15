package com.kintsugirun

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val timerManager: TimerManager by inject()
    private lateinit var ttsManager: TtsManager
    private val pendingRoute = MutableStateFlow<ActiveWorkoutRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ttsManager = TtsManager(this)
        timerManager.setVoicePromptManager(ttsManager)

        lifecycleScope.launch {
            timerManager.timerState.collect { state ->
                LockScreenHelper.setLockScreenVisibility(
                    activity = this@MainActivity,
                    show = state.isPlaying || state.totalElapsedSeconds > 0
                )
            }
        }

        checkNavigationIntent(intent)

        setContent {
            val routeToNavigate by pendingRoute.collectAsState()

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KintsugiRunApp(
                        pendingActiveRoute = routeToNavigate,
                        onRouteConsumed = { pendingRoute.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkNavigationIntent(intent)
    }

    private fun checkNavigationIntent(intent: Intent?) {
        val targetRoute = NavigationIntentHelper.extractActiveWorkoutRoute(
            intent = intent,
            timerState = timerManager.timerState.value
        )
        if (targetRoute != null) {
            pendingRoute.value = targetRoute
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LockScreenHelper.setLockScreenVisibility(this, false)
        timerManager.setVoicePromptManager(null)
        ttsManager.shutdown()
    }
}

