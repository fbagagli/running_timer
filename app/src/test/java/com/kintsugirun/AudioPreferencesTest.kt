package com.kintsugirun

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val prefStorage = mutableMapOf<String, String>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        prefStorage.clear()

        every { sharedPreferences.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            prefStorage[key] ?: default
        }

        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            prefStorage[key] = value
            editor
        }

        every { editor.apply() } just Runs
        every { editor.commit() } returns true
        every { sharedPreferences.edit() } returns editor
        every { context.getSharedPreferences(AudioPreferences.PREFS_NAME, Context.MODE_PRIVATE) } returns sharedPreferences
    }

    @Test
    fun `default audio focus mode is DUCK when not configured`() {
        val audioPreferences = AudioPreferences(context, sharedPreferences)
        assertThat(audioPreferences.getAudioFocusMode()).isEqualTo(AudioFocusMode.DUCK)
        assertThat(audioPreferences.audioFocusMode.value).isEqualTo(AudioFocusMode.DUCK)
    }

    @Test
    fun `setAudioFocusMode to PAUSE saves to shared preferences and updates state flow`() {
        val audioPreferences = AudioPreferences(context, sharedPreferences)

        audioPreferences.setAudioFocusMode(AudioFocusMode.PAUSE)

        assertThat(prefStorage[AudioPreferences.KEY_AUDIO_FOCUS_MODE]).isEqualTo("PAUSE")
        assertThat(audioPreferences.getAudioFocusMode()).isEqualTo(AudioFocusMode.PAUSE)
        assertThat(audioPreferences.audioFocusMode.value).isEqualTo(AudioFocusMode.PAUSE)
        verify { editor.putString(AudioPreferences.KEY_AUDIO_FOCUS_MODE, "PAUSE") }
        verify { editor.apply() }
    }

    @Test
    fun `setAudioFocusMode to DUCK saves to shared preferences and updates state flow`() {
        prefStorage[AudioPreferences.KEY_AUDIO_FOCUS_MODE] = "PAUSE"
        val audioPreferences = AudioPreferences(context, sharedPreferences)
        assertThat(audioPreferences.getAudioFocusMode()).isEqualTo(AudioFocusMode.PAUSE)

        audioPreferences.setAudioFocusMode(AudioFocusMode.DUCK)

        assertThat(prefStorage[AudioPreferences.KEY_AUDIO_FOCUS_MODE]).isEqualTo("DUCK")
        assertThat(audioPreferences.getAudioFocusMode()).isEqualTo(AudioFocusMode.DUCK)
        assertThat(audioPreferences.audioFocusMode.value).isEqualTo(AudioFocusMode.DUCK)
        verify { editor.putString(AudioPreferences.KEY_AUDIO_FOCUS_MODE, "DUCK") }
        verify { editor.apply() }
    }

    @Test
    fun `corrupted or unknown value in shared preferences defaults to DUCK`() {
        prefStorage[AudioPreferences.KEY_AUDIO_FOCUS_MODE] = "UNKNOWN_MODE_XYZ"
        val audioPreferences = AudioPreferences(context, sharedPreferences)

        assertThat(audioPreferences.getAudioFocusMode()).isEqualTo(AudioFocusMode.DUCK)
    }

    @Test
    fun `initialization loads existing preference from shared preferences`() {
        prefStorage[AudioPreferences.KEY_AUDIO_FOCUS_MODE] = "PAUSE"
        val audioPreferences = AudioPreferences(context, sharedPreferences)

        assertThat(audioPreferences.getAudioFocusMode()).isEqualTo(AudioFocusMode.PAUSE)
        assertThat(audioPreferences.audioFocusMode.value).isEqualTo(AudioFocusMode.PAUSE)
    }
}
