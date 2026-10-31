package com.cinepilot.app.ui

import com.cinepilot.app.STBConnectionState
import com.cinepilot.app.device.RemoteKey

/** UI policy derived only from capabilities proven by an STBController. */
class RemotePresentationPolicy(
    private val verifiedCapabilities: Set<RemoteKey>
) {
    val actionLabel: String
        get() = if (verifiedCapabilities.isEmpty()) "Direct STB control unavailable" else "Verified controls available"

    fun isEnabled(key: RemoteKey): Boolean = key in verifiedCapabilities
}

fun connectionLabel(state: STBConnectionState): String = when (state) {
    STBConnectionState.Disconnected -> "Control unavailable"
    STBConnectionState.Connecting -> "Connecting"
    STBConnectionState.Connected -> "Connected"
    is STBConnectionState.Error -> "Connection error"
}
