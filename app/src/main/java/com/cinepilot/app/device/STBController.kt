package com.cinepilot.app.device

/**
 * Outcome of an attempt to control the STB.
 *
 * Every operation returns one of these rather than a boolean, so a failure carries
 * the reason and the evidence we actually observed. Nothing is reported as
 * [Success] unless a real, observed result backs it.
 */
sealed interface ControlResult {
    /** The operation completed and we observed the expected effect. */
    data class Success(val detail: String) : ControlResult

    /** The mechanism exists but the target did not respond. */
    data class NoResponse(val detail: String) : ControlResult

    /** We cannot perform this operation on this target at all. */
    data class Unsupported(val detail: String) : ControlResult

    /** The operation failed with an error worth surfacing. */
    data class Failure(val detail: String, val cause: Throwable? = null) : ControlResult
}

/** Buttons a TV remote can send. Only implemented where a verified mechanism exists. */
enum class RemoteKey {
    POWER, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, OK, BACK, HOME, VOLUME_UP, VOLUME_DOWN, MUTE
}

/**
 * Abstract control surface for a television / set-top box.
 *
 * Implementations must only claim capabilities they can actually perform. The app
 * queries [capabilities] before offering a control in the UI.
 */
interface STBController {

    /** Stable identifier for the mechanism, e.g. "jio-remote-intent". */
    val id: String

    /** Human-readable name for the UI. */
    val displayName: String

    /**
     * True only when the mechanism is present and usable on this device right now.
     * Implementations must actually check (package lookup, permission, adapter state)
     * rather than assuming.
     */
    suspend fun isAvailable(): Boolean

    /** The keys this mechanism can actually send. */
    fun capabilities(): Set<RemoteKey>

    /** Send a key. Returns an observed outcome. */
    suspend fun sendKey(key: RemoteKey): ControlResult

    /** Release any held resources. */
    suspend fun close() {}
}