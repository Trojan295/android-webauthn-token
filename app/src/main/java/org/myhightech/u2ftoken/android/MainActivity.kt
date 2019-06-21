package org.myhightech.u2ftoken.android

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import org.myhightech.u2ftoken.R
import org.myhightech.u2ftoken.ble.profiles.BluetoothReceiver


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = getString(R.string.app_name)

        findViewById<View>(R.id.tokenButton).setOnClickListener { startActivity(Intent(applicationContext, FIDO2Activity::class.java)) }

        BluetoothReceiver.registerReceivers(this)
    }
}
