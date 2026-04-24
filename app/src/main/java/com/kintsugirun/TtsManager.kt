package com.kintsugirun

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var queuedMessage: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.ENGLISH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "The Language specified is not supported!")
            } else {
                isReady = true
                queuedMessage?.let {
                    speak(it)
                    queuedMessage = null
                }
            }
        } else {
            Log.e("TtsManager", "Initialization Failed!")
        }
    }

    fun speak(message: String) {
        if (isReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w("TtsManager", "TTS is not ready yet, queuing message: $message")
            queuedMessage = message
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
