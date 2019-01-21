package org.myhightech.u2ftoken.ble.services

import android.bluetooth.*
import android.os.ParcelUuid
import org.myhightech.u2ftoken.ble.util.BleUuidUtils
import java.util.*

interface GattService {
    fun getPrimaryServiceUUID(): ParcelUuid
    fun setupService(): BluetoothGattService

    fun getCharacteristics(): Set<UUID>
    fun getDescriptors(): Set<UUID>

    fun onCharacteristicsRead(gattServer: BluetoothGattServer,
                              device: BluetoothDevice,
                              requestId: Int, offset: Int,
                              characteristic: BluetoothGattCharacteristic)

    fun onCharacteristicsWrite(gattServer: BluetoothGattServer,
                               device: BluetoothDevice,
                               requestId: Int,
                               characteristic: BluetoothGattCharacteristic,
                               preparedWrite: Boolean,
                               responseNeeded: Boolean,
                               offset: Int, value: ByteArray)

    fun onDescriptorWrite(gattServer: BluetoothGattServer,
                          device: BluetoothDevice,
                          requestId: Int,
                          descriptor: BluetoothGattDescriptor,
                          preparedWrite: Boolean,
                          responseNeeded: Boolean,
                          offset: Int,
                          value: ByteArray)
}