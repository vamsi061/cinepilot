package com.cinepilot.app.ui

import com.cinepilot.app.STBConnectionState
import com.cinepilot.app.device.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePresentationTest {
    @Test
    fun `unverified controller keeps every remote key disabled`() {
        val policy = RemotePresentationPolicy(verifiedCapabilities = emptySet())

        assertTrue(RemoteKey.entries.all { !policy.isEnabled(it) })
        assertEquals("Direct STB control unavailable", policy.actionLabel)
    }

    @Test
    fun `verified capabilities enable only the advertised key`() {
        val policy = RemotePresentationPolicy(setOf(RemoteKey.OK))

        assertTrue(policy.isEnabled(RemoteKey.OK))
        assertFalse(policy.isEnabled(RemoteKey.BACK))
    }

    @Test
    fun `connection labels never imply a connected unverified session`() {
        assertEquals("Control unavailable", connectionLabel(STBConnectionState.Disconnected))
        assertEquals("Connecting", connectionLabel(STBConnectionState.Connecting))
        assertEquals("Connected", connectionLabel(STBConnectionState.Connected))
        assertEquals("Connection error", connectionLabel(STBConnectionState.Error("x")))
    }
}
