package org.myhightech.u2ftoken.ble.services

import android.bluetooth.*
import android.os.ParcelUuid
import org.myhightech.u2ftoken.ble.util.BleUuidUtils
import java.nio.charset.StandardCharsets
import java.util.*

class DeviceInformationService : GattService {
    private val SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A)
    private val CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(0x2A29)
    private val CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(0x2A24)
    private val CHARACTERISTIC_FIRMWARE_REVISION = BleUuidUtils.fromShortValue(0x2A26)

    var manufacturer = "MHT Corp."
    var firmwareRevision = "0.1"
    var deviceName = "U2F Token"

    override fun getPrimaryServiceUUID(): ParcelUuid {
        return ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString())
    }

    override fun setupService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_MANUFACTURER_NAME,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_MODEL_NUMBER,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_FIRMWARE_REVISION,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        return service
    }

    override fun getCharacteristics(): Set<UUID> {
        return setOf(CHARACTERISTIC_MANUFACTURER_NAME, CHARACTERISTIC_MODEL_NUMBER,
                CHARACTERISTIC_FIRMWARE_REVISION)
    }

    override fun getDescriptors(): Set<UUID> {
        return setOf()
    }

    override fun onCharacteristicsRead(gattServer: BluetoothGattServer, device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
        val characteristicUuid = characteristic.uuid
        when {
            BleUuidUtils.matches(CHARACTERISTIC_MANUFACTURER_NAME, characteristicUuid) -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, 0, manufacturer.toByteArray())

            BleUuidUtils.matches(CHARACTERISTIC_FIRMWARE_REVISION, characteristicUuid) -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, 0, firmwareRevision.toByteArray())

            BleUuidUtils.matches(CHARACTERISTIC_MODEL_NUMBER, characteristicUuid) -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, 0, deviceName.toByteArray())
        }
    }

    override fun onCharacteristicsWrite(gattServer: BluetoothGattServer, device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
        if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
        }
    }

    override fun onDescriptorWrite(gattServer: BluetoothGattServer,
                                   device: BluetoothDevice,
                                   requestId: Int,
                                   descriptor: BluetoothGattDescriptor,
                                   preparedWrite: Boolean,
                                   responseNeeded: Boolean,
                                   offset: Int,
                                   value: ByteArray) {
        if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
        }
    }
}