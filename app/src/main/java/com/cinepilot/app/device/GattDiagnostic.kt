package com.cinepilot.app.device

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Outcome of a GATT enumeration attempt on a specific device.
 * Sealed so the UI can render each case distinctly.
 */
sealed interface GattDiagnosticResult {
    /** Services and characteristics were successfully enumerated. */
    data class Success(
        val deviceAddress: String,
        val deviceName: String?,
        val services: List<GattServiceInfo>,
        val durationMs: Long
    ) : GattDiagnosticResult

    /** The connection or discovery timed out. */
    data class Timeout(
        val deviceAddress: String,
        val timeoutMs: Long
    ) : GattDiagnosticResult

    /** The device disconnected before or during service discovery. */
    data class Disconnected(
        val deviceAddress: String,
        val detail: String
    ) : GattDiagnosticResult

    /** A permission was missing at runtime. */
    data class PermissionDenied(
        val deviceAddress: String,
        val detail: String
    ) : GattDiagnosticResult

    /** An unexpected error occurred. */
    data class Error(
        val deviceAddress: String,
        val detail: String,
        val cause: Throwable? = null
    ) : GattDiagnosticResult
}

/**
 * Redaction utilities for BLE diagnostic output.
 *
 * MAC addresses are partially masked so logs remain useful for debugging
 * (last two octets visible) without leaking the full hardware identifier.
 */
object BleRedactor {
    /** "AA:BB:CC:DD:EE:FF" → "…:EE:FF" */
    fun redactAddress(address: String): String {
        val parts = address.split(":")
        return if (parts.size >= 2) "…:${parts[parts.size - 2]}:${parts.last()}" else "…"
    }

    /** UUIDs are not sensitive; they are standard identifiers. No redaction needed. */
    fun uuidLabel(uuid: String): String {
        return when {
            uuid.startsWith("0000") && uuid.endsWith("-0000-1000-8000-00805f9b34fb") ->
                "SIG 16-bit: 0x${uuid.substring(4, 8).uppercase()}"
            else -> uuid.take(8) + "…"
        }
    }
}

/**
 * Formats a GATT table into a human-readable diagnostic report.
 * Pure function, testable without Android.
 */
object GattReportFormatter {

    fun format(result: GattDiagnosticResult): String = when (result) {
        is GattDiagnosticResult.Success -> buildString {
            appendLine("═══ GATT Diagnostic Report ═══")
            appendLine("Device: ${result.deviceName ?: "(unnamed)"} [${BleRedactor.redactAddress(result.deviceAddress)}]")
            appendLine("Duration: ${result.durationMs} ms")
            appendLine("Services: ${result.services.size}")
            appendLine()
            result.services.forEachIndexed { i, service ->
                appendLine("  Service ${i + 1}: ${BleRedactor.uuidLabel(service.serviceUuid)}")
                service.characteristics.forEach { c ->
                    val props = describeProperties(c.properties)
                    appendLine("    Char: ${BleRedactor.uuidLabel(c.uuid)}  [$props]")
                }
            }
            appendLine("═══════════════════════════════")
        }

        is GattDiagnosticResult.Timeout ->
            "⏱ Timeout after ${result.timeoutMs} ms connecting to ${BleRedactor.redactAddress(result.deviceAddress)}"

        is GattDiagnosticResult.Disconnected ->
            "🔌 Disconnected: ${BleRedactor.redactAddress(result.deviceAddress)} — ${result.detail}"

        is GattDiagnosticResult.PermissionDenied ->
            "🔒 Permission denied for ${BleRedactor.redactAddress(result.deviceAddress)}: ${result.detail}"

        is GattDiagnosticResult.Error ->
            "❌ Error for ${BleRedactor.redactAddress(result.deviceAddress)}: ${result.detail}"
    }

    /** Decode the BluetoothGattCharacteristic property bitmask into readable labels. */
    fun describeProperties(properties: Int): String {
        val flags = mutableListOf<String>()
        if (properties and 0x01 != 0) flags.add("BROADCAST")
        if (properties and 0x02 != 0) flags.add("READ")
        if (properties and 0x04 != 0) flags.add("WRITE_NO_RESPONSE")
        if (properties and 0x08 != 0) flags.add("WRITE")
        if (properties and 0x10 != 0) flags.add("NOTIFY")
        if (properties and 0x20 != 0) flags.add("INDICATE")
        if (properties and 0x40 != 0) flags.add("SIGNED_WRITE")
        if (properties and 0x80 != 0) flags.add("EXTENDED")
        return if (flags.isEmpty()) "NONE" else flags.joinToString(", ")
    }
}

/**
 * Orchestrates a single GATT enumeration with a bounded timeout.
 *
 * Design decisions:
 *  - Only one device at a time; the caller passes the address the user selected.
 *  - Read-only: we enumerate services and characteristic properties but never write.
 *  - Timeout is enforced here rather than relying solely on the BLE stack, which
 *    may never call back on some chipsets.
 *  - The result is always one of the sealed variants; null never escapes.
 */
class GattDiagnosticWorkflow(
    private val enumerator: GattEnumerator,
    private val timeoutMs: Long = 15_000L
) {
    companion object {
        private const val TAG = "GattDiagnostic"
    }

    /**
     * Enumerate the GATT table of the device at [address].
     * Returns a sealed result; never throws.
     */
    suspend fun run(address: String, deviceName: String?): GattDiagnosticResult {
        Log.i(TAG, "Starting GATT enumeration for ${BleRedactor.redactAddress(address)}")
        val start = System.currentTimeMillis()

        if (!enumerator.hasPermissions()) {
            Log.w(TAG, "Missing Bluetooth permission for ${BleRedactor.redactAddress(address)}")
            return GattDiagnosticResult.PermissionDenied(address, "Bluetooth connect permission not granted")
        }

        val outcome = withTimeoutOrNull(timeoutMs) {
            enumerator.enumerateGatt(address)
        }

        val elapsed = System.currentTimeMillis() - start

        return when {
            outcome == null -> {
                Log.w(TAG, "Timeout after ${timeoutMs}ms for ${BleRedactor.redactAddress(address)}")
                GattDiagnosticResult.Timeout(address, timeoutMs)
            }
            outcome.isSuccess -> {
                val services = outcome.getOrNull() ?: emptyList()
                Log.i(TAG, "Enumerated ${services.size} services in ${elapsed}ms")
                GattDiagnosticResult.Success(address, deviceName, services, elapsed)
            }
            else -> {
                val error = outcome.exceptionOrNull()
                Log.e(TAG, "Enumeration failed for ${BleRedactor.redactAddress(address)}", error)
                when (error) {
                    is SecurityException ->
                        GattDiagnosticResult.PermissionDenied(address, error.message ?: "permission denied")
                    is IllegalStateException -> {
                        val msg = error.message ?: ""
                        if (msg.contains("Disconnect", ignoreCase = true)) {
                            GattDiagnosticResult.Disconnected(address, msg)
                        } else {
                            GattDiagnosticResult.Error(address, msg, error)
                        }
                    }
                    else ->
                        GattDiagnosticResult.Error(address, error?.message ?: "unknown", error)
                }
            }
        }
    }
}