package com.thesuperagent.agent.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.thesuperagent.agent.automation.AutomationEngine
import com.thesuperagent.agent.core.LogStore
import kotlinx.coroutines.*

/**
 * Accessibility: app-open trigger for automations + GestureControl.
 * Config flags live in res/xml/accessibility_config.xml.
 */
class SuperAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SuperAccessibilityService? = null
            private set
        var lastApp = ""
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.launch { LogStore.log("accessibility", "service connected") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.isNotBlank() && pkg != lastApp) {
                lastApp = pkg
                scope.launch { AutomationEngine.onAppOpen(pkg) }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    fun swipe(dir: String): Boolean {
        val (x1, y1, x2, y2) = when (dir) {
            "up" -> floatArrayOf(540f, 1600f, 540f, 500f)
            "down" -> floatArrayOf(540f, 500f, 540f, 1600f)
            "left" -> floatArrayOf(900f, 1000f, 200f, 1000f)
            else -> floatArrayOf(200f, 1000f, 900f, 1000f)
        }
        return gesture(x1, y1, x2, y2, 300)
    }

    fun tap(x: Float, y: Float): Boolean = gesture(x, y, x, y, 60)

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms)
        return try {
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (t: Throwable) { false }
    }
}
