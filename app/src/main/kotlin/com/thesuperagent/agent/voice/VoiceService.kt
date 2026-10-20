package com.thesuperagent.agent.voice

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.thesuperagent.agent.R
import com.thesuperagent.agent.SuperAgentApp
import com.thesuperagent.agent.core.LanguageDetector
import com.thesuperagent.agent.core.LogStore
import com.thesuperagent.agent.core.OpenRouterClient
import com.thesuperagent.agent.core.SecurePrefs
import com.thesuperagent.agent.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Voice Super Agent — foreground service (type microphone).
 * Wake word "Hey Super Agent", STT ur-PK/it-IT/en-US, TTS TextToSpeech.
 * Battery-aware, push-to-talk fallback, mic privacy indicator in notification.
 */
class VoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.thesuperagent.agent.voice.START"
        const val ACTION_PAUSE = "com.thesuperagent.agent.voice.PAUSE"
        const val ACTION_RESUME = "com.thesuperagent.agent.voice.RESUME"
        const val ACTION_STOP = "com.thesuperagent.agent.voice.STOP"
        const val ACTION_PTT = "com.thesuperagent.agent.voice.PTT"
        const val NOTIF_ID = 4711
        var micLive = false
            private set
        var lastTranscript = ""
            private set

        fun start(ctx: Context) {
            val i = Intent(ctx, VoiceService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var paused = false
    private var listening = false
    private var thinking = false
    private var currentLang = LanguageDetector.Lang.ENGLISH

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> { paused = false; beginListening() }
            ACTION_PAUSE -> { paused = true; stopRecognizer(); notifyState() }
            ACTION_STOP -> { stopEverything(); stopSelf() }
            ACTION_PTT -> { paused = false; beginListening() }
            else -> beginListening()
        }
        return START_STICKY
    }

    private fun batteryLow(): Boolean {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = registerReceiver(null, ifilter)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return false
        return (level * 100 / scale) < 10
    }

    private fun beginListening() {
        if (listening || thinking) return
        if (batteryLow()) {
            scope.launch { LogStore.log("voice", "battery <10% — voice paused (battery-aware)") }
            notifyState()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { notifyState(); return }
        stopRecognizer()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        val ri = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLang.stt)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLang.stt)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.setListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: android.os.Bundle?) { listening = true; micLive = true; notifyState() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; micLive = false; notifyState() }
            override fun onError(e: Int) {
                listening = false; micLive = false; notifyState()
                if (!paused) scope.launch { delay(700); beginListening() }
            }
            override fun onResults(r: android.os.Bundle?) {
                listening = false; micLive = false
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onFinalText(text)
                notifyState()
                if (!paused) scope.launch { delay(400); beginListening() }
            }
            override fun onPartialResults(r: android.os.Bundle?) {
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                lastTranscript = list?.firstOrNull().orEmpty()
            }
            override fun onEvent(t: Int, p: android.os.Bundle?) {}
        })
        try { sr.startListening(ri) } catch (t: Throwable) { listening = false }
        startForegroundNotif()
    }

    private fun stopRecognizer() {
        try { recognizer?.cancel(); recognizer?.destroy() } catch (t: Throwable) {}
        recognizer = null
        listening = false
        micLive = false
    }

    private fun onFinalText(raw: String) {
        val text = raw.trim()
        lastTranscript = text
        val wake = SecurePrefs.wakeWordEnabled
        if (wake && !text.contains("hey super agent", true) && !text.contains("super agent", true)) {
            return
        }
        val clean = if (wake)
            text.replace(Regex("hey super agent|super agent", RegexOption.IGNORE_CASE), "").trim()
        else text
        if (clean.isBlank()) { speak(currentLang, "Yes, I am listening.") ; return }
        currentLang = LanguageDetector.detect(clean)
        thinking = true
        notifyState()
        scope.launch {
            LogStore.log("voice", "heard: $clean")
            val res = OpenRouterClient.chat(clean, emptyList(), currentLang.label)
            res.onSuccess { reply ->
                LogStore.log("voice", "reply: ${reply.take(120)}")
                speak(LanguageDetector.detect(reply), reply)
            }.onFailure { e ->
                speak(currentLang, "Offline mode: " + (e.message ?: "error"))
            }
            thinking = false
            notifyState()
        }
    }

    private fun speak(lang: LanguageDetector.Lang, text: String) {
        if (!ttsReady || tts == null) return
        val loc = when (lang) {
            LanguageDetector.Lang.URDU -> Locale("ur", "PK")
            LanguageDetector.Lang.ITALIAN -> Locale.ITALY
            LanguageDetector.Lang.ENGLISH -> Locale.US
        }
        tts?.language = loc
        tts?.speak(text.take(900), TextToSpeech.QUEUE_FLUSH, null, "super-agent")
    }

    private fun startForegroundNotif() {
        val n = buildNotif()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (t: Throwable) {
            // HyperOS fallback: immediate start without explicit type
            try { startForeground(NOTIF_ID, n) } catch (t2: Throwable) {}
        }
    }

    private fun buildNotif(): android.app.Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1,
            Intent(this, VoiceService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val resume = PendingIntent.getService(this, 2,
            Intent(this, VoiceService::class.java).setAction(ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val state = when {
            paused -> "⏸ Paused"
            micLive -> "🔴 MIC LIVE — listening"
            thinking -> "🧠 thinking…"
            else -> getString(R.string.notif_listening)
        }
        return NotificationCompat.Builder(this, SuperAgentApp.VOICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("The Super Agent")
            .setContentText(state)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Pause", pause)
            .addAction(0, "Resume", resume)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun notifyState() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotif())
        } catch (t: Throwable) {}
    }

    private fun stopEverything() {
        stopRecognizer()
        try { tts?.stop(); tts?.shutdown() } catch (t: Throwable) {}
        tts = null
        micLive = false
        scope.cancel()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
