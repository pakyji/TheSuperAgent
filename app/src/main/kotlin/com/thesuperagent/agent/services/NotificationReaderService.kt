package com.thesuperagent.agent.services

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.thesuperagent.agent.automation.AutomationEngine
import com.thesuperagent.agent.core.LogStore
import com.thesuperagent.agent.modules.WhatsAppAutoReply
import kotlinx.coroutines.*

data class NotificationItem(
    val pkg: String,
    val title: String,
    val text: String,
    val ts: Long,
)

/**
 * Notification reader / summarizer / speak-aloud source.
 * Android 14 crash fix: every extras access is null-guarded.
 */
class NotificationReaderService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        val recent = ArrayDeque<NotificationItem>()
        var connected = false
            private set

        fun summarizeStatic(count: Int = 10): String {
            val list = synchronized(recent) { recent.take(count).toList() }
            if (list.isEmpty()) return "No recent notifications."
            return list.joinToString("\n") { "- ${it.pkg.substringAfterLast('.')}: ${it.title} — ${it.text.take(60)}" }
        }
    }

    override fun onListenerConnected() {
        connected = true
        scope.launch { LogStore.log("notifications", "listener connected") }
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return   // Android 14 null-extras guard
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val item = NotificationItem(pkg, title, text, System.currentTimeMillis())
        synchronized(recent) {
            recent.addFirst(item)
            while (recent.size > 50) recent.removeLast()
        }
        scope.launch {
            LogStore.log("notification", "$pkg | $title | ${text.take(80)}")
            AutomationEngine.onNotification(item)
            WhatsAppAutoReply.onNotification(sbn, title, text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    fun summarize(count: Int = 10): String {
        val list = synchronized(recent) { recent.take(count).toList() }
        if (list.isEmpty()) return "No recent notifications."
        return list.joinToString("\n") { "- ${it.pkg.substringAfterLast('.')}: ${it.title} — ${it.text.take(60)}" }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
