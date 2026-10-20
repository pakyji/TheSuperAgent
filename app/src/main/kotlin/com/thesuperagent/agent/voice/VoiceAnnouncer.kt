package com.thesuperagent.agent.voice

import android.speech.tts.TextToSpeech
import com.thesuperagent.agent.SuperAgentApp
import com.thesuperagent.agent.core.LanguageDetector
import java.util.Locale

/**
 * Lightweight TTS announcer for alarms/automations (no service needed).
 */
object VoiceAnnouncer : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun ensure() {
        if (tts == null) tts = TextToSpeech(SuperAgentApp.instance, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) while (pending.isNotEmpty()) speakNow(pending.removeFirst())
    }

    fun announce(text: String) {
        ensure()
        if (ready) speakNow(text) else pending.addLast(text)
    }

    private fun speakNow(text: String) {
        val lang = LanguageDetector.detect(text)
        tts?.language = when (lang) {
            LanguageDetector.Lang.URDU -> Locale("ur", "PK")
            LanguageDetector.Lang.ITALIAN -> Locale.ITALY
            LanguageDetector.Lang.ENGLISH -> Locale.US
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "announcer-" + System.currentTimeMillis())
    }
}
