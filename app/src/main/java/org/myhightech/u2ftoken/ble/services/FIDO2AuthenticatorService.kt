package org.myhightech.u2ftoken.ble.services

import android.bluetooth.*
import android.os.ParcelUuid
import android.util.Log
import com.google.common.primitives.Shorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.myhightech.u2ftoken.ble.util.BleUuidUtils
import org.myhightech.u2ftoken.fido2.FIDORequest
import org.myhightech.u2ftoken.fido2.FIDOToken
import java.util.*
import kotlin.experimental.and

class FIDO2AuthenticatorService(private val fidoToken: FIDOToken) : GattService {
    private val tag = javaClass.simpleName

    private val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = BleUuidUtils.fromShortValue(0x2902)

    private val SERVICE_U2F_AUTHENTICATOR = BleUuidUtils.fromShortValue(0xFFFD)
    private val CHARACTERISTIC_U2F_CONTROL_POINT = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val CHARACTERISTIC_U2F_STATUS = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD = UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")

    private val serviceRevisionBitfield = byteArrayOf(0x20.toByte())  // FIDO2 support
    private val controlPointLength = byteArrayOf(0x02, 0x00)        // 512 byte packets

    private var fidoPacket: FIDO2Packet? = null

    private var fidoStatusCharacteristic: BluetoothGattCharacteristic? = null

    override fun getPrimaryServiceUUID(): ParcelUuid {
        return ParcelUuid.fromString(SERVICE_U2F_AUTHENTICATOR.toString())
    }

    override fun setupService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_U2F_AUTHENTICATOR, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_U2F_CONTROL_POINT,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED)

            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_U2F_STATUS,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)

            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                    DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                            or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
            clientCharacteristicConfigurationDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)

            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }

            fidoStatusCharacteristic = characteristic
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_READ)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
                            or BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }

        return service
    }

    override fun getCharacteristics(): Set<UUID> {
        return setOf(CHARACTERISTIC_U2F_CONTROL_POINT, CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH,
                CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD, CHARACTERISTIC_U2F_STATUS)
    }

    override fun getDescriptors(): Set<UUID> {
        return setOf(DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION)
    }

    override fun onCharacteristicsRead(gattServer: BluetoothGattServer, device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
        Log.v(tag, "onCharacteristicsRead uuid: ${characteristic.uuid}")

        when (characteristic.uuid) {
            CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, serviceRevisionBitfield)

            CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, controlPointLength)

            else -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
        }
    }

    data class FIDO2Packet(val command: Byte, val length: Short, val data: MutableList<Byte>)

    override fun onCharacteristicsWrite(gattServer: BluetoothGattServer,
                                        device: BluetoothDevice,
                                        requestId: Int,
                                        characteristic: BluetoothGattCharacteristic,
                                        preparedWrite: Boolean,
                                        responseNeeded: Boolean,
                                        offset: Int,
                                        value: ByteArray) {
        when(characteristic.uuid) {
            CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD -> gattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())

            CHARACTERISTIC_U2F_CONTROL_POINT -> {
                if (value[0] and 0x80.toByte() == 0x80.toByte()) {
                    assert(fidoPacket == null)
                    val command = value[0]
                    val length = Shorts.fromByteArray(value.sliceArray(1..2))
                    val data = value.slice(3 until value.size)
                    fidoPacket = FIDO2Packet(command, length, data.toMutableList())
                } else {
                    assert(fidoPacket != null)
                    val data = value.slice(1 until value.size)
                    fidoPacket!!.data.addAll(data)
                }

                fidoPacket!!.let {
                    if (it.length.toInt() == it.data.size) {
                        val request = FIDORequest.parse(it.data.toByteArray())

                        GlobalScope.launch(Dispatchers.Default) {
                            val response = request.response(fidoToken)

                            val responseBytes = response.serialize()
                            val responseSize = responseBytes.size.toShort()
                            val result = byteArrayOf(-125, *Shorts.toByteArray(responseSize), *responseBytes)

                            fidoStatusCharacteristic!!.value = result
                            gattServer.notifyCharacteristicChanged(device, fidoStatusCharacteristic!!, false)
                        }
                    }
                }
            }
        }

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
        descriptor.value = value
        if (responseNeeded) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
        }
    }
}