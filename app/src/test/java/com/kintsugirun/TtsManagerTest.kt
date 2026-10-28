package com.kintsugirun

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TtsManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var audioPreferences: AudioPreferences
    private lateinit var textToSpeech: TextToSpeech
    private var utteranceListenerSlot = slot<UtteranceProgressListener>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockkConstructor(AudioAttributes.Builder::class)
        every { anyConstructed<AudioAttributes.Builder>().setUsage(any()) } answers { self as AudioAttributes.Builder }
        every { anyConstructed<AudioAttributes.Builder>().setContentType(any()) } answers { self as AudioAttributes.Builder }
        every { anyConstructed<AudioAttributes.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(AudioFocusRequest.Builder::class)
        every { anyConstructed<AudioFocusRequest.Builder>().setAudioAttributes(any()) } answers { self as AudioFocusRequest.Builder }
        every { anyConstructed<AudioFocusRequest.Builder>().setAcceptsDelayedFocusGain(any()) } answers { self as AudioFocusRequest.Builder }
        every { anyConstructed<AudioFocusRequest.Builder>().setOnAudioFocusChangeListener(any()) } answers { self as AudioFocusRequest.Builder }
        every { anyConstructed<AudioFocusRequest.Builder>().build() } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        audioPreferences = mockk(relaxed = true)
        textToSpeech = mockk(relaxed = true)

        every { audioPreferences.getAudioFocusMode() } returns AudioFocusMode.DUCK
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        @Suppress("DEPRECATION")
        every { audioManager.requestAudioFocus(any(), any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { audioManager.abandonAudioFocusRequest(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        @Suppress("DEPRECATION")
        every { audioManager.abandonAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        every { textToSpeech.setLanguage(Locale.ENGLISH) } returns TextToSpeech.LANG_AVAILABLE
        every { textToSpeech.setOnUtteranceProgressListener(capture(utteranceListenerSlot)) } returns TextToSpeech.SUCCESS
        every { textToSpeech.speak(any(), any(), any(), any()) } returns TextToSpeech.SUCCESS
    }

    @After
    fun tearDown() {
        unmockkConstructor(AudioAttributes.Builder::class)
        unmockkConstructor(AudioFocusRequest.Builder::class)
        unmockkStatic(Log::class)
    }

    private fun createTtsManager(sdkInt: Int = Build.VERSION_CODES.O): TtsManager {
        return TtsManager(
            context = context,
            audioPreferences = audioPreferences,
            audioManager = audioManager,
            coroutineScope = testScope,
            ttsFactory = { textToSpeech },
            sdkInt = sdkInt
        )
    }

    @Test
    fun `speak when TTS is not ready queues the message`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        assertThat(manager.isReady).isFalse()

        manager.speak("Queued Message")

        verify(exactly = 0) { textToSpeech.speak(any(), any(), any(), any()) }
        verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
    }

    @Test
    fun `onInit with SUCCESS sets ready and processes queued message`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        manager.speak("Queued Message")

        manager.onInit(TextToSpeech.SUCCESS)

        assertThat(manager.isReady).isTrue()
        verify { textToSpeech.setLanguage(Locale.ENGLISH) }
        verify { textToSpeech.setAudioAttributes(any()) }

        // Audio focus is acquired immediately
        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        assertThat(manager.isFocusHeld).isTrue()

        // Wait duck delay
        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()
        verify(exactly = 1) {
            textToSpeech.speak("Queued Message", TextToSpeech.QUEUE_ADD, null, any())
        }
    }

    @Test
    fun `onInit with ERROR does not set ready`() = runTest(testDispatcher) {
        val manager = createTtsManager()

        manager.onInit(TextToSpeech.ERROR)

        assertThat(manager.isReady).isFalse()
        manager.speak("Should Not Speak")
        verify(exactly = 0) { textToSpeech.speak(any(), any(), any(), any()) }
    }

    @Test
    fun `speak with DUCK mode requests audio focus with TRANSIENT_MAY_DUCK`() = runTest(testDispatcher) {
        every { audioPreferences.getAudioFocusMode() } returns AudioFocusMode.DUCK
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Running Interval")

        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        assertThat(manager.isFocusHeld).isTrue()
        assertThat(manager.lastFocusGain).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }

    @Test
    fun `speak with PAUSE mode requests audio focus with AUDIOFOCUS_GAIN_TRANSIENT`() = runTest(testDispatcher) {
        every { audioPreferences.getAudioFocusMode() } returns AudioFocusMode.PAUSE
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Running Interval")

        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        assertThat(manager.isFocusHeld).isTrue()
        assertThat(manager.lastFocusGain).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    }

    @Test
    fun `audio focus on API 24 uses legacy requestAudioFocus and abandonAudioFocus`() = runTest(testDispatcher) {
        every { audioPreferences.getAudioFocusMode() } returns AudioFocusMode.PAUSE
        val manager = createTtsManager(sdkInt = 24)
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Legacy Audio Focus")

        @Suppress("DEPRECATION")
        verify(exactly = 1) {
            audioManager.requestAudioFocus(any(), AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        assertThat(manager.isFocusHeld).isTrue()

        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()
        val utteranceSlot = slot<String>()
        verify(exactly = 1) {
            textToSpeech.speak("Legacy Audio Focus", TextToSpeech.QUEUE_ADD, null, capture(utteranceSlot))
        }

        manager.utteranceProgressListener.onDone(utteranceSlot.captured)
        testDispatcher.scheduler.advanceTimeBy(TtsManager.POST_SPEECH_RELEASE_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        @Suppress("DEPRECATION")
        verify(exactly = 1) { audioManager.abandonAudioFocus(any()) }
        assertThat(manager.isFocusHeld).isFalse()
    }

    @Test
    fun `utterance onDone releases audio focus after post-speech release delay`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Workout started")
        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        val utteranceSlot = slot<String>()
        verify(exactly = 1) {
            textToSpeech.speak("Workout started", TextToSpeech.QUEUE_ADD, null, capture(utteranceSlot))
        }

        // When speech finishes, focus should NOT be released before 400ms delay
        manager.utteranceProgressListener.onDone(utteranceSlot.captured)
        testDispatcher.scheduler.advanceTimeBy(200L)
        testDispatcher.scheduler.runCurrent()
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isTrue()

        // After remaining 200ms (total 400ms), focus is abandoned
        testDispatcher.scheduler.advanceTimeBy(200L)
        testDispatcher.scheduler.runCurrent()
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isFalse()
    }

    @Test
    fun `utterance onError releases audio focus after post-speech release delay`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Error prompt")
        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        val utteranceSlot = slot<String>()
        verify(exactly = 1) {
            textToSpeech.speak("Error prompt", TextToSpeech.QUEUE_ADD, null, capture(utteranceSlot))
        }

        @Suppress("DEPRECATION")
        manager.utteranceProgressListener.onError(utteranceSlot.captured)
        testDispatcher.scheduler.advanceTimeBy(TtsManager.POST_SPEECH_RELEASE_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isFalse()
    }

    @Test
    fun `multiple utterances hold audio focus until the last one finishes`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("First message")
        manager.speak("Second message")

        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        val utteranceSlots = mutableListOf<String>()
        verify(exactly = 2) {
            textToSpeech.speak(any(), TextToSpeech.QUEUE_ADD, null, capture(utteranceSlots))
        }
        assertThat(utteranceSlots.size).isEqualTo(2)

        val id1 = utteranceSlots[0]
        val id2 = utteranceSlots[1]

        // First finishes
        manager.utteranceProgressListener.onDone(id1)
        testDispatcher.scheduler.advanceTimeBy(TtsManager.POST_SPEECH_RELEASE_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        // Focus still held because second message is not done
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isTrue()

        // Second finishes
        manager.utteranceProgressListener.onDone(id2)
        testDispatcher.scheduler.advanceTimeBy(TtsManager.POST_SPEECH_RELEASE_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        // Now focus is released
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isFalse()
    }

    @Test
    fun `new utterance during post-speech release delay cancels release`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Message 1")
        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        val id1Slot = slot<String>()
        verify { textToSpeech.speak("Message 1", TextToSpeech.QUEUE_ADD, null, capture(id1Slot)) }

        manager.utteranceProgressListener.onDone(id1Slot.captured)
        // Advance 200ms (halfway through the 400ms release delay)
        testDispatcher.scheduler.advanceTimeBy(200L)
        testDispatcher.scheduler.runCurrent()

        // New message arrives before focus release
        manager.speak("Message 2")

        // Advance 300ms more (past the original 400ms mark)
        testDispatcher.scheduler.advanceTimeBy(300L)
        testDispatcher.scheduler.runCurrent()
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isTrue()

        // Advance remaining duck delay for Message 2
        testDispatcher.scheduler.advanceTimeBy(700L)
        testDispatcher.scheduler.runCurrent()
        val id2Slot = slot<String>()
        verify { textToSpeech.speak("Message 2", TextToSpeech.QUEUE_ADD, null, capture(id2Slot)) }

        // Finish Message 2
        manager.utteranceProgressListener.onDone(id2Slot.captured)
        testDispatcher.scheduler.advanceTimeBy(TtsManager.POST_SPEECH_RELEASE_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isFalse()
    }

    @Test
    fun `shutdown cancels pending coroutines, stops TTS, and abandons audio focus immediately`() = runTest(testDispatcher) {
        val manager = createTtsManager()
        manager.onInit(TextToSpeech.SUCCESS)

        manager.speak("Message before shutdown")
        assertThat(manager.isFocusHeld).isTrue()

        manager.shutdown()

        verify(exactly = 1) { textToSpeech.stop() }
        verify(exactly = 1) { textToSpeech.shutdown() }
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
        assertThat(manager.isFocusHeld).isFalse()
        assertThat(manager.isReady).isFalse()

        // Advance time - no speaking should happen since coroutines were cancelled
        testDispatcher.scheduler.advanceTimeBy(TtsManager.PRE_SPEECH_DUCK_DELAY_MS)
        testDispatcher.scheduler.runCurrent()
        verify(exactly = 0) { textToSpeech.speak(any(), any(), any(), any()) }
    }
}
