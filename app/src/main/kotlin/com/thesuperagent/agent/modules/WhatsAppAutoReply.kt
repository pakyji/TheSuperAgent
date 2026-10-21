package com.thesuperagent.agent.modules

import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import com.thesuperagent.agent.core.LogStore
import com.thesuperagent.agent.core.SecurePrefs
import com.thesuperagent.agent.services.NotificationReaderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WhatsApp auto-reply — OFFICIAL API only.
 * NotificationListenerService + RemoteInput direct reply. No root, no hacks.
 * Respects WhatsApp sandbox: com.whatsapp and com.whatsapp.w4b only.
 */
object WhatsAppAutoReply {

    enum class OnlyWhen { ALWAYS, SCREEN_OFF, DRIVING }

    var enabled: Boolean
        get() = SecurePrefs.bool("wa_enabled", false)
        set(v) = SecurePrefs.setBool("wa_enabled", v)

    var replyTemplate: String
        get() = SecurePrefs.string("wa_template",
            "Hi {sender}! I'm busy right now — The Super Agent will make sure pakyji replies soon. ({time})")
        set(v) = SecurePrefs.setString("wa_template", v)

    var onlyWhen: OnlyWhen
        get() = runCatching { OnlyWhen.valueOf(SecurePrefs.string("wa_when", "ALWAYS")) }
            .getOrDefault(OnlyWhen.ALWAYS)
        set(v) = SecurePrefs.setString("wa_when", v.name)

    var allowedContacts: MutableSet<String>
        get() = SecurePrefs.string("wa_contacts", "").split(',')
            .filter { it.isNotBlank() }.toMutableSet()
        set(v) = SecurePrefs.setString("wa_contacts", v.joinToString(","))

    var keywords: MutableList<String>
        get() = SecurePrefs.string("wa_keywords", "").split(',')
            .filter { it.isNotBlank() }.toMutableList()
        set(v) = SecurePrefs.setString("wa_keywords", v.joinToString(","))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    /** Group title detection: WhatsApp posts group name as title, sender inside text. */
    fun senderOf(title: String, text: String): String {
        val colon = text.indexOf(':')
        return if (colon in 1..40) text.substring(0, colon).trim() else title.trim()
    }

    fun shouldReply(pkg: String, title: String, text: String): Boolean {
        if (!enabled) return false
        if (pkg !in WA_PACKAGES) return false                     // sandbox respect
        if (!NotificationReaderService.connected) return false
        val sender = senderOf(title, text)
        if (sender.isBlank()) return false
        val allowed = allowedContacts
        if (allowed.isNotEmpty() && sender !in allowed) return false
        val kws = keywords
        if (kws.isNotEmpty() && kws.none { text.contains(it, true) }) return false
        return when (onlyWhen) {
            OnlyWhen.ALWAYS -> true
            OnlyWhen.SCREEN_OFF -> !SuperAgentScreen.isScreenOn()
            OnlyWhen.DRIVING -> SuperAgentScreen.isDriving()
        }
    }

    fun onNotification(sbn: StatusBarNotification, title: String, text: String) {
        if (!shouldReply(sbn.packageName ?: return, title, text)) return
        performAutoReply(sbn, senderOf(title, text))
    }

    /** Official direct-reply path: RemoteInput.addResultsToIntent + actionIntent.send */
    fun performAutoReply(sbn: StatusBarNotification, sender: String) {
        scope.launch {
            try {
                val actions = sbn.notification?.actions ?: return@launch
                val reply = replyTemplate
                    .replace("{sender}", sender)
                    .replace("{time}", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                for (action in actions) {
                    val ris = android.app.Notification.getRemoteInputs(action) ?: continue
                    val results = android.os.Bundle()
                    ris.forEach { ri -> results.putString(ri.resultKey, reply) }
                    val intent = Intent()
                    RemoteInput.addResultsToIntent(ris, intent, results)
                    action.actionIntent?.send(
                        com.thesuperagent.agent.SuperAgentApp.instance, 0, intent)
                    LogStore.log("whatsapp", "auto-replied to $sender: ${reply.take(60)}")
                    return@launch
                }
                LogStore.log("whatsapp", "no direct-reply action found for $sender")
            } catch (t: Throwable) {
                LogStore.log("whatsapp", "reply failed: ${t.message}")
            }
        }
    }
}

/** Small helper so WhatsAppAutoReply stays single-purpose. */
object SuperAgentScreen {
    fun isScreenOn(): Boolean =
        com.thesuperagent.agent.SuperAgentApp.instance
            .getSystemService(android.os.PowerManager::class.java)
            ?.isInteractive ?: true

    fun isDriving(): Boolean =
        com.thesuperagent.agent.super12.DriveMode.active
}
