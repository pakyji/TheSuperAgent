package com.thesuperagent.agent.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct OpenRouter calls from device. No backend, no hardcoded keys.
 */
object OpenRouterClient {
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    data class Msg(val role: String, val content: String)

    suspend fun chat(
        userText: String,
        history: List<Msg>,
        languageHint: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = SecurePrefs.openRouterKey
        if (key.isBlank()) return@withContext Result.failure(
            IllegalStateException("NO_KEY: Settings tab mein OpenRouter API key paste karo.")
        )
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content",
                SecurePrefs.personality + "\nRespond strictly in: " + languageHint))
            history.takeLast(10).forEach { m ->
                messages.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            messages.put(JSONObject().put("role", "user").put("content", userText))

            val body = JSONObject()
                .put("model", SecurePrefs.model)
                .put("messages", messages)

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key")
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
                IllegalStateException("OpenRouter HTTP $code: ${text.take(200)}")
            )
            val json = JSONObject(text)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
            Result.success(content)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun models(): List<String> = withContext(Dispatchers.IO) {
        val key = SecurePrefs.openRouterKey
        if (key.isBlank()) return@withContext listOf("openai/gpt-4o-mini")
        try {
            val conn = URL("https://openrouter.ai/api/v1/models").openConnection() as HttpURLConnection
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
