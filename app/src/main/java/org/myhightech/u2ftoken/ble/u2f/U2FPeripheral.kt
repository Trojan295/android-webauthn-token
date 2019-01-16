package org.myhightech.u2ftoken.ble.u2f

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.text.TextUtils
import android.util.Log
import org.myhightech.u2ftoken.ble.util.BleUuidUtils
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class U2FPeripheral(context: Context) {
    private val tag: String = this.javaClass.simpleName
    private val debug: Boolean = true

    private val SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A)
    private val CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(0x2A29)
    private val CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(0x2A24)
    private val CHARACTERISTIC_FIRMWARE_REVISION = BleUuidUtils.fromShortValue(0x2A26)
    private val DEVICE_NAME_MAX_LENGTH = 20

    private var manufacturer = "MHT Corp."
    private var firmwareRevision = "0.1"
    var deviceName: String = "U2F Token"
        set(value) {
            val deviceNameBytes = value.toByteArray(StandardCharsets.UTF_8)
            field = if (deviceNameBytes.size > DEVICE_NAME_MAX_LENGTH) {
                val bytes = ByteArray(DEVICE_NAME_MAX_LENGTH)
                System.arraycopy(deviceNameBytes, 0, bytes, 0, 20)
                String(bytes, StandardCharsets.UTF_8)
            } else {
                value
            }
        }

    private val SERVICE_U2F_AUTHENTICATOR = BleUuidUtils.fromShortValue(0xFFFD)
    private val CHARACTERISTIC_U2F_CONTROL_POINT = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val CHARACTERISTIC_U2F_STATUS = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD = UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")

    private val serviceRevisionBitfield = byteArrayOf(64.toByte()) // 1.2 support
    private val controlPointLength = byteArrayOf(0x02, 0x00)

    private val handler = Handler(Looper.getMainLooper())
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val appContext = context.applicationContext
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private var gattServer: BluetoothGattServer? = null
    private val bluetoothDevicesMap = HashMap<String, BluetoothDevice>()

    private val advertiseCallback = NullAdvertiseCallback()

    private inner class NullAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.v(tag, "onStartSuccess:$settingsInEffect")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.v(tag, "onStartFailure:$errorCode")
        }
    }

    private var mBroadcastReceiver: BroadcastReceiver? = null

    private inner class MyBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action
            Log.v(tag, "onReceive action: " + action!!)

            if (!TextUtils.isEmpty(action)) {
                val bondedDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                when (action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (state == BluetoothDevice.BOND_BONDED) {
                        // successfully bonded
                        context.unregisterReceiver(this)
                        handler.post {
                            if (gattServer != null) {
                                gattServer!!.connect(bondedDevice, true)
                            }
                        }
                        Log.v(tag, "successfully bonded")
                    }
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> Log.v(tag, "BluetoothDevice.ACTION_PAIRING_REQUEST")
                }
            }
        }
    }

    private var mSetupServiceTask: SetupServiceTask? = null

    private inner class SetupServiceTask : Runnable {

        @Volatile
        private var mIsRunning: Boolean = false
        @Volatile
        var mServiceAdded: Boolean = false

        override fun run() {
            mIsRunning = true
            try {
                addService(setUpU2FAuthenticatorDevice())
                addService(setUpDeviceInformationService())
                lock.withLock {
                    if (mIsRunning) {

                    }
                }
            } catch (e: Exception) {
                Log.w(tag, e)
            }

            lock.withLock {
                mSetupServiceTask = null
            }
        }

        private fun addService(service: BluetoothGattService) {
            Log.v(tag, "addService:" + service.uuid)
            mServiceAdded = false
            try {
                lock.withLock {
                    if (gattServer!!.addService(service)) {
                        // wait until BluetoothGattServerCallback#onServiceAdded is called.
                        while (mIsRunning && !mServiceAdded) {
                            condition.await(1000, TimeUnit.MILLISECONDS)
                        }
                        if (mIsRunning) {
                            // additional wait for 30 millis to avoid issue on BluetoothGattServer#addService
                            condition.await(30, TimeUnit.MILLISECONDS)
                        }
                        Log.v(tag, "addService:" + service.uuid + " added.")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Adding Service failed", e)
                return
            }

            Log.w(tag, "Adding Service failed")
        }
    }

    private fun setUpDeviceInformationService(): BluetoothGattService {
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

    private fun setUpU2FAuthenticatorDevice(): BluetoothGattService {
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
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED)

            val clientCharacteristicConfigurationDescriptor = BluetoothGattDescriptor(
                    Companion.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                            or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE)
            clientCharacteristicConfigurationDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.addDescriptor(clientCharacteristicConfigurationDescriptor)

            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }
        run {
            val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED)
            if (!service.addCharacteristic(characteristic)) {
                throw RuntimeException("failed to add characteristic")
            }
        }

        return service
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice,
                                             status: Int, newState: Int) {

            super.onConnectionStateChange(device, status, newState)
            Log.v(tag, "onConnectionStateChange status: $status, newState: $newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.v(tag, "onConnectionStateChange:STATE_CONNECTED")
                    // check bond status
                    val bondState = device.bondState
                    Log.v(tag, "BluetoothProfile.STATE_CONNECTED bondState: $bondState")
                    if (bondState == BluetoothDevice.BOND_NONE) {
                        Log.v(tag, "onConnectionStateChange:BOND_NONE")
                        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                        appContext.registerReceiver(mBroadcastReceiver, filter)

                        device.createBond()
                    } else if (bondState == BluetoothDevice.BOND_BONDED) {
                        Log.v(tag, "onConnectionStateChange:BOND_BONDED")
                        handler.post {
                            if (gattServer != null) {
                                gattServer!!.connect(device, true)
                            }
                        }
                        synchronized(bluetoothDevicesMap) {
                            bluetoothDevicesMap.put(device.address, device)
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.v(tag, "onConnectionStateChange:STATE_DISCONNECTED")
                    val deviceAddress = device.address

                    synchronized(bluetoothDevicesMap) {
                        bluetoothDevicesMap.remove(deviceAddress)
                    }
                    // try reconnect immediately
                    handler.post {
                        if (gattServer != null) {
                            // gattServer.cancelConnection(device);
                            gattServer!!.connect(device, true)
                        }
                    }
                }

                else -> {
                }
            }// do nothing
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice,
                                                 requestId: Int, offset: Int,
                                                 characteristic: BluetoothGattCharacteristic) {

            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (gattServer == null) {
                return
            }
            Log.v(tag, "onCharacteristicReadRequest characteristic: " + characteristic.uuid + ", offset: " + offset)

            handler.post {
                val characteristicUuid = characteristic.uuid
                when {
                    BleUuidUtils.matches(CHARACTERISTIC_MANUFACTURER_NAME, characteristicUuid) -> gattServer!!.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, 0, manufacturer.toByteArray(StandardCharsets.UTF_8))

                    BleUuidUtils.matches(CHARACTERISTIC_FIRMWARE_REVISION, characteristicUuid) -> gattServer!!.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, 0, firmwareRevision.toByteArray(StandardCharsets.UTF_8))

                    BleUuidUtils.matches(CHARACTERISTIC_MODEL_NUMBER, characteristicUuid) -> gattServer!!.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, 0, deviceName.toByteArray(StandardCharsets.UTF_8))

                    BleUuidUtils.matches(CHARACTERISTIC_U2F_SERVICE_REVISION_BITFIELD, characteristicUuid) -> gattServer!!.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, 0, serviceRevisionBitfield)

                    BleUuidUtils.matches(CHARACTERISTIC_U2F_CONTROL_POINT_LENGTH, characteristicUuid) -> gattServer!!.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, 0, controlPointLength)

                    else -> gattServer!!.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, 0, characteristic.value)
                }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.v(tag, "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.uuid)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            Log.v(tag, "onCharacteristicWriteRequest characteristic: " + characteristic.uuid + ", value: " + Arrays.toString(value))

            if (gattServer == null) {
                return
            }

            if (responseNeeded) {
                gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            Log.v(tag, "onDescriptorWriteRequest descriptor: " + descriptor.uuid + ", value: " + Arrays.toString(value) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite)

            descriptor.value = value

            if (responseNeeded) {
                if (BleUuidUtils.matches(Companion.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION, descriptor.uuid)) {
                    // send empty
                    if (gattServer != null) {
                        gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
                    }
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            Log.v(tag, "onServiceAdded status: " + status + ", service: " + service.uuid)

            // workaround to avoid issue of BluetoothGattServer#addService
            // when adding multiple BluetoothGattServices continuously.
            lock.withLock {
                if (mSetupServiceTask != null) {
                    mSetupServiceTask!!.mServiceAdded = true
                    condition.signal()
                }
            }
            if (status != 0) {
                Log.w(tag, "onServiceAdded Adding Service failed..")
            }
        }
    }

    init {
        val appContext = context.applicationContext
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
                ?: throw UnsupportedOperationException("Bluetooth is not available.")
        if (!bluetoothAdapter.isEnabled) {
            throw UnsupportedOperationException("Bluetooth is disabled.")
        }

        Log.v(tag, "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported)
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        Log.v(tag, "bluetoothLeAdvertiser: $bluetoothLeAdvertiser")
        if (bluetoothLeAdvertiser == null) {
            throw UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.")
        }

        gattServer = bluetoothManager.openGattServer(appContext, gattServerCallback)
        if (gattServer == null) {
            throw UnsupportedOperationException("gattServer is null, check Bluetooth is ON.")
        }

        lock.withLock {
            mSetupServiceTask = SetupServiceTask(20)
            Thread(mSetupServiceTask, tag).start()
        }
    }

    fun startAdvertising() {
        registerBroadcast()
        handler.post {
            // set up advertising setting
            val advertiseSettings = AdvertiseSettings.Builder()
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .build()

            // set up advertising data
            val advertiseData = AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(true)
                    .setIncludeDeviceName(true)
                    .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                    .addServiceUuid(ParcelUuid.fromString(SERVICE_U2F_AUTHENTICATOR.toString()))
                    .build()

            // set up scan result
            val scanResult = AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                    .addServiceUuid(ParcelUuid.fromString(SERVICE_U2F_AUTHENTICATOR.toString()))
                    .build()

            Log.v(tag, "advertiseData: $advertiseData, scanResult: $scanResult")
            bluetoothLeAdvertiser.startAdvertising(
                    advertiseSettings, advertiseData, scanResult, advertiseCallback)
        }
    }

    fun stopAdvertising() {
        unRegisterBroadcast()
        handler.post {
            try {
                bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
            } catch (e: IllegalStateException) {
                // BT Adapter is not turned ON
                if (debug) Log.w(tag, e)
            }

            try {
                if (gattServer != null) {
                    val devices = getDevices()
                    for (device in devices) {
                        gattServer!!.cancelConnection(device)
                    }
                    gattServer!!.clearServices()
                    gattServer!!.close()
                    gattServer = null
                }
            } catch (e: IllegalStateException) {
                // BT Adapter is not turned ON
                if (debug) Log.w(tag, e)
            }
        }
    }

    private fun registerBroadcast() {
        lock.withLock {
            if (mBroadcastReceiver == null) {
                mBroadcastReceiver = MyBroadcastReceiver()
                val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                appContext.registerReceiver(mBroadcastReceiver, filter)
            }
        }
    }

    private fun unRegisterBroadcast() {
        lock.withLock {
            if (mBroadcastReceiver != null) {
                try {
                    appContext.unregisterReceiver(mBroadcastReceiver)
                } catch (e: Exception) {
                    if (debug) Log.w(tag, e)
                }

                mBroadcastReceiver = null
            }
        }
    }

    private fun getDevices(): Set<BluetoothDevice> {
        val deviceSet: Set<BluetoothDevice>
        synchronized(bluetoothDevicesMap) {
            deviceSet = HashSet<BluetoothDevice>(bluetoothDevicesMap.values)
        }
        return Collections.unmodifiableSet(deviceSet)
    }

    companion object {
        const val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = BleUuidUtils.fromShortValue(0x2902)
    }
}
