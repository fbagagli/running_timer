package com.kintsugirun

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import java.util.UUID

class TtsManager(
    private val context: Context,
    val audioPreferences: AudioPreferences = AudioPreferences(context),
    private val audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    ttsFactory: ((TextToSpeech.OnInitListener) -> TextToSpeech)? = null,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : TextToSpeech.OnInitListener, VoicePromptManager {

    companion object {
        const val PRE_SPEECH_DUCK_DELAY_MS = 1000L
        const val POST_SPEECH_RELEASE_DELAY_MS = 400L
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = null
    internal var isReady = false
    private var queuedMessage: String? = null

    internal val activeUtterances = Collections.synchronizedSet(mutableSetOf<String>())
    private var releaseJob: Job? = null
    internal var isFocusHeld = false
    internal var currentFocusRequest: AudioFocusRequest? = null
    internal var lastFocusGain: Int? = null

    private val audioAttributes: AudioAttributes? = runCatching {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }.getOrNull()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { /* no-op */ }

    internal val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "Utterance started: $utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "Utterance done: $utteranceId")
            handleUtteranceFinished(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.e(TAG, "Utterance error: $utteranceId")
            handleUtteranceFinished(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "Utterance error ($errorCode): $utteranceId")
            handleUtteranceFinished(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            Log.d(TAG, "Utterance stopped: $utteranceId (interrupted: $interrupted)")
            handleUtteranceFinished(utteranceId)
        }
    }

    init {
        tts = ttsFactory?.invoke(this) ?: TextToSpeech(context.applicationContext, this)
        tts?.setOnUtteranceProgressListener(utteranceProgressListener)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            audioAttributes?.let { tts?.setAudioAttributes(it) }
            tts?.setOnUtteranceProgressListener(utteranceProgressListener)
            val result = tts?.setLanguage(Locale.ENGLISH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "The Language specified is not supported!")
            } else {
                isReady = true
                queuedMessage?.let {
                    val messageToSpeak = it
                    queuedMessage = null
                    speak(messageToSpeak)
                }
            }
        } else {
            Log.e(TAG, "Initialization Failed!")
        }
    }

    override fun speak(message: String) {
        if (!isReady) {
            Log.w(TAG, "TTS is not ready yet, queuing message: $message")
            queuedMessage = message
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        synchronized(this) {
            activeUtterances.add(utteranceId)
            releaseJob?.cancel()
            releaseJob = null
            acquireAudioFocus()
        }

        coroutineScope.launch {
            delay(PRE_SPEECH_DUCK_DELAY_MS)
            val result = tts?.speak(message, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS speak failed with result: $result")
                handleUtteranceFinished(utteranceId)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun acquireAudioFocus() {
        val audioMgr = audioManager ?: return
        val mode = audioPreferences.getAudioFocusMode()
        val focusGain = when (mode) {
            AudioFocusMode.DUCK -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AudioFocusMode.PAUSE -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        }

        if (isFocusHeld && lastFocusGain == focusGain) {
            return
        }

        val result = if (sdkInt >= Build.VERSION_CODES.O) {
            requestAudioFocusOreo(audioMgr, focusGain)
        } else {
            @Suppress("DEPRECATION")
            audioMgr.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, focusGain)
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            isFocusHeld = true
            lastFocusGain = focusGain
        } else {
            Log.w(TAG, "Audio focus request returned: $result")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusOreo(audioMgr: AudioManager, focusGain: Int): Int {
        val builder = AudioFocusRequest.Builder(focusGain)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
        audioAttributes?.let { builder.setAudioAttributes(it) }
        val request = builder.build()
        currentFocusRequest = request
        return audioMgr.requestAudioFocus(request)
    }

    private fun handleUtteranceFinished(utteranceId: String?) {
        synchronized(this) {
            utteranceId?.let { activeUtterances.remove(it) }
            if (activeUtterances.isEmpty()) {
                releaseJob?.cancel()
                releaseJob = coroutineScope.launch {
                    delay(POST_SPEECH_RELEASE_DELAY_MS)
                    synchronized(this@TtsManager) {
                        if (activeUtterances.isEmpty()) {
                            abandonAudioFocus()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun abandonAudioFocus() {
        val audioMgr = audioManager
        if (sdkInt >= Build.VERSION_CODES.O) {
            abandonAudioFocusOreo(audioMgr)
        } else {
            @Suppress("DEPRECATION")
            audioMgr?.abandonAudioFocus(audioFocusChangeListener)
        }
        isFocusHeld = false
        lastFocusGain = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocusOreo(audioMgr: AudioManager?) {
        currentFocusRequest?.let { request ->
            audioMgr?.abandonAudioFocusRequest(request)
            currentFocusRequest = null
        }
    }

    fun shutdown() {
        coroutineScope.coroutineContext.cancelChildren()
        synchronized(this) {
            activeUtterances.clear()
            releaseJob?.cancel()
            releaseJob = null
            abandonAudioFocus()
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
