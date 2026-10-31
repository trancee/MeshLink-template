package ch.trancee.meshlink.proof

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission

internal class GattCentral(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    listener: ProofTransport.Listener,
) : GattEndpoint(listener) {
    private var gatt: BluetoothGatt? = null
    private var request: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var writing = false
    private var earlyResponse: ByteArray? = null

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
        ])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            post {
                if (scanning) {
                    stopScan()
                    gatt = result.device.connectGatt(
                        context, false, callback, BluetoothDevice.TRANSPORT_LE,
                    )
                    if (gatt == null) fail("connectGatt returned null")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            post { fail("Scan failed: $errorCode") }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(link: BluetoothGatt, status: Int, newState: Int) {
            if (!active && newState == BluetoothProfile.STATE_DISCONNECTED) {
                try {
                    link.close()
                } catch (_: SecurityException) {
                    // The OS owns final cleanup if permission was revoked.
                }
                return
            }
            post {
                if (link !== gatt) return@post
                if (status != BluetoothGatt.GATT_SUCCESS ||
                    newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("Connection ended: $status/$newState")
                } else if (newState == BluetoothProfile.STATE_CONNECTED && !link.discoverServices()) {
                    fail("Service discovery rejected")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(link: BluetoothGatt, status: Int) {
            post {
                if (link !== gatt) return@post
                val service = link.getService(ProofUuids.service)
                request = service?.getCharacteristic(ProofUuids.request)
                val response = service?.getCharacteristic(ProofUuids.response)
                val descriptor = response?.getDescriptor(ProofUuids.cccd)
                if (status != BluetoothGatt.GATT_SUCCESS || request == null) {
                    fail("Service discovery failed: $status")
                    return@post
                }
                if (response == null || descriptor == null ||
                    !link.setCharacteristicNotification(response, true)) {
                    fail("Missing proof service or subscription rejected")
                    return@post
                }
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    link.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    descriptor.value = value
                    link.writeDescriptor(descriptor)
                }
                if (!accepted) fail("CCCD write rejected")
            }
        }

        override fun onDescriptorWrite(
            link: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
        ) {
            post {
                if (link !== gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == ProofUuids.cccd) {
                    listener.ready()
                } else {
                    fail("CCCD write failed: $status")
                }
            }
        }

        override fun onCharacteristicWrite(
            link: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
        ) {
            post {
                if (link !== gatt) return@post
                writing = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Request write failed: $status")
                } else {
                    earlyResponse?.let {
                        earlyResponse = null
                        listener.received(it)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            link: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
        ) {
            notification(link, characteristic, value.copyOf())
        }

        override fun onCharacteristicChanged(
            link: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notification(link, characteristic, characteristic.value?.copyOf() ?: byteArrayOf())
            }
        }
    }

    private fun notification(
        link: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
    ) {
        post {
            if (link !== gatt || characteristic.uuid != ProofUuids.response) return@post
            if (writing) {
                if (earlyResponse != null) fail("More than one pending response")
                else earlyResponse = value
            } else {
                listener.received(value)
            }
        }
    }

    override fun start() {
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            fail("BLE scanner unavailable")
            return
        }
        scanning = true
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(ProofUuids.service)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun send(bytes: ByteArray) {
        val link = gatt
        val characteristic = request
        if (writing || link == null || characteristic == null) {
            fail("GATT write not ready")
            return
        }
        writing = true
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            link.writeCharacteristic(characteristic, bytes, type) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = type
            characteristic.value = bytes
            link.writeCharacteristic(characteristic)
        }
        if (!accepted) fail("Request write rejected")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        if (scanning) {
            scanning = false
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
    ])
    override fun release() {
        try {
            stopScan()
        } finally {
            gatt?.let { link ->
                try {
                    link.disconnect()
                } finally {
                    // Close after disconnect callback, with a bounded stack-failure fallback.
                    handler.postDelayed({
                        try {
                            link.close()
                        } catch (_: SecurityException) {
                            // The OS owns final cleanup if permission was revoked.
                        }
                    }, CLOSE_DEADLINE_MS)
                }
            }
            gatt = null
            request = null
            earlyResponse = null
        }
    }

    private companion object {
        const val CLOSE_DEADLINE_MS = 2_000L
    }
}
