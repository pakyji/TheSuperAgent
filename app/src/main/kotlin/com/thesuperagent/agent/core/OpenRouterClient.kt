package com.thesuperagent.agent.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct chat calls from the device. No backend, no hardcoded keys.
 *
 * Providers:
 *  - "pollinations": free. Works with no key (keyless text endpoint) or with a
 *    free key from enter.pollinations.ai/keys (gen.pollinations.ai).
 *  - "openrouter": paid/credit-based, needs the OpenRouter key from Settings.
 */
object OpenRouterClient {

    data class Msg(val role: String, val content: String)

    private const val OPENROUTER_CHAT = "https://openrouter.ai/api/v1/chat/completions"
    private const val OPENROUTER_MODELS = "https://openrouter.ai/api/v1/models"
    private const val POLLI_KEYED_CHAT = "https://gen.pollinations.ai/v1/chat/completions"
    private const val POLLI_KEYLESS_CHAT = "https://text.pollinations.ai/openai"

    /** Models offered when the provider has no model list endpoint. */
    val POLLINATIONS_MODELS = listOf("openai", "mistral")

    private class Target(val url: String, val key: String, val model: String)

    private fun target(): Target = if (SecurePrefs.provider == "openrouter") {
        Target(OPENROUTER_CHAT, SecurePrefs.openRouterKey, SecurePrefs.model)
    } else {
        val k = SecurePrefs.pollinationsKey
        Target(if (k.isBlank()) POLLI_KEYLESS_CHAT else POLLI_KEYED_CHAT, k, SecurePrefs.pollModel)
    }

    suspend fun chat(
        userText: String,
        history: List<Msg>,
        languageHint: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val tg = target()
        if (SecurePrefs.provider == "openrouter" && tg.key.isBlank()) {
            return@withContext Result.failure(IllegalStateException(
                "NO_KEY: OpenRouter key nahi hai. Settings mein key daalo ya provider Pollinations (free) choose karo."))
        }
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content",
                SecurePrefs.personality + "\nRespond strictly in: " + languageHint))
            history.takeLast(10).forEach { m ->
                messages.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            messages.put(JSONObject().put("role", "user").put("content", userText))

            val body = JSONObject()
                .put("model", tg.model)
                .put("messages", messages)

            val conn = URL(tg.url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (tg.key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${tg.key}")
            conn.setRequestProperty("HTTP-Referer", "https://localhost")
            conn.setRequestProperty("X-Title", "The Super Agent")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = if (code in 200..299)
                conn.inputStream.bufferedReader().readText()
            else
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            if (code !in 200..299) return@withContext Result.failure(
                IllegalStateException("${SecurePrefs.provider} HTTP $code: ${text.take(200)}")
            )
            val json = JSONObject(text)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
            if (content.isBlank()) Result.failure(IllegalStateException("empty reply"))
            else Result.success(content)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun models(): List<String> = withContext(Dispatchers.IO) {
        if (SecurePrefs.provider != "openrouter") return@withContext POLLINATIONS_MODELS
        val key = SecurePrefs.openRouterKey
        if (key.isBlank()) return@withContext listOf("openai/gpt-4o-mini")
        try {
            val conn = URL(OPENROUTER_MODELS).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $key")
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONObject(text).getJSONArray("data")
            (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
                .filter { it.isNotBlank() }
        } catch (t: Throwable) {
            listOf("openai/gpt-4o-mini")
        }
    }
}
