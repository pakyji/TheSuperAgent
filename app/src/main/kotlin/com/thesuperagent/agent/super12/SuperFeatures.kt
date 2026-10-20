package com.thesuperagent.agent.super12

import android.content.Context
import android.provider.MediaStore
import com.thesuperagent.agent.SuperAgentApp
import com.thesuperagent.agent.core.MemoryDb
import com.thesuperagent.agent.core.NoteEntity
import com.thesuperagent.agent.core.SecurePrefs
import com.thesuperagent.agent.tools.DeviceControlHub
import com.thesuperagent.agent.voice.VoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/* ---------- 1. SmartBattery ---------- */
object SmartBattery {
    fun report(): String = DeviceControlHub.batteryInfo()
}

/* ---------- 2. AppLock ---------- */
object AppLock {
    var lockedApps: MutableSet<String>
        get() = SecurePrefs.string("locked_apps", "").split(',').filter { it.isNotBlank() }.toMutableSet()
        set(v) = SecurePrefs.setString("locked_apps", v.joinToString(","))

    fun lock(pkg: String) { lockedApps = (lockedApps + pkg).toMutableSet() }
    fun unlock(pkg: String) { lockedApps = (lockedApps - pkg).toMutableSet() }
    fun isLocked(pkg: String): Boolean = pkg in lockedApps

    /** Uses UsageStats to know if a locked app is in foreground. */
    fun foregroundPackage(): String {
        val top = DeviceControlHub.appUsageTop(1).firstOrNull().orEmpty()
        return top.substringBefore(" (")
    }
}

/* ---------- 3. FileVault (AES file encryption) ---------- */
object FileVault {
    private fun keyBytes(): ByteArray {
        val hex = SecurePrefs.string("vault_key", "")
        return if (hex.length == 32) hex.toByteArray()
        else {
            val k = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
            SecurePrefs.setString("vault_key", k)
            k.toByteArray()
        }
    }

    fun encryptFile(rel: String): String = runCatching {
        val f = File(DeviceControlHub.workspace(), rel)
        if (!f.exists()) return "File not found: $rel"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes(), "AES"))
        val out = File(f.parentFile, f.name + ".vault")
        out.writeBytes(cipher.doFinal(f.readBytes()))
        "Encrypted: ${out.name}"
    }.getOrDefault("Encryption failed.")

    fun decryptFile(rel: String): String = runCatching {
        val f = File(DeviceControlHub.workspace(), rel)
        if (!f.exists()) return "File not found: $rel"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes(), "AES"))
        val out = File(f.parentFile, f.name.removeSuffix(".vault"))
        out.writeBytes(cipher.doFinal(f.readBytes()))
        "Decrypted: ${out.name}"
    }.getOrDefault("Decryption failed.")
}

/* ---------- 4. AINotes ---------- */
object AINotes {
    val notes = MutableStateFlow<List<NoteEntity>>(emptyList())

    suspend fun refresh() {
        // collected in ViewModel via Flow; helper for quick add
    }

    suspend fun add(title: String, body: String) =
        MemoryDb.dao().addNote(NoteEntity(title = title, body = body))

    suspend fun delete(id: Long) = MemoryDb.dao().delNote(id)
}

/* ---------- 5. ScreenTimeDashboard ---------- */
object ScreenTimeDashboard {
    fun top(): List<String> = DeviceControlHub.appUsageTop(24)
}

/* ---------- 6. CallScreener ---------- */
object CallScreener {
    private val spamMarkers = listOf("spam", "scam", "fraud", "lottery", "prize", "loan")
    fun isSpam(number: String, label: String = ""): Boolean {
        val known = DeviceControlHub.searchContacts(number)
        if (known.isNotEmpty()) return false
        val low = label.lowercase()
        return spamMarkers.any { low.contains(it) } ||
            number.length > 12 ||
            SecurePrefs.string("spam_list", "").split(',').any { it.isNotBlank() && number.contains(it) }
    }
}

