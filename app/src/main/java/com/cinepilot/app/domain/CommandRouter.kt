package com.cinepilot.app.domain

import com.cinepilot.app.data.models.GeminiIntentResponse
import com.cinepilot.app.data.models.IntentType

/**
 * A resolved instruction for the app to carry out.
 * Kept separate from the raw intent so the routing rules are testable without Android.
 */
sealed interface Command {
    data class SearchMovies(val query: String) : Command
    data class PlayTitle(val title: String) : Command
    data object TransportToggle : Command      // pause/resume
    data object TransportStop : Command
    data object NavigateHome : Command
    data class OpenStreamingApp(val service: String) : Command
    data object VolumeUp : Command
    data object VolumeDown : Command
    data class Unsupported(val reason: String) : Command
}

/**
 * Maps a parsed intent onto a concrete [Command].
 *
 * Rules that matter:
 *  - A PLAY_MOVIE with no title is not executable; degrade it to a search.
 *  - Anything we cannot carry out is reported as [Command.Unsupported] rather than
 *    silently doing nothing or pretending it worked.
 */
object CommandRouter {

    fun route(response: GeminiIntentResponse): Command {
        return when (response.intentType) {
            IntentType.SEARCH_MOVIE -> {
                val query = response.query?.takeIf { it.isNotBlank() }
                    ?: response.movieTitle?.takeIf { it.isNotBlank() }
                if (query == null) {
                    Command.Unsupported("Search request had no query text")
                } else {
                    Command.SearchMovies(query)
                }
            }

            IntentType.PLAY_MOVIE -> {
                val title = response.movieTitle?.takeIf { it.isNotBlank() }
                    ?: response.query?.takeIf { it.isNotBlank() }
                if (title == null) {
                    Command.Unsupported("Play request had no title")
                } else {
                    Command.PlayTitle(title)
                }
            }

            IntentType.PAUSE, IntentType.RESUME -> Command.TransportToggle
            IntentType.STOP -> Command.TransportStop
            IntentType.NAVIGATE_HOME -> Command.NavigateHome

            IntentType.OPEN_APP -> {
                val service = response.serviceName?.takeIf {
                    it != com.cinepilot.app.data.models.StreamingService.UNKNOWN
                }
                if (service == null) {
                    Command.Unsupported("Open-app request named no known service")
                } else {
                    Command.OpenStreamingApp(service.name)
                }
            }

            IntentType.CHANGE_VOLUME -> {
                // Direction is expressed in the free-text query until we have a signed delta.
                val q = response.query?.lowercase() ?: ""
                if (q.contains("down") || q.contains("lower") || q.contains("decrease")) {
                    Command.VolumeDown
                } else {
                    Command.VolumeUp
                }
            }

            IntentType.CHANGE_CHANNEL ->
                Command.Unsupported("Channel control is not supported on this target")

            IntentType.UNKNOWN ->
                Command.Unsupported("Command not understood")
        }
    }
}