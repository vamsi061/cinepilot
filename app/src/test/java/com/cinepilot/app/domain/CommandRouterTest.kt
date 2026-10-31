package com.cinepilot.app.domain

import com.cinepilot.app.data.models.GeminiIntentResponse
import com.cinepilot.app.data.models.IntentType
import com.cinepilot.app.data.models.StreamingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {

    private fun response(
        intent: IntentType,
        query: String? = null,
        title: String? = null,
        service: StreamingService? = null,
    ) = GeminiIntentResponse(
        intentType = intent,
        query = query,
        movieTitle = title,
        serviceName = service,
        confidence = 0.8f
    )

    @Test
    fun `search routes to SearchMovies`() {
        val cmd = CommandRouter.route(response(IntentType.SEARCH_MOVIE, query = "comedy"))
        assertEquals(Command.SearchMovies("comedy"), cmd)
    }

    @Test
    fun `search with no query is unsupported`() {
        val cmd = CommandRouter.route(response(IntentType.SEARCH_MOVIE))
        assertTrue(cmd is Command.Unsupported)
    }

    @Test
    fun `play with a title routes to PlayTitle`() {
        val cmd = CommandRouter.route(response(IntentType.PLAY_MOVIE, title = "Inception"))
        assertEquals(Command.PlayTitle("Inception"), cmd)
    }

    @Test
    fun `play falls back to the query when title is absent`() {
        val cmd = CommandRouter.route(response(IntentType.PLAY_MOVIE, query = "the matrix"))
        assertEquals(Command.PlayTitle("the matrix"), cmd)
    }

    @Test
    fun `play with neither title nor query is unsupported`() {
        val cmd = CommandRouter.route(response(IntentType.PLAY_MOVIE))
        assertTrue(cmd is Command.Unsupported)
    }

    @Test
    fun `pause and resume both map to a transport toggle`() {
        assertEquals(Command.TransportToggle, CommandRouter.route(response(IntentType.PAUSE)))
        assertEquals(Command.TransportToggle, CommandRouter.route(response(IntentType.RESUME)))
    }

    @Test
    fun `stop maps to TransportStop`() {
        assertEquals(Command.TransportStop, CommandRouter.route(response(IntentType.STOP)))
    }

    @Test
    fun `home maps to NavigateHome`() {
        assertEquals(Command.NavigateHome, CommandRouter.route(response(IntentType.NAVIGATE_HOME)))
    }

    @Test
    fun `volume direction is read from the query`() {
        assertEquals(Command.VolumeDown, CommandRouter.route(response(IntentType.CHANGE_VOLUME, query = "down")))
        assertEquals(Command.VolumeUp, CommandRouter.route(response(IntentType.CHANGE_VOLUME, query = "up")))
    }

    @Test
    fun `open app with a known service routes to OpenStreamingApp`() {
        val cmd = CommandRouter.route(
            response(IntentType.OPEN_APP, service = StreamingService.NETFLIX)
        )
        assertEquals(Command.OpenStreamingApp("NETFLIX"), cmd)
    }

    @Test
    fun `open app with an unknown service is unsupported`() {
        val cmd = CommandRouter.route(
            response(IntentType.OPEN_APP, service = StreamingService.UNKNOWN)
        )
        assertTrue(cmd is Command.Unsupported)
    }

    @Test
    fun `channel change is explicitly unsupported`() {
        val cmd = CommandRouter.route(response(IntentType.CHANGE_CHANNEL, query = "next"))
        assertTrue(cmd is Command.Unsupported)
    }

    @Test
    fun `unknown intent is unsupported`() {
        val cmd = CommandRouter.route(response(IntentType.UNKNOWN))
        assertTrue(cmd is Command.Unsupported)
    }
}