/* ---------- 7. LiveTranslate (free MyMemory endpoint) ---------- */
object LiveTranslate {
    suspend fun translate(text: String, from: String = "auto", to: String = "en"): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val src = if (from == "auto")
                    (if (text.any { it in '؀'.. 'ۿ' }) "ur" else if (from == "auto" && isItalian(text)) "it" else "en")
                else from
                val url = "https://api.mymemory.translated.net/get?q=" +
                    java.net.URLEncoder.encode(text.take(450), "UTF-8") +
                    "&langpair=$src|$to"
                val conn = URL(url).openConnection() as java.net.HttpURLConnection
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                JSONObject(body).getJSONObject("responseData").optString("translatedText", text)
            }.getOrDefault(text)
        }

    private fun isItalian(t: String) =
        t.lowercase().split(' ').count { it in listOf("come", "sono", "grazie", "perche", "dimmi") } >= 2
}

/* ---------- 8. GestureControl ---------- */
object GestureControl {
    var enabled: Boolean
        get() = SecurePrefs.bool("gestures", true)
        set(v) = SecurePrefs.setBool("gestures", v)

    private val map = mapOf(
        "swipe_right" to "next_song",
        "swipe_left" to "prev_song",
        "swipe_up" to "play_pause",
        "swipe_down" to "stop_music",
    )

    fun handle(gesture: String): String {
        if (!enabled) return "gestures off"
        return when (map[gesture]) {
            "next_song" -> { com.thesuperagent.agent.modules.SpotifySuperControl.next(); "next" }
            "prev_song" -> { com.thesuperagent.agent.modules.SpotifySuperControl.prev(); "prev" }
            "play_pause" -> {
                val t = com.thesuperagent.agent.modules.SpotifySuperControl.currentTrack.value
                if (t?.isPlaying == true) com.thesuperagent.agent.modules.SpotifySuperControl.pause()
                else com.thesuperagent.agent.modules.SpotifySuperControl.play()
                "play/pause"
            }
            "stop_music" -> { com.thesuperagent.agent.modules.SpotifySuperControl.pause(); "stopped" }
            else -> "unmapped"
        }
    }
}

/* ---------- 9. DriveMode ---------- */
object DriveMode {
    var active: Boolean = false
    var autoDetect: Boolean
        get() = SecurePrefs.bool("drive_auto", true)
        set(v) = SecurePrefs.setBool("drive_auto", v)

    fun activate() {
        active = true
        DeviceControlHub.setDnd(true)
        com.thesuperagent.agent.voice.VoiceAnnouncer.announce("Drive mode active. Notifications silenced.")
    }

    fun deactivate() {
        active = false
        DeviceControlHub.setDnd(false)
    }
}

/* ---------- 10. SleepMode ---------- */
object SleepMode {
    var bedtime: String
        get() = SecurePrefs.string("bedtime", "23:00")
        set(v) = SecurePrefs.setString("bedtime", v)
    var wakeTime: String
        get() = SecurePrefs.string("waketime", "07:00")
        set(v) = SecurePrefs.setString("waketime", v)

    fun isActiveNow(): Boolean {
        val now = java.util.Calendar.getInstance()
        val cur = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val b = parse(bedtime); val w = parse(wakeTime)
        return if (b <= w) cur in b until w else cur >= b || cur < w
    }

    private fun parse(s: String): Int {
        val p = s.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 23) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun apply() {
        if (isActiveNow()) {
            DeviceControlHub.setBrightness(30)
            DeviceControlHub.setDnd(true)
        }
    }
}

/* ---------- 11. SmartGallery ---------- */
object SmartGallery {
    fun searchPhotos(query: String): List<String> {
        val out = mutableListOf<String>()
        val cur = SuperAgentApp.instance.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            null, null, null) ?: return out
        cur.use {
            while (it.moveToNext() && out.size < 30) {
                val n = it.getString(0).orEmpty()
                if (n.contains(query, true)) out.add(n)
            }
        }
        return out
    }
}

/* ---------- 12. PrivacyShield ---------- */
object PrivacyShield {
    fun activateKillSwitch(ctx: Context): String {
        ctx.stopService(android.content.Intent(ctx, VoiceService::class.java))
        DeviceControlHub.clipboardClear()
        SecurePrefs.setBool("shield", true)
        return "PrivacyShield: voice service stopped, clipboard cleared."
    }

    fun disarm() = SecurePrefs.setBool("shield", false)
    val armed: Boolean get() = SecurePrefs.bool("shield", false)
}
