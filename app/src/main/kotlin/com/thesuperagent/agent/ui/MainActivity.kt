package com.thesuperagent.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thesuperagent.agent.automation.AutomationEngine

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCorePermissions()
        setContent {
            MaterialTheme(colorScheme = superColors()) {
                SuperAgentScreen(viewModel())
            }
        }
    }

    private fun requestCorePermissions() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.READ_MEDIA_IMAGES
        }
        val need = list.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
        AutomationEngine.load()
    }
}

private val TABS = listOf(
    "Chat", "Voice", "Automations", "App Builder", "Device",
    "Files", "Tools", "Memory", "Logs", "Settings"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuperAgentScreen(vm: AgentViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Bg,
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedOrb(active = vm.busy.value)
                    Column(Modifier.weight(1f)) {
                        Text("The Super Agent", color = Cyan,
                            style = MaterialTheme.typography.titleMedium)
                        Text(vm.status.value, color = TextDim,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    containerColor = Bg,
                    contentColor = Cyan,
                    edgePadding = 8.dp,
                ) {
                    TABS.forEachIndexed { i, name ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(name, color = if (tab == i) Cyan else TextDim) },
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> ChatTab(vm)
                1 -> VoiceTab(vm)
                2 -> AutomationsTab(vm)
                3 -> AppBuilderTab(vm)
                4 -> DeviceTab(vm)
                5 -> FilesTab(vm)
                6 -> ToolsTab(vm)
                7 -> MemoryTab(vm)
                8 -> LogsTab(vm)
                9 -> SettingsTab(vm)
            }
        }
    }
}
