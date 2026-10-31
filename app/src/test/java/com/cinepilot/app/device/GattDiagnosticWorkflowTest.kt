package com.cinepilot.app.device

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GattDiagnosticWorkflow].
 *
 * These are MOCKED tests: they drive the workflow with a fake [GattEnumerator] and
 * verify timeout handling, error classification, and result mapping. They do not
 * exercise real Bluetooth hardware. Hardware verification is a separate activity,
 * documented in docs/specs/04_integration_evidence.md, and requires a physical
 * device plus the STB.
 */
class GattDiagnosticWorkflowTest {

    private class FakeGattEnumerator(
        private val result: Result<List<GattServiceInfo>>? = null,
        private val delayMs: Long = 0,
        private val permissions: Boolean = true
    ) : GattEnumerator {
        override fun hasPermissions(): Boolean = permissions

        override suspend fun enumerateGatt(address: String): Result<List<GattServiceInfo>> {
            if (delayMs > 0) delay(delayMs)
            return result ?: Result.failure(IllegalStateException("not configured"))
        }
    }

    private fun servicesFixture() = listOf(
        GattServiceInfo(
            serviceUuid = "0000180a-0000-1000-8000-00805f9b34fb",
            characteristics = listOf(
                GattCharacteristicInfo("00002a29-0000-1000-8000-00805f9b34fb", 0x02)
            )
        )
    )

    @Test
    fun `success result maps to Success carrying the service table`() = runTest {
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.success(servicesFixture())),
            timeoutMs = 5000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", "TestDevice")

        assertTrue(result is GattDiagnosticResult.Success)
        val success = result as GattDiagnosticResult.Success
        assertEquals(1, success.services.size)
        assertEquals("TestDevice", success.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", success.deviceAddress)
    }

    @Test
    fun `security exception maps to PermissionDenied`() = runTest {
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.failure(SecurityException("BLUETOOTH_CONNECT not granted"))),
            timeoutMs = 5000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", null)

        assertTrue(result is GattDiagnosticResult.PermissionDenied)
    }

    @Test
    fun `disconnection maps to Disconnected`() = runTest {
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.failure(IllegalStateException("Disconnected before discovery (status 133)"))),
            timeoutMs = 5000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", null)

        assertTrue(result is GattDiagnosticResult.Disconnected)
    }

    @Test
    fun `generic error maps to Error`() = runTest {
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.failure(RuntimeException("connectGatt returned null"))),
            timeoutMs = 5000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", null)

        assertTrue(result is GattDiagnosticResult.Error)
    }

    @Test
    fun `a slow enumeration is cut off by the timeout`() = runTest {
        // Enumerator takes 10s of virtual time; the workflow allows only 1s.
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.success(servicesFixture()), delayMs = 10_000),
            timeoutMs = 1_000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", null)

        assertTrue(
            "Expected Timeout but got ${result::class.simpleName}",
            result is GattDiagnosticResult.Timeout
        )
    }

    @Test
    fun `an enumeration inside the deadline succeeds`() = runTest {
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.success(servicesFixture()), delayMs = 100),
            timeoutMs = 5_000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", null)

        assertTrue(result is GattDiagnosticResult.Success)
    }

    @Test
    fun `missing permissions short circuits to PermissionDenied without enumerating`() = runTest {
        val workflow = GattDiagnosticWorkflow(
            FakeGattEnumerator(Result.success(servicesFixture()), permissions = false),
            timeoutMs = 5000
        )

        val result = workflow.run("AA:BB:CC:DD:EE:FF", null)

        assertTrue(result is GattDiagnosticResult.PermissionDenied)
    }
}