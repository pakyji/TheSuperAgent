package com.thesuperagent.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thesuperagent.agent.core.SecurePrefs
import com.thesuperagent.agent.super12.*
import com.thesuperagent.agent.tools.DeviceControlHub
import kotlinx.coroutines.launch

/* ================= 6. FILES ================= */
@Composable
fun FilesTab(vm: AgentViewModel) {
    var files by remember { mutableStateOf(DeviceControlHub.listWorkspace()) }
    var selected by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("Sandbox Workspace", color = Cyan)
            Text("/files/super_agent_workspace/", color = TextDim)
            Row {
                ActionButton("Refresh", { files = DeviceControlHub.listWorkspace() })
                ActionButton("New file", {
                    DeviceControlHub.writeWorkspace("new-${System.currentTimeMillis() % 1000}.txt", "")
                    files = DeviceControlHub.listWorkspace()
                })
            }
        }
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f)) {
                items(files) { f ->
                    TextButton(onClick = {
                        selected = f
                        content = DeviceControlHub.readWorkspace(f)
                    }) { Text(f, color = if (f == selected) Cyan else TextMain) }
                }
            }
            Column(Modifier.weight(1f).padding(4.dp)) {
                Text(selected, color = Cyan)
                OutlinedTextField(
                    content, { content = it }, Modifier.fillMaxSize(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                        focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                )
            }
        }
        Row {
            ActionButton("Save", {
                if (selected.isNotBlank()) {
                    DeviceControlHub.writeWorkspace(selected, content)
                    files = DeviceControlHub.listWorkspace()
                }
            })
            ActionButton("Encrypt", { if (selected.isNotBlank()) FileVault.encryptFile(selected) })
            ActionButton("Decrypt", { if (selected.isNotBlank()) FileVault.decryptFile(selected) })
        }
    }
}

/* ================= 7. TOOLS ================= */
@Composable
fun ToolsTab(vm: AgentViewModel) {
    var out by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    val tOut by vm.toolOut.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("Super Tools v4–v5", color = Cyan)
            Text(out, color = TextMain)
            Text(tOut, color = TextDim)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("query / text…", color = TextDim) })
            FlowRowSafe {
                ActionButton("SmartBattery", { out = SmartBattery.report() })
                ActionButton("ScreenTime", { out = ScreenTimeDashboard.top().joinToString("\n") })
                ActionButton("Translate", { vm.translate(q) })
                ActionButton("Gallery search", { out = SmartGallery.searchPhotos(q).joinToString("\n").ifBlank { "no match" } })
                ActionButton("CallScreener", { out = if (CallScreener.isSpam(q)) "SPAM risk" else "looks safe" })
                ActionButton("AppLock list", { out = AppLock.lockedApps.joinToString(", ").ifBlank { "none locked" } })
                ActionButton("Lock app", { if (q.isNotBlank()) { AppLock.lock(q); out = "locked $q" } })
                ActionButton("Unlock app", { if (q.isNotBlank()) { AppLock.unlock(q); out = "unlocked $q" } })
                ActionButton("Drive mode ON", { DriveMode.activate(); out = "drive mode on" })
                ActionButton("Drive mode OFF", { DriveMode.deactivate(); out = "drive mode off" })
                ActionButton("Sleep apply", { SleepMode.apply(); out = "sleep mode applied (${SleepMode.bedtime}-${SleepMode.wakeTime})" })
                ActionButton("Gesture next", { out = GestureControl.handle("swipe_right") })
                ActionButton("PrivacyShield", {
                    out = PrivacyShield.activateKillSwitch(ctx)
                })
            }
        }
    }
}

/* ================= 8. MEMORY ================= */
@Composable
fun MemoryTab(vm: AgentViewModel) {
    val memories by vm.memories.collectAsState(initial = emptyList())
    val notes by vm.notes.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("Memory + AINotes", color = Cyan)
            Text("Personality:", color = TextDim)
            var pers by remember { mutableStateOf(SecurePrefs.personality) }
            OutlinedTextField(pers, { pers = it; SecurePrefs.personality = it },
                Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder))
            Row {
                OutlinedTextField(title, { title = it }, Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                        focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                    placeholder = { Text("note title", color = TextDim) })
                ActionButton("Add note", { if (title.isNotBlank()) { vm.addNote(title, body); title = ""; body = "" } })
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(notes) { n ->
                PanelBox {
                    Text(n.title, color = Cyan)
                    Text(n.body, color = TextMain)
                }
            }
            items(memories.take(30)) { m ->
                PanelBox {
                    Text("${m.role} · ${m.lang}", color = TextDim)
                    Text(m.content.take(200), color = TextMain)
                }
            }
        }
    }
}

