package com.cinepilot.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val description: String,
    val releaseYear: Int,
    val duration: Int,
    val genres: List<String>,
    val rating: Double,
    val posterUrl: String,
    val backdropUrl: String,
    val availableServices: List<StreamingService>,
    val isAvailable: Boolean,
    val cast: List<String> = emptyList(),
    val director: String = "",
    val trailerUrl: String = "",
    val isFavorite: Boolean = false,
    val addedDate: Long = System.currentTimeMillis()
) {

    fun getFormattedDuration(): String {
        val hours = duration / 60
        val minutes = duration % 60
        return if (hours > 0) {
            if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        } else {
            "${minutes}m"
        }
    }

    fun getFormattedRating(): String {
        return "${rating}/10"
    }

    fun getFormattedReleaseYear(): String {
        return "($releaseYear)"
    }
}

@Serializable
enum class StreamingService {
    JIO_MOVIE,
    JIO_WORLD,
    NETFLIX,
    AMAZON_PRIME,
    DISNEY_PLUS,
    APPLETV,
    HOTSTAR,
    VIKRAM,
    YOUTUBE,
    UNKNOWN;

    companion object {
        fun fromString(serviceName: String): StreamingService {
            return try {
                StreamingService.valueOf(serviceName.uppercase())
            } catch (e: IllegalArgumentException) {
                UNKNOWN
            }
        }

        fun getPreferredServices(userSubscriptions: List<StreamingService>): List<StreamingService> {
            return userSubscriptions.filter { service ->
                when (service) {
                    StreamingService.JIO_MOVIE, StreamingService.JIO_WORLD -> true
                    else -> false
                }
            }
        }
    }
}