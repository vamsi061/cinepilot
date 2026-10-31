package com.cinepilot.app.domain

import com.cinepilot.app.data.models.IntentType
import com.cinepilot.app.data.models.StreamingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {

    @Test
    fun `parses a well formed response`() {
        val raw = """
            {"intentType":"SEARCH_MOVIE","query":"comedy movies","movieTitle":null,"serviceName":null,"confidence":0.9}
        """.trimIndent()

        val result = IntentParser.parse(raw, "fallback")

        assertEquals(IntentType.SEARCH_MOVIE, result.intentType)
        assertEquals("comedy movies", result.query)
        assertNull(result.movieTitle)
        assertNull(result.serviceName)
        assertEquals(0.9f, result.confidence, 0.001f)
    }

    @Test
    fun `strips a json code fence`() {
        val raw = "```json\n{\"intentType\":\"PLAY_MOVIE\",\"movieTitle\":\"Inception\",\"confidence\":0.7}\n```"

        val result = IntentParser.parse(raw, "play inception")

        assertEquals(IntentType.PLAY_MOVIE, result.intentType)
        assertEquals("Inception", result.movieTitle)
    }

    @Test
    fun `strips a bare code fence`() {
        val raw = "```\n{\"intentType\":\"PAUSE\",\"confidence\":0.6}\n```"

        val result = IntentParser.parse(raw, "pause")

        assertEquals(IntentType.PAUSE, result.intentType)
    }

    @Test
    fun `unknown intent name falls back and keeps the utterance`() {
        val raw = """{"intentType":"DANCE","confidence":0.9}"""

        val result = IntentParser.parse(raw, "do a dance")

        assertEquals(IntentType.UNKNOWN, result.intentType)
        assertEquals("do a dance", result.query)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `malformed json falls back`() {
        val result = IntentParser.parse("this is not json", "show me action")

        assertEquals(IntentType.UNKNOWN, result.intentType)
        assertEquals("show me action", result.query)
    }

    @Test
    fun `empty model output falls back`() {
        val result = IntentParser.parse("", "find thriller")

        assertEquals(IntentType.UNKNOWN, result.intentType)
        assertEquals("find thriller", result.query)
    }

    @Test
    fun `json null title becomes kotlin null not the string null`() {
        val raw = """{"intentType":"PLAY_MOVIE","movieTitle":null,"query":"something","confidence":0.5}"""

        val result = IntentParser.parse(raw, "something")

        assertEquals(IntentType.PLAY_MOVIE, result.intentType)
        assertNull(result.movieTitle)
        assertEquals("something", result.query)
    }

    @Test
    fun `parses a known streaming service`() {
        val raw = """{"intentType":"OPEN_APP","serviceName":"netflix","confidence":0.8}"""

        val result = IntentParser.parse(raw, "open netflix")

        assertEquals(StreamingService.NETFLIX, result.serviceName)
    }

    @Test
    fun `maps an unknown streaming service to UNKNOWN`() {
        val raw = """{"intentType":"OPEN_APP","serviceName":"some-future-app","confidence":0.8}"""

        val result = IntentParser.parse(raw, "open some future app")

        assertEquals(StreamingService.UNKNOWN, result.serviceName)
    }

    @Test
    fun `clamps confidence to the unit interval`() {
        val high = IntentParser.parse("""{"intentType":"STOP","confidence":4.2}""", "stop")
        val low = IntentParser.parse("""{"intentType":"STOP","confidence":-3}""", "stop")

        assertTrue(high.confidence <= 1f)
        assertTrue(low.confidence >= 0f)
    }

    @Test
    fun `confidence defaults when absent`() {
        val result = IntentParser.parse("""{"intentType":"STOP"}""", "stop")

        assertEquals(0.5f, result.confidence, 0.001f)
    }
}