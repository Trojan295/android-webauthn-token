package org.myhightech.u2ftoken.ble.profiles

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
import android.text.TextUtils
import android.util.Log
import org.myhightech.u2ftoken.ble.services.DeviceInformationService
import org.myhightech.u2ftoken.ble.services.FIDO2AuthenticatorService
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class U2FProfile(context: Context) {
    private val tag: String = this.javaClass.simpleName
    private val debug: Boolean = true

    private val services = listOf(DeviceInformationService(), FIDO2AuthenticatorService())

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
        var mIsRunning: Boolean = false
        @Volatile
        var mServiceAdded: Boolean = false

        override fun run() {
            mIsRunning = true
            try {
                addService(services[0].setupService())
                addService(services[1].setupService())
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
                    handler.post {
                        if (gattServer != null) {
                            gattServer!!.connect(device, true)
                        }
                    }
                }

                else -> {
                }
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice,
                                                 requestId: Int, offset: Int,
                                                 characteristic: BluetoothGattCharacteristic) {
            Log.v(tag, "onCharacteristicReadRequest characteristic: " + characteristic.uuid + ", offset: " + offset)
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (gattServer == null) {
                return
            }

            handler.post {
                val characteristicUuid = characteristic.uuid
                services.forEach { service ->
                    service.takeIf { it.getCharacteristics().contains(characteristicUuid) }
                            ?.onCharacteristicsRead(gattServer!!, device, requestId, offset, characteristic)
                }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            Log.v(tag, "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.uuid)
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.v(tag, "onCharacteristicWriteRequest characteristic: " + characteristic.uuid + ", value: " + Arrays.toString(value))
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            if (gattServer == null) {
                return
            }

            handler.post {
                val characteristicUuid = characteristic.uuid
                services.forEach { service ->
                    service.takeIf { it.getCharacteristics().contains(characteristicUuid) }
                            ?.onCharacteristicsWrite(gattServer!!, device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                }
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.v(tag, "onDescriptorWriteRequest descriptor: " + descriptor.uuid + ", value: " + Arrays.toString(value) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite)
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

            descriptor.value = value

            if (responseNeeded) {
                services.forEach { service ->
                    service.takeIf { it.getDescriptors().contains(descriptor.uuid) }
                            ?.onDescriptorWrite(gattServer!!, device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            Log.v(tag, "onServiceAdded status: " + status + ", service: " + service.uuid)

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
            mSetupServiceTask = SetupServiceTask()
            Thread(mSetupServiceTask, tag).start()
        }
    }

    fun startAdvertising() {
        Log.i(tag, "startAdvertising")
        registerBroadcast()
        handler.post {
            val advertiseSettings = AdvertiseSettings.Builder()
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .build()

            val advertiseData = AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(true)
                    .addServiceUuid(services[0].getPrimaryServiceUUID())
                    .addServiceUuid(services[1].getPrimaryServiceUUID())
                    .build()

            // set up scan result
            val scanResult = AdvertiseData.Builder()
                    .addServiceUuid(services[0].getPrimaryServiceUUID())
                    .addServiceUuid(services[1].getPrimaryServiceUUID())
                    .build()

            Log.v(tag, "advertiseData: $advertiseData, scanResult: $scanResult")
            bluetoothLeAdvertiser.startAdvertising(
                    advertiseSettings, advertiseData, scanResult, advertiseCallback)
        }
    }

    fun stopAdvertising() {
        Log.i(tag, "stopAdvertising")
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
}
