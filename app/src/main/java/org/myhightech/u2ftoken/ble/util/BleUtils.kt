package org.myhightech.u2ftoken.ble.util

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object BleUtils {

    const val REQUEST_CODE_BLUETOOTH_ENABLE = 0xb1e
    const val REQUEST_CODE_ACCESS_LOCATION_PERMISSION = 0x4cee5

    fun isBleSupported(context: Context): Boolean {
        try {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } catch (ignored: Throwable) {
            // ignore exception
        }
        return false
    }

    fun isBlePeripheralSupported(context: Context): Boolean {
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        return bluetoothAdapter.isMultipleAdvertisementSupported
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        return adapter.isEnabled
    }

    fun enableBluetooth(activity: Activity) {
        activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_BLUETOOTH_ENABLE)
    }
}
