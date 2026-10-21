package com.thesuperagent.agent.voice

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
 * Voice Super Agent: foreground service (type microphone).
 *
 * Loop: listen -> (wake word or open window) -> ask OpenRouter/Pollinations -> speak -> listen again.
 * The mic is stopped while we speak so the agent does not hear itself.
 * After a wake word with no command, or after an answer, a 15 s window accepts the next
 * sentence without the wake word.
 */
class VoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.thesuperagent.agent.voice.START"
        const val ACTION_PAUSE = "com.thesuperagent.agent.voice.PAUSE"
        const val ACTION_RESUME = "com.thesuperagent.agent.voice.RESUME"
        const val ACTION_STOP = "com.thesuperagent.agent.voice.STOP"
        const val ACTION_PTT = "com.thesuperagent.agent.voice.PTT"
        const val ACTION_TEST = "com.thesuperagent.agent.voice.TEST"
        const val NOTIF_ID = 4711
        private const val WINDOW_MS = 15_000L

        var micLive = false
            private set
        var lastTranscript = ""
            private set
        /** Human-readable last problem, shown on the Voice tab. */
        var lastError = ""
            private set
        var ttsStatus = "tts: starting"
            private set

        fun start(ctx: Context) {
            val i = Intent(ctx, VoiceService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        private fun errorName(e: Int): String = when (e) {
            1 -> "NETWORK_TIMEOUT"
            2 -> "NETWORK"
            3 -> "AUDIO"
            4 -> "SERVER"
            5 -> "CLIENT"
            6 -> "SPEECH_TIMEOUT"
            7 -> "NO_MATCH"
            8 -> "RECOGNIZER_BUSY"
            9 -> "INSUFFICIENT_PERMISSIONS"
            10 -> "TOO_MANY_REQUESTS"
            11 -> "SERVER_DISCONNECTED"
            12 -> "LANGUAGE_NOT_SUPPORTED"
            13 -> "LANGUAGE_UNAVAILABLE"
            else -> "ERROR_$e"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    @Volatile private var ttsReady = false
    @Volatile private var paused = false
    @Volatile private var listening = false
    @Volatile private var thinking = false
    @Volatile private var speaking = false
    @Volatile private var awakeUntil = 0L
    private var currentLang = LanguageDetector.Lang.ENGLISH

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        ttsStatus = if (ttsReady) "tts: ready"
        else "tts: FAILED. Phone par Google Text-to-speech enable karo"
        if (ttsReady) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = onSpeechFinished()
                override fun onError(utteranceId: String?) = onSpeechFinished()
            })
        }
        log(ttsStatus)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground within 5 s of startForegroundService (Android 8+),
        // even when beginListening() returns early.
        startForegroundNotif()
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> { paused = false; beginListening() }
            ACTION_PAUSE -> { paused = true; stopRecognizer(); notifyState() }
            ACTION_STOP -> { stopEverything(); stopSelf() }
            ACTION_PTT -> {
                paused = false
                awakeUntil = System.currentTimeMillis() + WINDOW_MS
                beginListening()
            }
            ACTION_TEST -> speak(currentLang, "Test. The Super Agent voice is working.")
            else -> { paused = false; beginListening() }
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
        if (paused || listening || thinking || speaking) return
        if (batteryLow()) {
            setError("battery <10%: voice paused")
            notifyState()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            paused = true
            setError("Mic permission nahi hai. Settings > Apps > The Super Agent > Permissions > Microphone ON karo")
            notifyState()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setError("Speech recognizer nahi mila. Google app / speech services enable karo")
            notifyState()
            return
        }
        stopRecognizer()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        val ri = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLang.stt)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLang.stt)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: android.os.Bundle?) {
                listening = true; micLive = true; notifyState()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {
                listening = false; micLive = false; notifyState()
            }
            override fun onError(e: Int) {
                listening = false; micLive = false
                val name = errorName(e)
                when (e) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        lastError = "sun nahi paaya ($name). Phir se bolo"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        paused = true
                        setError("Mic permission denied ($name)")
                    }
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        setError("Phone ka speech recognizer ${currentLang.stt} support nahi karta ($name)")
                    else -> setError("Recognizer error: $name")
                }
                notifyState()
                if (!paused && !speaking && !thinking) {
                    val delayMs = if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 700L
                    scope.launch { delay(delayMs); beginListening() }
                }
            }
            override fun onResults(r: android.os.Bundle?) {
                listening = false; micLive = false
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    lastTranscript = text
                    onFinalText(text)
                }
                notifyState()
                if (!paused && !speaking && !thinking) {
                    scope.launch { delay(400); beginListening() }
                }
            }
            override fun onPartialResults(r: android.os.Bundle?) {
                val list = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = list?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) lastTranscript = partial
            }
            override fun onEvent(t: Int, p: android.os.Bundle?) {}
        })
        try {
            sr.startListening(ri)
            setError("")
        } catch (t: Throwable) {
            listening = false
            setError("Mic start fail: ${t.message?.take(80)}")
        }
        startForegroundNotif()
    }

    private fun onFinalText(raw: String) {
        val text = raw.trim()
        val now = System.currentTimeMillis()
        val wake = SecurePrefs.wakeWordEnabled
        val hasWake = text.contains("hey super agent", true) || text.contains("super agent", true)
        val windowOpen = now < awakeUntil
        if (wake && !hasWake && !windowOpen) return

        val clean = if (hasWake)
            text.replace(Regex("hey super agent|super agent", RegexOption.IGNORE_CASE), "").trim()
        else text

        if (clean.isBlank()) {
            // Only the wake phrase: open a window so the next sentence is the command.
            awakeUntil = now + WINDOW_MS
            speak(currentLang, "Yes, I am listening.")
            return
        }
        awakeUntil = 0L
        currentLang = LanguageDetector.detect(clean)
        askAgent(clean)
    }

    private fun askAgent(clean: String) {
        thinking = true
        notifyState()
        scope.launch {
            LogStore.log("voice", "heard: ${clean.take(80)}")
            val res = OpenRouterClient.chat(clean, emptyList(), currentLang.label)
            thinking = false
            res.onSuccess { reply ->
                LogStore.log("voice", "reply: ${reply.take(120)}")
                speak(LanguageDetector.detect(reply), reply)
                awakeUntil = System.currentTimeMillis() + WINDOW_MS
            }.onFailure { e ->
                setError("AI error: ${e.message?.take(120)}")
                speak(currentLang, "Sorry, abhi jawab nahi de paaya.")
            }
            if (!speaking && !paused) beginListening()
            notifyState()
        }
    }

    /** Called from the TTS thread when an utterance ends. */
    private fun onSpeechFinished() {
        speaking = false
        scope.launch {
            delay(300)
            if (!paused && !thinking && !speaking) beginListening()
        }
    }

    private fun speak(lang: LanguageDetector.Lang, text: String) {
        if (!ttsReady || tts == null) {
            setError("TTS ready nahi hai: reply bol nahi sakta. Google Text-to-speech check karo")
            log("tts not ready, reply skipped")
            return
        }
        val loc = when (lang) {
            LanguageDetector.Lang.URDU -> Locale("ur", "PK")
            LanguageDetector.Lang.ITALIAN -> Locale.ITALY
            LanguageDetector.Lang.ENGLISH -> Locale.US
        }
        val avail = tts?.setLanguage(loc)
        if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            setError("${loc.toLanguageTag()} voice phone par installed nahi hai. Settings > Text-to-speech se install karo")
        }
        // Stop listening while we talk, so the agent does not transcribe its own voice.
        stopRecognizer()
        speaking = true
        val r = tts?.speak(text.take(900), TextToSpeech.QUEUE_FLUSH, null,
            "sa-${System.currentTimeMillis()}")
        if (r == TextToSpeech.ERROR) {
            speaking = false
            setError("TTS speak error")
        }
        notifyState()
    }

    private fun stopRecognizer() {
        try { recognizer?.cancel(); recognizer?.destroy() } catch (t: Throwable) {}
        recognizer = null
        listening = false
        micLive = false
    }

    private fun setError(msg: String) {
        lastError = msg
        if (msg.isNotBlank()) log(msg)
    }

    private fun log(msg: String) {
        scope.launch { LogStore.log("voice", msg) }
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
            // Fallback: start without an explicit type.
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
            micLive -> "🔴 MIC LIVE: listening"
            thinking -> "🧠 thinking…"
            speaking -> "🔊 speaking…"
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
        ttsReady = false
        micLive = false
        scope.cancel()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
