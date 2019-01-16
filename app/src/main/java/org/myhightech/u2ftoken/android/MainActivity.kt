package org.myhightech.u2ftoken.android

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import org.myhightech.u2ftoken.R


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = getString(R.string.ble_hid)

        findViewById<View>(R.id.keyboardButton).setOnClickListener { startActivity(Intent(applicationContext, U2FActivity::class.java)) }
    }
}
