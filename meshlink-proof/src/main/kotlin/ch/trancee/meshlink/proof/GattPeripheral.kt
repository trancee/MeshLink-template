package ch.trancee.meshlink.proof

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission

internal class GattPeripheral(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    listener: ProofTransport.Listener,
) : GattEndpoint(listener) {
    private var server: BluetoothGattServer? = null
    private var peer: BluetoothDevice? = null
    private var subscribed = false
    private var notifying = false
    private var pendingRequest: ByteArray? = null
    private val response = BluetoothGattCharacteristic(
        ProofUuids.response, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0,
    )

    private val advertising = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            post { fail("Advertising failed: $errorCode") }
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            post {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Service registration failed: $status")
                    return@post
                }
                adapter.bluetoothLeAdvertiser?.startAdvertising(
                    AdvertiseSettings.Builder().setConnectable(true)
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build(),
                    AdvertiseData.Builder().addServiceUuid(ParcelUuid(ProofUuids.service)).build(),
                    advertising,
                )
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (peer != null && peer != device) server?.cancelConnection(device)
                    else peer = device
                } else if (device == peer) {
                    fail("Peer disconnected; restart peripheral for another run")
                }
            }
        }

        @RequiresPermission(allOf = [
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE,
        ])
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            val copy = value.copyOf()
            post {
                val valid = device == peer && descriptor.uuid == ProofUuids.cccd &&
                    !preparedWrite && offset == 0 &&
                    copy.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (responseNeeded) {
                    server?.sendResponse(
                        device, requestId,
                        if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        offset, null,
                    )
                }
                if (valid && !subscribed) {
                    subscribed = true
                    adapter.bluetoothLeAdvertiser?.stopAdvertising(advertising)
                    listener.ready()
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor,
        ) {
            post {
                val valid = device == peer && descriptor.uuid == ProofUuids.cccd && offset == 0
                server?.sendResponse(
                    device, requestId,
                    if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    offset, if (subscribed) byteArrayOf(1, 0) else byteArrayOf(0, 0),
                )
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            post {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            val copy = value.copyOf()
            post {
                val valid = device == peer && subscribed && characteristic.uuid == ProofUuids.request &&
                    responseNeeded && !preparedWrite && offset == 0 && copy.size == 20 &&
                    pendingRequest == null
                if (responseNeeded) {
                    server?.sendResponse(
                        device, requestId,
                        if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        offset, null,
                    )
                }
                if (valid) {
                    if (notifying) pendingRequest = copy else listener.received(copy)
                } else if (device == peer) {
                    fail("Invalid or overlapping request")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            post { server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            post {
                if (device != peer) return@post
                notifying = false
                if (status != BluetoothGatt.GATT_SUCCESS) fail("Notification failed: $status")
                else pendingRequest?.let {
                    pendingRequest = null
                    listener.received(it)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun start() {
        if (!adapter.isMultipleAdvertisementSupported || adapter.bluetoothLeAdvertiser == null) {
            fail("This device cannot advertise as a BLE peripheral")
            return
        }
        server = context.getSystemService(BluetoothManager::class.java).openGattServer(context, callback)
        val request = BluetoothGattCharacteristic(
            ProofUuids.request, BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        response.addDescriptor(BluetoothGattDescriptor(
            ProofUuids.cccd,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        ))
        val service = BluetoothGattService(ProofUuids.service, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(request)
        service.addCharacteristic(response)
        if (server?.addService(service) != true) fail("Could not open/register GATT server")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun send(bytes: ByteArray) {
        val device = peer
        val link = server
        val ready = subscribed && !notifying
        if (device == null || link == null || !ready) {
            fail("Notification not ready")
            return
        }
        notifying = true
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            link.notifyCharacteristicChanged(device, response, false, bytes) == BluetoothStatusCodes.SUCCESS
        } else {
            response.value = bytes
            link.notifyCharacteristicChanged(device, response, false)
        }
        if (!accepted) fail("Notification rejected")
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE,
    ])
    override fun release() {
        try {
            adapter.bluetoothLeAdvertiser?.stopAdvertising(advertising)
        } finally {
            server?.close()
            server = null
            peer = null
            pendingRequest = null
        }
    }
}
