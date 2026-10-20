package com.thesuperagent.agent.core

/**
 * Auto language detection: Urdu (اردو) / Italian / English.
 * Drives STT locale, TTS locale and the model's reply-language hint.
 */
object LanguageDetector {
    enum class Lang(val label: String, val stt: String, val tts: String) {
        URDU("Urdu", "ur-PK", "ur-PK"),
        ITALIAN("Italian", "it-IT", "it-IT"),
        ENGLISH("English", "en-US", "en-US"),
    }

    private val ITALIAN_WORDS = setOf(
        "come", "sono", "perche", "grazie", "prego", "buongiorno", "buonasera",
        "apri", "chiudi", "dimmi", "cosa", "quando", "dove", "andiamo", "vai"
    )
    private val URDU_ROMAN_WORDS = setOf(
        "karo", "karna", "kya", "kaisa", "hai", "hain", "nahi", "nhn", "haan", "bolo",
        "batao", "chahiye", "mujhe", "tum", "apk", "theek", "acha", "shukriya", "salam"
    )

    fun detect(text: String): Lang {
        val t = text.trim()
        if (t.isEmpty()) return Lang.ENGLISH
        // Arabic script => Urdu
        val arabic = t.count { it in '؀'..'ۿ' }
        if (arabic * 2 > t.length) return Lang.URDU
        val words = t.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return Lang.ENGLISH
        val itHits = words.count { it.trimEnd('.', '!', '?', ',') in ITALIAN_WORDS }
        val urHits = words.count { it.trimEnd('.', '!', '?', ',') in URDU_ROMAN_WORDS }
        return when {
            urHits >= 1 && urHits >= itHits -> Lang.URDU
            itHits >= 2 || (itHits == 1 && words.size <= 3) -> Lang.ITALIAN
            else -> Lang.ENGLISH
        }
    }
}
