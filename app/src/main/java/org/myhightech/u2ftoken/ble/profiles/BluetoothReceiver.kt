package org.myhightech.u2ftoken.ble.profiles

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.support.v4.content.ContextCompat.startActivity




class BluetoothReceiver : BroadcastReceiver() {
    private val tag = javaClass.simpleName

    companion object {
        @Volatile
        private var receiversRegistered = false

        fun registerReceivers(contextIn: Context) {
            if (receiversRegistered) return

            val context = contextIn.applicationContext
            val receiver = BluetoothReceiver()

            val bluetoothEvents = IntentFilter()
            bluetoothEvents.addAction("android.bluetooth.device.action.ACL_CONNECTED")
            bluetoothEvents.addAction("android.bluetooth.device.action.PAIRING_REQUEST")
            bluetoothEvents.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
            bluetoothEvents.priority = IntentFilter.SYSTEM_HIGH_PRIORITY

            context.registerReceiver(receiver, bluetoothEvents)

            Log.i(BluetoothReceiver.toString(), "Registered receivers from " + contextIn.javaClass.name)
            receiversRegistered = true
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.v(tag, "onReceive action: " + intent?.action!!)
        val action = intent.action

        if (!TextUtils.isEmpty(action)) {
            val bondedDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            when (action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (state == BluetoothDevice.BOND_BONDED) {
                    Log.v(tag, "successfully bonded, device: ${bondedDevice.name}")
                } else {
                    Log.v(tag, "bond status: $state")
                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.PAIRING_VARIANT_PIN)
                    //Log.v(tag, "BluetoothDevice.ACTION_PAIRING_REQUEST, variant: $variant")
                }
            }
        }
    }

    fun pairDevice(context: Context, device: BluetoothDevice) {
        val intent = Intent("android.bluetooth.device.action.PAIRING_REQUEST")
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device)
        intent.putExtra("android.bluetooth.device.extra.PAIRING_VARIANT",
                BluetoothDevice.PAIRING_VARIANT_PIN)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(context, intent, Bundle())
    }
}