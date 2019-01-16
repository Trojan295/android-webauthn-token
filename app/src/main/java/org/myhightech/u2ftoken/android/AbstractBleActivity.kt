package org.myhightech.u2ftoken.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AlertDialog.Builder
import android.support.v7.app.AppCompatActivity
import android.widget.Toast

import org.myhightech.u2ftoken.R
import org.myhightech.u2ftoken.ble.util.BleUtils

abstract class AbstractBleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BleUtils.isBluetoothEnabled(this)) {
            BleUtils.enableBluetooth(this)
            return
        }

        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                BleUtils.REQUEST_CODE_ACCESS_LOCATION_PERMISSION)

        if (!BleUtils.isBleSupported(this) || !BleUtils.isBlePeripheralSupported(this)) {
            // display alert and exit
            val alertDialog = Builder(this).create()
            alertDialog.setTitle(getString(R.string.not_supported))
            alertDialog.setMessage(getString(R.string.ble_perip_not_supported))
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok)
            ) { dialog, _ -> dialog.dismiss() }
            alertDialog.setOnDismissListener { finish() }
            alertDialog.show()
        } else {
            setupBlePeripheralProvider()
        }
    }

    internal abstract fun setupBlePeripheralProvider()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == BleUtils.REQUEST_CODE_BLUETOOTH_ENABLE || requestCode == BleUtils.REQUEST_CODE_ACCESS_LOCATION_PERMISSION) {
            if (!BleUtils.isBluetoothEnabled(this)) {
                // User selected NOT to use Bluetooth.
                // do nothing
                Toast.makeText(this, R.string.requires_bl_enabled, Toast.LENGTH_LONG).show()
                return
            }

            if (!BleUtils.isBleSupported(this) || !BleUtils.isBlePeripheralSupported(this)) {
                // display alert and exit
                val alertDialog = Builder(this).create()
                alertDialog.setTitle(getString(R.string.not_supported))
                alertDialog.setMessage(getString(R.string.ble_perip_not_supported))
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok)
                ) { dialog, _ -> dialog.dismiss() }
                alertDialog.setOnDismissListener { finish() }
                alertDialog.show()
            } else {
                setupBlePeripheralProvider()
            }
        }
    }
}
