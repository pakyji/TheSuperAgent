package com.thesuperagent.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thesuperagent.agent.automation.AutomationEngine
import com.thesuperagent.agent.builder.AppBuilderPro
import com.thesuperagent.agent.core.*
import com.thesuperagent.agent.modules.SpotifySuperControl
import com.thesuperagent.agent.services.NotificationReaderService
import com.thesuperagent.agent.super12.AINotes
import com.thesuperagent.agent.tools.DeviceControlHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatMsg(val role: String, val content: String, val lang: String = "en")

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    private val _chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chat: StateFlow<List<ChatMsg>> = _chat

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _status = MutableStateFlow("ready")
    val status: StateFlow<String> = _status

    val models = MutableStateFlow<List<String>>(listOf("openai/gpt-4o-mini"))
    val logs = MemoryDb.dao().logs()
    val memories = MemoryDb.dao().all()
    val notes = MemoryDb.dao().notes()
    val clips = MemoryDb.dao().clips()
    val tasks = AutomationEngine.tasks
    val track = SpotifySuperControl.currentTrack
    val builderLogs = MutableStateFlow<List<String>>(emptyList())

    init {
        AutomationEngine.load()
        viewModelScope.launch {
            models.value = OpenRouterClient.models()
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _busy.value) return
        val lang = LanguageDetector.detect(text)
        _chat.value = _chat.value + ChatMsg("user", text, lang.label)
        _busy.value = true
        _status.value = "thinking…"
        viewModelScope.launch {
            MemoryDb.dao().insert(MemoryEntity(role = "user", content = text, lang = lang.label))
            LogStore.log("chat", "user: ${text.take(80)}")
            val history = _chat.value.dropLast(1).map { OpenRouterClient.Msg(it.role, it.content) }
            val res = OpenRouterClient.chat(text, history, lang.label)
            res.onSuccess { reply ->
                _chat.value = _chat.value + ChatMsg("assistant", reply, lang.label)
                MemoryDb.dao().insert(MemoryEntity(role = "assistant", content = reply, lang = lang.label))
                _status.value = "ready"
            }.onFailure { e ->
                val offline = offlineAnswer(text)
                _chat.value = _chat.value + ChatMsg("assistant", offline, lang.label)
                _status.value = "offline mode: ${e.message?.take(60)}"
            }
            _busy.value = false
        }
    }

    /** Offline fallback: device control / automation / builder keep working. */
    private fun offlineAnswer(text: String): String {
        val low = text.lowercase()
        return when {
            low.startsWith("open ") -> {
                val name = low.removePrefix("open ").trim()
                val pkg = DeviceControlHub.installedApps()
                    .firstOrNull { it.substringAfterLast('.').contains(name.take(6), true) }
                if (pkg != null && DeviceControlHub.launchApp(pkg)) "Opening $name."
                else "App not found: $name"
            }
            "battery" in low -> DeviceControlHub.batteryInfo()
            "storage" in low -> DeviceControlHub.storageInfo()
            low.startsWith("remind") || low.startsWith("at ") -> {
                val t = AutomationEngine.createFromText(text)
                "Automation created: ${t.name} [${t.trigger}]"
            }
            low.startsWith("build ") || low.startsWith("create app") -> {
                val n = AppBuilderPro.generate(text)
                "Project generated: $n (offline builder)"
            }
            "flashlight" in low -> DeviceControlHub.toggleFlashlight()
            "volume" in low -> { DeviceControlHub.volumeUp(); "volume up" }
            else -> "Offline mode: internet ya API key check karo. Device tools phir bhi kaam karte hain — try: open youtube / battery / remind me at 8 pm call pakyji."
        }
    }

    fun clearChat() { _chat.value = emptyList() }

    fun refreshModels() { viewModelScope.launch { models.value = OpenRouterClient.models() } }

    fun summarizeNotifications(): String =
        com.thesuperagent.agent.services.NotificationReaderService.summarizeStatic()

    fun pushBuilder(project: String) {
        viewModelScope.launch { _status.value = AppBuilderPro.pushToGitHub(project) }
    }

    fun addNote(title: String, body: String) {
        viewModelScope.launch { AINotes.add(title, body) }
    }

    fun logLine(s: String) { viewModelScope.launch { LogStore.log("ui", s) } }

    val toolOut = MutableStateFlow("")
    fun translate(q: String) {
        viewModelScope.launch {
            toolOut.value = "translating…"
            toolOut.value = com.thesuperagent.agent.super12.LiveTranslate.translate(q)
        }
    }
    fun setToolOut(s: String) { toolOut.value = s }
}
