package com.thesuperagent.agent.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences (AES256-GCM) with a custom MasterKey alias and a
 * plain-prefs fallback for the Redmi 15C keystore bug. No key ever hardcoded.
 */
object SecurePrefs {
    private const val FILE = "super_agent_secure"
    private const val FALLBACK = "super_agent_fallback"
    private const val ALIAS = "super_agent_masterkey_v1"

    private lateinit var prefs: SharedPreferences
    var usingFallback = false
        private set

    fun init(ctx: Context) {
        prefs = try {
            val master = MasterKey.Builder(ctx, ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx, FILE, master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            usingFallback = true
            ctx.getSharedPreferences(FALLBACK, Context.MODE_PRIVATE)
        }
    }

    var openRouterKey: String
        get() = prefs.getString("or_key", "") ?: ""
        set(v) = prefs.edit().putString("or_key", v).apply()

    var model: String
        get() = prefs.getString("model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
        set(v) = prefs.edit().putString("model", v).apply()

    var personality: String
        get() = prefs.getString("personality", DEFAULT_PERSONALITY) ?: DEFAULT_PERSONALITY
        set(v) = prefs.edit().putString("personality", v).apply()

    var githubToken: String
        get() = prefs.getString("gh_token", "") ?: ""
        set(v) = prefs.edit().putString("gh_token", v).apply()

    var githubRepo: String
        get() = prefs.getString("gh_repo", "") ?: ""
        set(v) = prefs.edit().putString("gh_repo", v).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice", false)
        set(v) = prefs.edit().putBoolean("voice", v).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake", true)
        set(v) = prefs.edit().putBoolean("wake", v).apply()

    fun bool(key: String, def: Boolean = false): Boolean = prefs.getBoolean(key, def)
    fun setBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    fun string(key: String, def: String = ""): String = prefs.getString(key, def) ?: def
    fun setString(key: String, v: String) = prefs.edit().putString(key, v).apply()

    const val DEFAULT_PERSONALITY =
        "You are The Super Agent, ultimate Android assistant for inayat, Redmi 15C. Respond in user's language."
}
