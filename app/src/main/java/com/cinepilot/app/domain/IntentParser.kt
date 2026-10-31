package com.cinepilot.app.domain

import com.cinepilot.app.data.models.GeminiIntentResponse
import com.cinepilot.app.data.models.IntentType
import com.cinepilot.app.data.models.StreamingService
import org.json.JSONObject

/**
 * Parses the raw text returned by Gemini into a structured [GeminiIntentResponse].
 *
 * This is deliberately free of Android dependencies so it can be unit tested on the JVM.
 * The parser is defensive: model output is untrusted text and may be wrapped in markdown
 * fences, may omit fields, or may name an intent that does not exist.
 */
object IntentParser {

    private val validIntents: Set<String> = IntentType.entries.map { it.name }.toSet()

    /**
     * @param rawText the model response, possibly wrapped in a ```json fence
     * @param fallbackQuery the original user utterance, used when parsing fails
     */
    fun parse(rawText: String, fallbackQuery: String): GeminiIntentResponse {
        val cleaned = stripCodeFence(rawText)

        val obj = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            return fallback(fallbackQuery)
        }

        val intentName = obj.optString("intentType", "").trim().uppercase()
        if (intentName !in validIntents) {
            return fallback(fallbackQuery)
        }

        val intentType = try {
            IntentType.valueOf(intentName)
        } catch (e: IllegalArgumentException) {
            return fallback(fallbackQuery)
        }

        val confidence = obj.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f)

        return GeminiIntentResponse(
            intentType = intentType,
            query = obj.optNullableString("query"),
            movieTitle = obj.optNullableString("movieTitle"),
            serviceName = parseService(obj.optNullableString("serviceName")),
            confidence = confidence
        )
    }

    /** Strips a leading/trailing markdown code fence if the model added one. */
    internal fun stripCodeFence(text: String): String {
        var s = text.trim()
        if (s.startsWith("```")) {
            // Drop the opening fence line (```json, ```JSON, ``` etc.)
            val firstNewline = s.indexOf('\n')
            s = if (firstNewline >= 0) s.substring(firstNewline + 1) else ""
        }
        val closingFence = s.lastIndexOf("```")
        if (closingFence >= 0) {
            s = s.substring(0, closingFence)
        }
        return s.trim()
    }

    private fun parseService(raw: String?): StreamingService? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().uppercase().replace(' ', '_').replace('-', '_')
        return try {
            StreamingService.valueOf(normalized)
        } catch (e: IllegalArgumentException) {
            StreamingService.UNKNOWN
        }
    }

    private fun fallback(query: String): GeminiIntentResponse = GeminiIntentResponse(
        intentType = IntentType.UNKNOWN,
        query = query,
        movieTitle = null,
        serviceName = null,
        confidence = 0f
    )

    /** JSONObject.optString returns "null" for a JSON null, which we must not treat as a title. */
    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "").trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
        return value
    }
}