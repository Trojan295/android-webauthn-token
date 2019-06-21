package org.myhightech.u2ftoken.ble.profiles

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import org.myhightech.u2ftoken.ble.services.GattService
import java.util.*
import android.bluetooth.BluetoothGattService



class BLEProfile(context: Context, val services: Sequence<GattService>) {
    private val tag: String = this.javaClass.simpleName

    private val mBluetoothManager: BluetoothManager by lazy {
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        mBluetoothManager.adapter
    }

    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser by lazy {
        mBluetoothAdapter.bluetoothLeAdvertiser
    }

    private val mGattServer: BluetoothGattServer by lazy {
        mBluetoothManager.openGattServer(context.applicationContext, mGattServerCallback)
    }

    private val mAdvertiseCallback: AdvertiseCallback by lazy {
        LogAdvertiseCallback()
    }

    private inner class LogAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.v(tag, "Advertising started, settings :$settingsInEffect")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.v(tag, "Advertising failed, code: $errorCode")
        }
    }

    private val mGattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice,
                                             status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)

            when (newState) {
                BluetoothProfile.STATE_CONNECTED ->
                    Log.v(tag, "onConnectionStateChange, STATE_CONNECTED: ${device.name}")
                BluetoothProfile.STATE_DISCONNECTED ->
                    Log.v(tag, "onConnectionStateChange, STATE_DISCONNECTED: ${device.name}")
                else ->
                    Log.v(tag, "onConnectionStateChange: $newState")
            }
            mGattServer.cancelConnection(device)
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice,
                                                 requestId: Int, offset: Int,
                                                 characteristic: BluetoothGattCharacteristic) {
            Log.v(tag, "onCharacteristicReadRequest characteristic: " + characteristic.uuid + ", offset: " + offset)
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

            val characteristicUuid = characteristic.uuid
            services.forEach { service ->
                service.takeIf { it.getCharacteristics().contains(characteristicUuid) }
                        ?.onCharacteristicsRead(mGattServer, device, requestId, offset, characteristic)
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            Log.v(tag, "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.uuid)
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.v(tag, "onCharacteristicWriteRequest characteristic: " + characteristic.uuid + ", value: " + Arrays.toString(value))
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            val characteristicUuid = characteristic.uuid
            services.forEach { service ->
                service.takeIf { it.getCharacteristics().contains(characteristicUuid) }
                        ?.onCharacteristicsWrite(mGattServer, device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.v(tag, "onDescriptorWriteRequest descriptor: " + descriptor.uuid + ", value: " + Arrays.toString(value) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite)
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

            descriptor.value = value

            if (responseNeeded) {
                services.forEach { service ->
                    service.takeIf { it.getDescriptors().contains(descriptor.uuid) }
                            ?.onDescriptorWrite(mGattServer, device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
                }
            }
        }
    }

    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .build()
    }

    private fun buildAdvertiseData(): AdvertiseData {
        return AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .let { builder ->
                    services.map {it.getPrimaryServiceUUID()}
                            .forEach { builder.addServiceUuid(it) }
                    builder
                }
                .build()
    }

    fun startAdvertising() {
        Log.i(tag, "startAdvertising")
        val advertiseData = buildAdvertiseData()
        val advertiseSettings = buildAdvertiseSettings()
        Log.v(tag, "advertiseData: $advertiseData, advertiseSettings: $advertiseSettings")
        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback)
    }

    fun stopAdvertising() {
        Log.i(tag, "stopAdvertising")
        bluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback)
    }

    fun startGattServer() {
        services.map { it.setupService() }.forEach {
            mGattServer.addService(it)
            Thread.sleep(100)
        }
    }
}