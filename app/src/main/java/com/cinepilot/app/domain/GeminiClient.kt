package com.cinepilot.app.domain

/**
 * Boundary for intent understanding.
 *
 * A production implementation calls Gemini and returns the model's raw JSON text.
 * Keeping the interface narrow means the rest of the app (and its tests) never depend
 * on a network client, and no API key is ever compiled into the APK: a real client
 * must obtain credentials from user input at runtime or from a backend proxy.
 *
 * Contract: [classifyIntent] returns JSON of the shape expected by [IntentParser],
 * or throws. It must not return a partially built object.
 */
interface GeminiClient {
    suspend fun classifyIntent(utterance: String): String

    /** Human-readable name shown in diagnostics, e.g. "mock". */
    val name: String
}

/**
 * Deterministic, offline stand-in used during development and tests.
 *
 * It performs simple keyword matching rather than pretending to understand language.
 * The output shape matches the real model contract so [IntentParser] is exercised
 * end-to-end without a key or network.
 */
class MockGeminiClient : GeminiClient {

    override val name: String = "mock"

    override suspend fun classifyIntent(utterance: String): String {
        val text = utterance.lowercase().trim()
        val original = utterance.trim()

        val (intent, query, title) = when {
            text.contains("play ") || text.startsWith("play") -> {
                // Find "play" in the lowercased text to get the index,
                // then extract from the original to preserve casing.
                val playIdx = text.indexOf("play")
                val t = if (playIdx >= 0) original.substring(playIdx + 4).trim() else null
                val resolved = if (t != null && !t.isBlank()) t else null
                Triple("PLAY_MOVIE", resolved, resolved)
            }
            text.contains("pause") || text.contains("resume") ->
                Triple("PAUSE", null, null)
            text.contains("stop") -> Triple("STOP", null, null)
            text.contains("home") -> Triple("NAVIGATE_HOME", null, null)
            text.contains("volume up") || text.contains("louder") ->
                Triple("CHANGE_VOLUME", "up", null)
            text.contains("volume down") || text.contains("quieter") ->
                Triple("CHANGE_VOLUME", "down", null)
            text.contains("search") || text.contains("find") ||
                text.contains("show me") || text.contains("look for") -> {
                val q = text
                    .removePrefix("search")
                    .removePrefix("find")
                    .removePrefix("show me")
                    .removePrefix("look for")
                    .removePrefix("for")
                    .trim()
                Triple("SEARCH_MOVIE", q.ifBlank { null }, null)
            }
            text.isNotBlank() -> Triple("SEARCH_MOVIE", text, null)
            else -> Triple("UNKNOWN", null, null)
        }

        return buildString {
            append("{")
            append("\"intentType\":\"$intent\",")
            append("\"query\":${jsonOrNull(query)},")
            append("\"movieTitle\":${jsonOrNull(title)},")
            append("\"serviceName\":null,")
            append("\"confidence\":${if (intent == "UNKNOWN") "0.0" else "0.8"}")
            append("}")
        }
    }

    private fun jsonOrNull(value: String?): String =
        if (value == null) "null" else "\"" + value.replace("\"", "\\\"") + "\""
}