/* ================= 9. LOGS ================= */
@Composable
fun LogsTab(vm: AgentViewModel) {
    val logs by vm.logs.collectAsState(initial = emptyList())
    LazyColumn(Modifier.padding(10.dp)) {
        items(logs) { l ->
            Text("[${l.tag}] ${l.message}", color = TextMain,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/* ================= 10. SETTINGS ================= */
@Composable
fun SettingsTab(vm: AgentViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var key by remember { mutableStateOf(SecurePrefs.openRouterKey) }
    var plKey by remember { mutableStateOf(SecurePrefs.pollinationsKey) }
    var provider by remember { mutableStateOf(SecurePrefs.provider) }
    var model by remember { mutableStateOf(SecurePrefs.activeModel) }
    var ghToken by remember { mutableStateOf(SecurePrefs.githubToken) }
    var ghRepo by remember { mutableStateOf(SecurePrefs.githubRepo) }
    val models by vm.models.collectAsState()
    var wake by remember { mutableStateOf(SecurePrefs.wakeWordEnabled) }
    Column(Modifier.padding(10.dp)) {
        PanelBox {
            Text("Settings", color = Cyan)
            Text("Provider: " + (if (provider == "openrouter") "OpenRouter" else "Pollinations (free)"),
                color = TextDim)
            Row {
                ActionButton("Pollinations (free)", {
                    provider = "pollinations"; SecurePrefs.provider = provider
                    model = SecurePrefs.activeModel; vm.refreshModels()
                })
                ActionButton("OpenRouter", {
                    provider = "openrouter"; SecurePrefs.provider = provider
                    model = SecurePrefs.activeModel; vm.refreshModels()
                })
            }
            Spacer(Modifier.height(4.dp))
            Text("Pollinations key (optional, FREE: enter.pollinations.ai/keys). Blank = keyless endpoint:",
                color = TextDim)
            OutlinedTextField(plKey, { plKey = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("sk_... (optional)", color = TextDim) })
            Row {
                ActionButton("Save Pollinations key", {
                    SecurePrefs.pollinationsKey = plKey
                    SecurePrefs.provider = "pollinations"; provider = "pollinations"
                    model = SecurePrefs.activeModel; vm.refreshModels()
                })
            }
            Spacer(Modifier.height(6.dp))
            Text("OpenRouter API key (EncryptedSharedPreferences AES256-GCM" +
                (if (SecurePrefs.usingFallback) " — FALLBACK prefs (Redmi keystore bug)" else "") + "):",
                color = TextDim)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder))
            Row {
                ActionButton("Save key", { SecurePrefs.openRouterKey = key; vm.refreshModels() })
            }
            Spacer(Modifier.height(6.dp))
            Text("Model (" + provider + "):", color = TextDim)
            FlowRowSafe {
                models.take(12).forEach { m ->
                    ActionButton(m.substringAfterLast('/'), { SecurePrefs.activeModel = m; model = m })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("Wake word \"Hey Super Agent\":", color = TextDim,
                    modifier = Modifier.weight(1f))
                Switch(wake, { wake = it; SecurePrefs.wakeWordEnabled = it })
            }
            Spacer(Modifier.height(6.dp))
            Text("GitHub (App Builder push):", color = TextDim)
            OutlinedTextField(ghToken, { ghToken = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("PAT token", color = TextDim) })
            OutlinedTextField(ghRepo, { ghRepo = it }, Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelBorder),
                placeholder = { Text("owner/repo", color = TextDim) })
            Row {
                ActionButton("Save GitHub", {
                    SecurePrefs.githubToken = ghToken; SecurePrefs.githubRepo = ghRepo
                })
                ActionButton("Permission guide", {
                    // opens HyperOS app settings page for this app
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + ctx.packageName)))
                    }
                })
            }
            Spacer(Modifier.height(6.dp))
            Text("HyperOS: Autostart ON · Battery No restrictions · Notification access ON · " +
                "Accessibility ON · Overlay ON · Usage access ON", color = TextDim)
        }
    }
}
