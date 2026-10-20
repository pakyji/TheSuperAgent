package com.thesuperagent.agent.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thesuperagent.agent.core.LogStore
import com.thesuperagent.agent.core.SecurePrefs
import com.thesuperagent.agent.services.NotificationItem
import com.thesuperagent.agent.tools.DeviceControlHub
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class TaskState { RUNNING, WAITING_PERMISSION, NEEDS_CONFIRMATION, COMPLETED, FAILED }
enum class Trigger { TIME, NOTIFICATION, APP_OPEN, BATTERY, CHARGING, HEADPHONES, LOCATION, MANUAL }

data class AutoTask(
    val id: String,
    val name: String,
    val trigger: Trigger,
    val condition: String,
    val action: String,
    var state: TaskState = TaskState.RUNNING,
    var attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Automation Engine V2 — natural-language creation, 7 triggers, 6 templates,
 * states, retry/timeout/cancel/recovery. Persisted as JSON in files dir.
 */
object AutomationEngine {

    val tasks = MutableStateFlow<List<AutoTask>>(emptyList())

    val templates = listOf(
        "Morning Routine" to "TIME|07:00|speak Good morning inayat, here is your day summary; open calendar",
        "Night Mode" to "TIME|23:00|set brightness 20; set dnd on",
        "Focus Mode" to "MANUAL||set dnd on; speak Focus mode on",
        "Drive Mode" to "APP_OPEN|com.google.android.maps|set dnd on; speak Driving mode active",
        "Study Timer" to "TIME|+25min|speak Study session complete, take a break",
        "Battery Saver" to "BATTERY|15|set brightness 30; speak Battery low, saver on",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Any()

    fun storeFile(): File =
        File(SuperAgentCtx.app().filesDir, "automations.json")

    fun load() {
        scope.launch {
            val list = mutableListOf<AutoTask>()
            runCatching {
                val arr = JSONArray(storeFile().readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(AutoTask(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        trigger = runCatching { Trigger.valueOf(o.optString("trigger")) }
                            .getOrDefault(Trigger.MANUAL),
                        condition = o.optString("condition"),
                        action = o.optString("action"),
                        state = runCatching { TaskState.valueOf(o.optString("state")) }
                            .getOrDefault(TaskState.RUNNING),
                    ))
                }
            }
            tasks.value = list
        }
    }

    private fun save() {
        synchronized(mutex) {
            runCatching {
                val arr = JSONArray()
                tasks.value.forEach { t ->
                    arr.put(JSONObject().apply {
                        put("id", t.id); put("name", t.name); put("trigger", t.trigger.name)
                        put("condition", t.condition); put("action", t.action)
                        put("state", t.state.name)
                    })
                }
                storeFile().writeText(arr.toString())
            }
        }
    }

    /** Natural-language creation: "At 8 PM remind me X", "When WhatsApp opens ...", "On charging ..." */
    fun createFromText(text: String): AutoTask {
        val lower = text.lowercase()
        val (trigger, condition) = when {
            Regex("at (\\d{1,2})(:(\\d{2}))?\\s*(am|pm)?").find(lower) != null -> {
                val m = Regex("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)!!
                var h = m.groupValues[1].toInt()
                val mm = m.groupValues[2].ifBlank { "0" }
                if (m.groupValues[3] == "pm" && h < 12) h += 12
                if (m.groupValues[3] == "am" && h == 12) h = 0
                Trigger.TIME to String.format("%02d:%s", h, mm)
            }
            "when i plug" in lower || "on charging" in lower || "charger" in lower -> Trigger.CHARGING to ""
            "headphone" in lower || "earphone" in lower -> Trigger.HEADPHONES to ""
            "battery" in lower -> Trigger.BATTERY to (Regex("(\\d{1,3})\\s*%").find(lower)?.groupValues?.get(1) ?: "15")
            "when i open" in lower || "opens" in lower -> {
                val pkg = DeviceControlHub.installedApps()
                    .firstOrNull { p -> p.substringAfterLast('.').contains(
                        Regex("open ([a-z]+)").find(lower)?.groupValues?.get(1).orEmpty(), true) }
                    .orEmpty()
                Trigger.APP_OPEN to pkg
            }
            "notification" in lower -> Trigger.NOTIFICATION to ""
            else -> Trigger.MANUAL to ""
        }
        val task = AutoTask(
            id = System.currentTimeMillis().toString(36),
            name = text.take(40),
            trigger = trigger,
            condition = condition,
            action = text,
        )
        tasks.value = tasks.value + task
        save()
        scope.launch {
            LogStore.log("automation", "created: ${task.name} [${task.trigger} ${task.condition}]")
            if (trigger == Trigger.TIME && condition.contains(":")) scheduleTime(task)
        }
        return task
    }

    fun delete(id: String) {
        tasks.value = tasks.value.filterNot { it.id == id }
        save()
    }

    fun runNow(id: String) {
        val t = tasks.value.firstOrNull { it.id == id } ?: return
        execute(t)
    }

    private fun scheduleTime(t: AutoTask) {
        val parts = t.condition.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis())
                add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        DeviceControlHub.setAlarm("auto:${t.id}", cal.timeInMillis)
    }

    /* -------- trigger callbacks -------- */
    fun onTime(taskId: String) {
        val t = tasks.value.firstOrNull { it.id == taskId } ?: return
        execute(t)
        if (t.condition.contains(":")) scheduleTime(t)   // daily recovery
    }

    fun onNotification(item: NotificationItem) {
        tasks.value.filter { it.trigger == Trigger.NOTIFICATION && it.state == TaskState.RUNNING }
            .forEach { t ->
                if (t.condition.isBlank() || item.pkg.contains(t.condition, true)
                    || item.title.contains(t.condition, true)) execute(t)
            }
    }

    fun onAppOpen(pkg: String) {
        tasks.value.filter { it.trigger == Trigger.APP_OPEN && it.condition == pkg
            && it.state == TaskState.RUNNING }.forEach { execute(it) }
    }

    fun onBattery(level: Int) {
        tasks.value.filter { it.trigger == Trigger.BATTERY && it.state == TaskState.RUNNING }
            .forEach { t -> if (level <= (t.condition.toIntOrNull() ?: 15)) execute(t) }
    }

    fun onCharging(charging: Boolean) {
        tasks.value.filter { it.trigger == Trigger.CHARGING && it.state == TaskState.RUNNING }
            .forEach { if (charging) execute(it) }
    }

    fun onHeadphones(connected: Boolean) {
        tasks.value.filter { it.trigger == Trigger.HEADPHONES && it.state == TaskState.RUNNING }
            .forEach { if (connected) execute(it) }
    }

    /* -------- execution with retry + timeout -------- */
    fun execute(t: AutoTask) {
        scope.launch {
            t.state = TaskState.RUNNING
            publish()
            val job = launch {
                withTimeoutOrNull(30_000) {
                    runActionString(t.action)
                } ?: run {
                    t.attempts++
                    if (t.attempts < 3) {
                        t.state = TaskState.FAILED
                        LogStore.log("automation", "timeout, retry ${t.attempts}: ${t.name}")
                        delay(2000)
                        execute(t)
                    } else t.state = TaskState.FAILED
                }
            }
            job.join()
            if (t.state == TaskState.RUNNING) t.state = TaskState.COMPLETED
            publish()
            save()
            LogStore.log("automation", "${t.state}: ${t.name}")
        }
    }

    private suspend fun runActionString(action: String) {
        val steps = action.split(';').map { it.trim() }.filter { it.isNotBlank() }
        for (s in steps) {
            val low = s.lowercase()
            when {
                low.startsWith("open ") -> DeviceControlHub.launchApp(
                    DeviceControlHub.installedApps()
                        .firstOrNull { it.substringAfterLast('.').contains(low.removePrefix("open ").take(6), true) }
                        .orEmpty())
                low.startsWith("set brightness") -> DeviceControlHub.setBrightness(
                    Regex("(\\d+)").find(low)?.value?.toIntOrNull() ?: 100)
                low.startsWith("set dnd on") -> DeviceControlHub.setDnd(true)
                low.startsWith("set dnd off") -> DeviceControlHub.setDnd(false)
                low.startsWith("speak ") -> com.thesuperagent.agent.voice.VoiceAnnouncer
                    .announce(s.removePrefix("speak ").removePrefix("Speak "))
                low.startsWith("search ") -> DeviceControlHub.webSearch(s.removePrefix("search "))
                else -> LogStore.log("automation", "unknown step: $s")
            }
        }
    }

    private fun publish() { tasks.value = tasks.value.toList() }
}

object SuperAgentCtx {
    fun app(): Context = com.thesuperagent.agent.SuperAgentApp.instance
}

/* ---------------- receivers ---------------- */

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val msg = intent.getStringExtra("msg").orEmpty()
        if (msg.startsWith("auto:")) {
            AutomationEngine.onTime(msg.removePrefix("auto:"))
        } else {
            com.thesuperagent.agent.voice.VoiceAnnouncer.announce(msg)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutomationEngine.load()
            AutomationEngine.tasks.value.filter { it.trigger == Trigger.TIME }
                .forEach { AutomationEngine.onTime(it.id) }
        }
    }
}

class HeadphoneReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val connected = when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> intent.getIntExtra("state", 0) == 1
            else -> true
        }
        AutomationEngine.onHeadphones(connected)
    }
}

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> AutomationEngine.onBattery(15)
            Intent.ACTION_BATTERY_OKAY -> {}
            Intent.ACTION_POWER_CONNECTED -> AutomationEngine.onCharging(true)
            Intent.ACTION_POWER_DISCONNECTED -> AutomationEngine.onCharging(false)
        }
    }
}
