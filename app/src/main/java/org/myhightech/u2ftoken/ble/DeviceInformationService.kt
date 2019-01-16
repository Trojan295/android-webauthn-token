package org.myhightech.u2ftoken.ble

import org.myhightech.u2ftoken.ble.util.BleUuidUtils

class DeviceInformationService {
    private val SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A)
    private val CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(0x2A29)
    private val CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(0x2A24)
    private val CHARACTERISTIC_FIRMWARE_REVISION = BleUuidUtils.fromShortValue(0x2A26)

    var manufacturer = "MHT Corp."
    var firmwareRevision = "0.1"
    var deviceName = "U2F Token"
}