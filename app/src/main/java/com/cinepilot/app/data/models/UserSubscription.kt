package com.cinepilot.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserSubscription(
    val serviceName: StreamingService,
    val accountId: String,
    val isActive: Boolean,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.STANDARD,
    val expiresAt: Long? = null,
    val isJioSubscription: Boolean = false
)

@Serializable
enum class SubscriptionTier {
    FREE,
    STANDARD,
    PREMIUM,
    UNKNOWN;

    companion object {
        fun fromString(tier: String): SubscriptionTier {
            return try {
                SubscriptionTier.valueOf(tier.uppercase())
            } catch (e: IllegalArgumentException) {
                UNKNOWN
            }
        }
    }
}

@Serializable
data class SubscriptionPreferences(
    val preferredOrder: List<StreamingService> = listOf(
        StreamingService.JIO_MOVIE,
        StreamingService.JIO_WORLD,
        StreamingService.NETFLIX,
        StreamingService.AMAZON_PRIME,
        StreamingService.DISNEY_PLUS,
        StreamingService.APPLETV,
        StreamingService.HOTSTAR,
        StreamingService.YOUTUBE
    ),
    val jioFirstEnabled: Boolean = true,
    val enableVoicePreferences: Boolean = true,
    val autoPlayTrailers: Boolean = true
)

@Serializable
data class VoiceCommand(
    val text: String,
    val confidence: Float,
    val intentType: IntentType,
    val entities: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class IntentType {
    SEARCH_MOVIE,
    PLAY_MOVIE,
    PAUSE,
    RESUME,
    STOP,
    NAVIGATE_HOME,
    OPEN_APP,
    CHANGE_VOLUME,
    CHANGE_CHANNEL,
    UNKNOWN
}

@Serializable
data class GeminiIntentResponse(
    val intentType: IntentType,
    val query: String? = null,
    val movieTitle: String? = null,
    val serviceName: StreamingService? = null,
    val confidence: Float,
    val entities: Map<String, String> = emptyMap()
)