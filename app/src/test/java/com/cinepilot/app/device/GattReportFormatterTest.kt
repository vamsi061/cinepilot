package com.cinepilot.app.device

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the GATT report formatter.
 * These run on the JVM without Android dependencies.
 */
class GattReportFormatterTest {

    @Test
    fun `formats a success result with services`() {
        val result = GattDiagnosticResult.Success(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "JioSTB",
            services = listOf(
                GattServiceInfo(
                    serviceUuid = "0000180a-0000-1000-8000-00805f9b34fb",
                    characteristics = listOf(
                        GattCharacteristicInfo(
                            uuid = "00002a29-0000-1000-8000-00805f9b34fb",
                            properties = 0x02 // READ
                        )
                    )
                )
            ),
            durationMs = 1234
        )

        val report = GattReportFormatter.format(result)

        assertTrue(report.contains("JioSTB"))
        assertTrue(report.contains("…:EE:FF"))
        assertTrue(report.contains("1234 ms"))
        assertTrue(report.contains("SIG 16-bit: 0x180A"))
        assertTrue(report.contains("READ"))
    }

    @Test
    fun `formats a timeout result`() {
        val result = GattDiagnosticResult.Timeout("AA:BB:CC:DD:EE:FF", 15000)
        val report = GattReportFormatter.format(result)
        assertTrue(report.contains("Timeout"))
        assertTrue(report.contains("15000"))
    }

    @Test
    fun `formats a permission denied result`() {
        val result = GattDiagnosticResult.PermissionDenied("AA:BB:CC:DD:EE:FF", "BLUETOOTH_CONNECT not granted")
        val report = GattReportFormatter.format(result)
        assertTrue(report.contains("Permission denied"))
    }

    @Test
    fun `describes property bitmask correctly`() {
        // READ | WRITE | NOTIFY = 0x02 | 0x08 | 0x10 = 0x1A
        val props = GattReportFormatter.describeProperties(0x1A)
        assertTrue(props.contains("READ"))
        assertTrue(props.contains("WRITE"))
        assertTrue(props.contains("NOTIFY"))
        assertTrue(!props.contains("BROADCAST"))
    }

    @Test
    fun `describes zero properties as NONE`() {
        val props = GattReportFormatter.describeProperties(0)
        assertTrue(props == "NONE")
    }

    @Test
    fun `formats a disconnected result`() {
        val result = GattDiagnosticResult.Disconnected("AA:BB:CC:DD:EE:FF", "Disconnected before discovery (status 133)")
        val report = GattReportFormatter.format(result)
        assertTrue(report.contains("Disconnected"))
        assertTrue(report.contains("status 133"))
    }
}