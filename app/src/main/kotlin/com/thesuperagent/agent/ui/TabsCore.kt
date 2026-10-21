package com.thesuperagent.agent.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thesuperagent.agent.automation.AutomationEngine
import com.thesuperagent.agent.builder.AppBuilderPro
import com.thesuperagent.agent.core.SecurePrefs
import com.thesuperagent.agent.modules.SpotifySuperControl
import com.thesuperagent.agent.services.NotificationReaderService
import com.thesuperagent.agent.tools.DeviceControlHub
import com.thesuperagent.agent.voice.VoiceService

@Composable
fun PanelBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(10.dp)
            .background(Panel, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Cyan),
        modifier = Modifier.padding(2.dp),
    ) { Text(label) }
}

/* ================= 1. CHAT ================= */
@Composable
fun ChatTab(vm: AgentViewModel) {
    val chat by vm.chat.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(8.dp)) {
            items(chat) { m ->
                PanelBox {
                    Text(if (m.role == "user") "YOU" else "SUPER AGENT",
                        color = Cyan, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(m.content, color = TextMain)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Row(Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("Ask anything…", color = TextDim) },
            )
            Spacer(Modifier.width(6.dp))
            ActionButton(if (busy) "…" else "Send", {
                vm.send(input); input = ""
            }, enabled = !busy)
        }
    }
}

/* ================= 2. VOICE ================= */
@Composable
fun VoiceTab(vm: AgentViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var running by remember { mutableStateOf(VoiceServiceStart.running) }
    val levels = remember { List(16) { (kotlin.math.sin(it * 0.7f) + 1.2f) / 2.4f } }
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                AnimatedOrb(active = running)
                Column {
                    Text("Voice Super Agent", color = Cyan)
                    Text(if (running) "mic service live — say \"Hey Super Agent\""
                         else "service stopped", color = TextDim)
                }
            }
            Spacer(Modifier.height(8.dp))
            VoiceVisualizer(levels)
            Spacer(Modifier.height(8.dp))
            Row {
                ActionButton("Start", {
                    VoiceService.start(ctx); VoiceServiceStart.running = true; running = true
                })
                ActionButton("Pause", {
                    ctx.startService(Intent(ctx, VoiceService::class.java)
                        .setAction(VoiceService.ACTION_PAUSE))
                })
                ActionButton("Resume", {
                    ctx.startService(Intent(ctx, VoiceService::class.java)
                        .setAction(VoiceService.ACTION_RESUME))
                })
                ActionButton("Stop", {
                    ctx.startService(Intent(ctx, VoiceService::class.java)
                        .setAction(VoiceService.ACTION_STOP))
                    VoiceServiceStart.running = false; running = false
                })
            }
            Row {
                ActionButton("Push-to-talk", {
                    ctx.startService(Intent(ctx, VoiceService::class.java)
                        .setAction(VoiceService.ACTION_PTT))
                })
            }
            Spacer(Modifier.height(6.dp))
            Text("mic privacy: notification shows 🔴 MIC LIVE jab mic khula ho", color = TextDim)
        }
        PanelBox {
            Text("Spotify Super Control", color = Cyan)
            val track by vm.track.collectAsState()
            Text(track?.let { "${it.title} — ${it.artist}" } ?: "no active session",
                color = TextMain)
            Row {
                ActionButton("⟲", { SpotifySuperControl.prev() })
                ActionButton("▶/⏸", {
                    val t = SpotifySuperControl.currentTrack.value
                    if (t?.isPlaying == true) SpotifySuperControl.pause() else SpotifySuperControl.play()
                })
                ActionButton("⟳", { SpotifySuperControl.next() })
                ActionButton("Refresh", { SpotifySuperControl.refresh() })
            }
            var q by remember { mutableStateOf("") }
            Row {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                        focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                    placeholder = { Text("playlist / song…", color = TextDim) })
                ActionButton("Play", { SpotifySuperControl.playPlaylist(q) })
            }
        }
    }
}

object VoiceServiceStart { var running = false }

