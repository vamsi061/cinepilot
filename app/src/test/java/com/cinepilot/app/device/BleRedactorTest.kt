package com.cinepilot.app.device

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for BLE address redaction and UUID labelling.
 * These run on the JVM without Android dependencies.
 */
class BleRedactorTest {

    @Test
    fun `redacts a standard MAC address keeping last two octets`() {
        val result = BleRedactor.redactAddress("AA:BB:CC:DD:EE:FF")
        assertEquals("…:EE:FF", result)
    }

    @Test
    fun `redacts a short address gracefully`() {
        val result = BleRedactor.redactAddress("AB")
        assertEquals("…", result)
    }

    @Test
    fun `redacts an empty string`() {
        val result = BleRedactor.redactAddress("")
        assertEquals("…", result)
    }

    @Test
    fun `labels a SIG 16-bit UUID`() {
        val uuid = "0000180a-0000-1000-8000-00805f9b34fb"
        val label = BleRedactor.uuidLabel(uuid)
        assertEquals("SIG 16-bit: 0x180A", label)
    }

    @Test
    fun `labels a custom UUID with truncation`() {
        val uuid = "12345678-abcd-efgh-ijkl-mnopqrstuvwx"
        val label = BleRedactor.uuidLabel(uuid)
        assertEquals("12345678…", label)
    }
}