package com.kintsugirun

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioFocusMode {
    DUCK,
    PAUSE
}

class AudioPreferences(
    private val context: Context,
    preferences: SharedPreferences? = null
) {
    companion object {
        const val PREFS_NAME = "kintsugi_audio_prefs"
        const val KEY_AUDIO_FOCUS_MODE = "audio_focus_mode"
    }

    private val prefs: SharedPreferences by lazy {
        preferences ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _audioFocusMode = MutableStateFlow(readStoredMode())
    val audioFocusMode: StateFlow<AudioFocusMode> = _audioFocusMode.asStateFlow()

    private fun readStoredMode(): AudioFocusMode {
        val name = prefs.getString(KEY_AUDIO_FOCUS_MODE, null) ?: return AudioFocusMode.DUCK
        return try {
            AudioFocusMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            AudioFocusMode.DUCK
        }
    }

    fun getAudioFocusMode(): AudioFocusMode {
        return readStoredMode()
    }

    fun setAudioFocusMode(mode: AudioFocusMode) {
        prefs.edit().putString(KEY_AUDIO_FOCUS_MODE, mode.name).apply()
        _audioFocusMode.value = mode
    }
}