/* ================= 3. AUTOMATIONS ================= */
@Composable
fun AutomationsTab(vm: AgentViewModel) {
    val tasks by vm.tasks.collectAsState()
    var nl by remember { mutableStateOf("") }
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("Automation Engine V2", color = Cyan)
            OutlinedTextField(nl, { nl = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("At 8 PM remind me to call pakyji…", color = TextDim) })
            Row {
                ActionButton("Create", {
                    if (nl.isNotBlank()) { AutomationEngine.createFromText(nl); nl = "" }
                })
            }
            Spacer(Modifier.height(6.dp))
            Text("Templates:", color = TextDim)
            FlowRowSafe {
                AutomationEngine.templates.forEach { (name, _) ->
                    ActionButton(name, { AutomationEngine.createFromText(templateToNl(name)) })
                }
            }
        }
        LazyColumn {
            items(tasks) { t ->
                PanelBox {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = TextMain)
                            Text("${t.trigger} ${t.condition} · ${t.state}", color = TextDim)
                        }
                        ActionButton("Run", { AutomationEngine.runNow(t.id) })
                        ActionButton("✕", { AutomationEngine.delete(t.id) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun FlowRowSafe(content: @Composable () -> Unit) {
    Column { content() }
}

private fun templateToNl(name: String): String = when (name) {
    "Morning Routine" -> "At 7:00 AM speak Good morning pakyji, here is your day summary"
    "Night Mode" -> "At 11:00 PM set brightness 20; set dnd on"
    "Focus Mode" -> "set dnd on; speak Focus mode on"
    "Drive Mode" -> "when i open maps set dnd on; speak Driving mode active"
    "Study Timer" -> "At +25min speak Study session complete, take a break"
    else -> "battery 15 set brightness 30; speak Battery low, saver on"
}

/* ================= 4. APP BUILDER ================= */
@Composable
fun AppBuilderTab(vm: AgentViewModel) {
    var prompt by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    val logs by vm.builderLogs.collectAsState()
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("App Builder Pro", color = Cyan)
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("A notes app with dark theme and cloud sync…", color = TextDim) })
            Row {
                ActionButton("Generate", {
                    if (prompt.isNotBlank()) {
                        last = AppBuilderPro.generate(prompt)
                        vm.builderLogs.value = synchronized(AppBuilderPro.logs) {
                            AppBuilderPro.logs.toList().map { "${if (it.ok) "✓" else "✗"} ${it.line}" }
                        }
                    }
                })
                ActionButton("ZIP", { if (last.isNotBlank()) AppBuilderPro.zipProject(last) })
                ActionButton("Push GitHub", { if (last.isNotBlank()) vm.pushBuilder(last) })
            }
            Spacer(Modifier.height(4.dp))
            AppBuilderPro.suggestions(prompt).forEach { Text("• $it", color = TextDim) }
        }
        LazyColumn {
            items(logs) { l -> Text(l, color = TextMain, modifier = Modifier.padding(2.dp)) }
        }
    }
}

/* ================= 5. DEVICE ================= */
@Composable
fun DeviceTab(vm: AgentViewModel) {
    var out by remember { mutableStateOf("") }
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("Device Control Hub", color = Cyan)
            Text(out, color = TextMain)
            FlowRowSafe {
                ActionButton("Battery", { out = DeviceControlHub.batteryInfo() })
                ActionButton("Storage", { out = DeviceControlHub.storageInfo() })
                ActionButton("Flashlight", { out = DeviceControlHub.toggleFlashlight() })
                ActionButton("DND ON", { out = DeviceControlHub.setDnd(true) })
                ActionButton("DND OFF", { out = DeviceControlHub.setDnd(false) })
                ActionButton("Vol +", { DeviceControlHub.volumeUp(); out = "vol up" })
                ActionButton("Vol -", { DeviceControlHub.volumeDown(); out = "vol down" })
                ActionButton("Brightness 30", { out = DeviceControlHub.setBrightness(77) })
                ActionButton("Open YouTube", {
                    out = if (DeviceControlHub.launchApp("com.google.android.youtube")) "opened" else "not installed"
                })
                ActionButton("Open WhatsApp", {
                    out = if (DeviceControlHub.launchApp("com.whatsapp")) "opened" else "not installed"
                })
                ActionButton("Search web", { out = if (DeviceControlHub.webSearch("super agent")) "searching" else "fail" })
                ActionButton("YT search", { out = if (DeviceControlHub.youtubeSearch("lofi")) "searching" else "fail" })
                ActionButton("Maps", { out = if (DeviceControlHub.mapsNavigate("bologna")) "navigating" else "fail" })
                ActionButton("Camera", { out = if (DeviceControlHub.cameraCapture()) "camera" else "fail" })
                ActionButton("QR", { out = if (DeviceControlHub.qrScan()) "scanner" else "fail" })
                ActionButton("Notif summary", { out = NotificationReaderService.summarizeStatic() })
                ActionButton("Usage top", { out = DeviceControlHub.appUsageTop().joinToString("\n") })
                ActionButton("Contacts", { out = DeviceControlHub.searchContacts("a").joinToString("\n") { "${it.first} ${it.second}" } })
                ActionButton("Calendar", { out = DeviceControlHub.calendarEventsNext().joinToString("\n").ifBlank { "no events next 24h" } })
                ActionButton("Clipboard copy", { DeviceControlHub.clipboardCopy("Super Agent!"); out = "copied" })
                ActionButton("Clipboard read", { out = DeviceControlHub.clipboardText() })
            }
        }
    }
}
