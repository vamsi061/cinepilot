package com.cinepilot.app.device

/**
 * Narrow contract for reading a peripheral's GATT table.
 *
 * Exists so [GattDiagnosticWorkflow] can be unit tested on the JVM without an
 * Android [android.content.Context] or real Bluetooth hardware.
 */
interface GattEnumerator {

    /** True when every runtime permission needed to connect is granted. */
    fun hasPermissions(): Boolean

    /**
     * Connect to [address], wait for service discovery, and return the GATT table.
     * Read-only: implementations must not write to any characteristic.
     */
    suspend fun enumerateGatt(address: String): Result<List<GattServiceInfo>>
}