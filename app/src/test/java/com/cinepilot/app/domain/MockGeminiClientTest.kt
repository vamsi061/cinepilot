package com.cinepilot.app.domain

import com.cinepilot.app.data.models.IntentType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MockGeminiClientTest {

    private val client = MockGeminiClient()

    @Test
    fun `play command yields PLAY_MOVIE with a title`() = runTest {
        val parsed = IntentParser.parse(client.classifyIntent("play Inception"), "play Inception")

        assertEquals(IntentType.PLAY_MOVIE, parsed.intentType)
        assertEquals("Inception", parsed.movieTitle)
    }

    @Test
    fun `search command yields SEARCH_MOVIE with a query`() = runTest {
        val parsed = IntentParser.parse(client.classifyIntent("search comedy movies"), "search comedy movies")

        assertEquals(IntentType.SEARCH_MOVIE, parsed.intentType)
        assertEquals("comedy movies", parsed.query)
    }

    @Test
    fun `pause yields PAUSE`() = runTest {
        val parsed = IntentParser.parse(client.classifyIntent("pause the movie"), "pause the movie")
        assertEquals(IntentType.PAUSE, parsed.intentType)
    }

    @Test
    fun `blank utterance yields UNKNOWN`() = runTest {
        val parsed = IntentParser.parse(client.classifyIntent("   "), "   ")
        assertEquals(IntentType.UNKNOWN, parsed.intentType)
    }

    @Test
    fun `free text is treated as a search rather than dropped`() = runTest {
        val parsed = IntentParser.parse(client.classifyIntent("the godfather"), "the godfather")
        assertEquals(IntentType.SEARCH_MOVIE, parsed.intentType)
        assertEquals("the godfather", parsed.query)
    }
}