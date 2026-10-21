package com.thesuperagent.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.thesuperagent.agent.core.MemoryDb
import com.thesuperagent.agent.core.SecurePrefs

/**
 * The Super Agent — application entry.
 * Identity: pure Super Agent (never JARVIS). Owner: pakyji.
 */
class SuperAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        SecurePrefs.init(this)
        MemoryDb.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            VOICE_CHANNEL,
            getString(R.string.channel_voice),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Voice Super Agent foreground service"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val VOICE_CHANNEL = "super_agent_voice_v4"
        lateinit var instance: SuperAgentApp
            private set
    }
}
