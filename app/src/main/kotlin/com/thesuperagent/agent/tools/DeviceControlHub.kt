package com.thesuperagent.agent.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.thesuperagent.agent.SuperAgentApp
import com.thesuperagent.agent.automation.AlarmReceiver
import com.thesuperagent.agent.core.ClipEntity
import com.thesuperagent.agent.core.LogStore
import com.thesuperagent.agent.core.MemoryDb
import java.io.File
import java.net.URLEncoder

/**
 * Device Control Hub — 40+ tools. Everything runs through official APIs.
 */
object DeviceControlHub {

    private val ctx: Context get() = SuperAgentApp.instance

    /* ---------- apps / links / search ---------- */
    fun launchApp(pkg: String): Boolean = runCatching {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true
    }.getOrDefault(false)

    fun deepLink(uri: String): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun openUrl(url: String): Boolean =
        deepLink(if (url.startsWith("http")) url else "https://$url")

    fun webSearch(q: String): Boolean =
        openUrl("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))

    fun youtubeSearch(q: String): Boolean = runCatching {
        val i = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i); true
    }.getOrElse { openUrl("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")) }

    fun mapsNavigate(q: String): Boolean =
        deepLink("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(q, "UTF-8"))

    fun dial(number: String): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun sms(to: String, text: String): Boolean = runCatching {
        android.telephony.SmsManager.getDefault()
            .sendTextMessage(to, null, text, null, null); true
    }.getOrDefault(false)

    /* ---------- audio / display / hardware ---------- */
    fun setVolume(percent: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent.coerceIn(0, 100) / 100), 0)
    }

    fun volumeUp() = keyVolume(AudioManager.ADJUST_RAISE)
    fun volumeDown() = keyVolume(AudioManager.ADJUST_LOWER)
    private fun keyVolume(dir: Int) {
        ctx.getSystemService(AudioManager::class.java)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
    }

    fun setBrightness(value: Int): String = runCatching {
        if (!Settings.System.canWrite(ctx)) return "Brightness: WRITE_SETTINGS permission chahiye (Settings → Special app access)."
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255))
        "Brightness set to $value."
    }.getOrDefault("Brightness failed.")

    private var torchOn = false
    fun toggleFlashlight(): String = runCatching {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return "No camera service."
        val id = cm.cameraIdList.firstOrNull() ?: return "No camera."
        torchOn = !torchOn
        cm.setTorchMode(id, torchOn)
        if (torchOn) "Flashlight ON." else "Flashlight OFF."
    }.getOrDefault("Flashlight unavailable.")

    fun setDnd(on: Boolean): String = runCatching {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return "No NM."
        if (!nm.isNotificationPolicyAccessGranted) return "DND: notification policy access chahiye."
        // 2 = INTERRUPTION_FILTER_PRIORITY, 1 = INTERRUPTION_FILTER_ALL (compile-safe ints)
        nm.setInterruptionFilter(if (on) 2 else 1)
        if (on) "DND ON." else "DND OFF."
    }.getOrDefault("DND failed.")

    /* ---------- clipboard ---------- */
    fun clipboardCopy(text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("super", text))
    }

    fun clipboardText(): String =
        ctx.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.getItemAt(0)?.text?.toString().orEmpty()

    suspend fun clipboardSaveHistory() {
        val t = clipboardText()
        if (t.isNotBlank()) MemoryDb.dao().addClip(ClipEntity(text = t.take(500)))
    }

    fun clipboardClear() {
        ctx.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip()
    }

    /* ---------- contacts / calendar ---------- */
    fun searchContacts(query: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val cur: Cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null) ?: return out
        cur.use {
            while (it.moveToNext() && out.size < 10) {
                val name = it.getString(0).orEmpty()
                val num = it.getString(1).orEmpty()
                if (name.contains(query, true) || num.contains(query)) out.add(name to num)
            }
        }
        return out
    }

    fun calendarEventsNext(hours: Int = 24): List<String> {
        val out = mutableListOf<String>()
        val start = System.currentTimeMillis()
        val cur = ctx.contentResolver.query(
            CalendarContract.Instances.CONTENT_URI,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            null, null, null) ?: return out
        cur.use {
            while (it.moveToNext() && out.size < 10) {
                val begin = it.getLong(1)
                if (begin in start..start + hours * 3600_000L) out.add(it.getString(0))
            }
        }
        return out
    }

    fun calendarCreate(title: String, startMs: Long, durationMin: Int): Boolean = runCatching {
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, startMs + durationMin * 60_000L)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    }.getOrDefault(false)

    /* ---------- alarms ---------- */
    fun setAlarm(label: String, triggerMs: Long): String = runCatching {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return "No alarm service."
        val pi = PendingIntent.getBroadcast(ctx, label.hashCode(),
            Intent(ctx, AlarmReceiver::class.java).putExtra("msg", label),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
            return "Exact alarm permission chahiye (Settings → Special app access → Alarms)."
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        "Alarm set: $label"
    }.getOrDefault("Alarm failed.")

    fun openAlarmUi() = runCatching {
        ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    /* ---------- camera / qr ---------- */
    fun cameraCapture(): Boolean = runCatching {
        ctx.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun qrScan(): Boolean = runCatching {
        // Google Play Services scanner, graceful fallback to camera intent
        val i = Intent("com.google.android.gms.scan.ACTION_SCAN").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i); true
    }.getOrElse { cameraCapture() }

    /* ---------- battery / storage / usage ---------- */
    fun batteryInfo(): String {
        val b = ctx.registerReceiver(null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Battery unknown."
        val level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val temp = b.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
        val health = when (b.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)) {
            android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            else -> "ok"
        }
        val charging = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ==
            android.os.BatteryManager.BATTERY_STATUS_CHARGING
        return "Battery ${if (scale > 0) level * 100 / scale else -1}% | ${temp}°C | health $health | charging=$charging"
    }

    fun storageInfo(): String {
        val st = StatFs(Environment.getDataDirectory().path)
        val free = st.availableBytes / 1048576
        val total = st.totalBytes / 1048576
        return "Storage: $free MB free of $total MB."
    }

    fun appUsageTop(hours: Int = 24): List<String> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - hours * 3600_000L, now)
        }.getOrNull() ?: return emptyList()
        return stats.sortedByDescending { it.totalTimeInForeground }
            .take(10)
            .map { "${it.packageName.substringAfterLast('.')} (${it.totalTimeInForeground / 60000} min)" }
    }

    fun installedApps(): List<String> =
        ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .filter { !it.startsWith("com.android") }
            .sorted()

    /* ---------- files (sandbox workspace) ---------- */
    fun workspace(): File =
        File(ctx.filesDir, "super_agent_workspace").apply { mkdirs() }

    fun listWorkspace(): List<String> =
        workspace().walkTopDown().filter { it.isFile }.map { it.relativeTo(workspace()).path }.toList()

    fun readWorkspace(rel: String): String = runCatching {
        File(workspace(), rel).takeIf { it.canonicalPath.startsWith(workspace().canonicalPath) }
            ?.readText().orEmpty()
    }.getOrDefault("")

    fun writeWorkspace(rel: String, content: String): Boolean = runCatching {
        val f = File(workspace(), rel)
        if (!f.canonicalPath.startsWith(workspace().canonicalPath)) return false
        f.parentFile?.mkdirs()
        f.writeText(content); true
    }.getOrDefault(false)

    /* ---------- misc ---------- */
    suspend fun logTool(tag: String, msg: String) = LogStore.log(tag, msg)

    fun shareText(text: String): Boolean = runCatching {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun openSettings(page: String): Boolean = runCatching {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "notif" -> "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
            "access" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "overlay" -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            "usage" -> Settings.ACTION_USAGE_ACCESS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
