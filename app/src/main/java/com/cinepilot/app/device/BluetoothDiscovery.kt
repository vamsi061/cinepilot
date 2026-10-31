package com.cinepilot.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A BLE peripheral we observed advertising. */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val advertisedServices: List<String>
)

/** A GATT service/characteristic we enumerated on a connected peripheral. */
data class GattServiceInfo(
    val serviceUuid: String,
    val characteristics: List<GattCharacteristicInfo>
)

data class GattCharacteristicInfo(
    val uuid: String,
    val properties: Int
)

/**
 * Bluetooth LE discovery and enumeration.
 *
 * Scope is deliberately limited to *reading* what a device advertises and *enumerating*
 * what its GATT table contains. This class does not write to any characteristic and does
 * not derive command bytes from UUIDs. Until a documented protocol is available, sending
 * guessed writes would be both unverifiable and potentially disruptive to the device.
 */
class BluetoothDiscovery(private val context: Context) : GattEnumerator {

    companion object {
        private const val TAG = "BluetoothDiscovery"
    }

    private val adapter: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter get() = adapter?.adapter

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    // The active scanner's callback, kept so stopScan can detach the same instance.
    private var activeCallback: ScanCallback? = null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** All runtime permissions this component needs at the current API level. */
    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Emits every advertisement seen until the collector stops or the caller invokes
     * [stopScan]. Callers are responsible for holding the permissions; a missing
     * permission surfaces as an exception on the flow.
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth LE scanner unavailable (adapter off or unsupported)"))
            return@callbackFlow
        }
        if (!hasPermissions()) {
            close(SecurityException("Bluetooth scan permission not granted"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.toDiscoveredDevice()
                // Deduplicate by address, keeping the most recent RSSI.
                _devices.value = _devices.value
                    .filterNot { it.address == record.address }
                    .plus(record)
                    .sortedByDescending { it.rssi }
                trySend(record)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Scan failed with code $errorCode")
                close(IllegalStateException("BLE scan failed (code $errorCode)"))
            }
        }

        activeCallback = callback

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // No filters: we want to see everything nearby so the user can identify their STB.
        scanner.startScan(emptyList<ScanFilter>(), settings, callback)

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "stopScan failed", e)
            }
            activeCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = activeCallback ?: return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan failed", e)
        }
        activeCallback = null
    }

    fun clearDevices() {
        _devices.value = emptyList()
    }

    private fun ScanResult.toDiscoveredDevice(): DiscoveredDevice {
        val services = scanRecord?.serviceUuids
            ?.map { it.uuid.toString().lowercase() }
            ?: emptyList()
        return DiscoveredDevice(
            name = scanRecord?.deviceName ?: device?.name,
            address = device.address,
            rssi = rssi,
            advertisedServices = services
        )
    }

    /**
     * Connect, wait for service discovery, and return the GATT table.
     *
     * Read-only: services and characteristic properties are listed; nothing is written.
     * The connection is closed before returning. A discovery timeout or a stack error is
     * returned as a failure with the observed status rather than retried in a loop.
     */
    @SuppressLint("MissingPermission")
    override suspend fun enumerateGatt(address: String): Result<List<GattServiceInfo>> {
        if (!hasPermissions()) {
            return Result.failure(SecurityException("Bluetooth connect permission not granted"))
        }
        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return Result.failure(IllegalArgumentException("Invalid device address: $address"))

        return suspendCancellableCoroutine { cont ->
            var gatt: BluetoothGatt? = null
            var completed = false

            fun finish(result: Result<List<GattServiceInfo>>) {
                if (completed) return
                completed = true
                try {
                    gatt?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "gatt.close failed", e)
                }
                if (cont.isActive) cont.resume(result)
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!g.discoverServices()) {
                            finish(Result.failure(IllegalStateException("discoverServices() returned false")))
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        finish(Result.failure(IllegalStateException("Disconnected before discovery (status $status)")))
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.failure(IllegalStateException("Service discovery failed (status $status)")))
                        return
                    }
                    val table = g.services?.map { service ->
                        GattServiceInfo(
                            serviceUuid = service.uuid.toString().lowercase(),
                            characteristics = service.characteristics.map { c ->
                                GattCharacteristicInfo(
                                    uuid = c.uuid.toString().lowercase(),
                                    properties = c.properties
                                )
                            }
                        )
                    } ?: emptyList()
                    finish(Result.success(table))
                }
            }

            try {
                gatt = device.connectGatt(context, false, callback)
            } catch (e: SecurityException) {
                finish(Result.failure(e))
                return@suspendCancellableCoroutine
            } catch (e: Exception) {
                finish(Result.failure(e))
                return@suspendCancellableCoroutine
            }

            if (gatt == null) {
                finish(Result.failure(IllegalStateException("connectGatt returned null")))
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try {
                    gatt?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "gatt.close on cancel failed", e)
                }
            }
        }
    }
}