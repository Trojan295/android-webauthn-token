package org.myhightech.u2ftoken.android

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import android.widget.Toast
import org.myhightech.u2ftoken.R
import org.myhightech.u2ftoken.ble.profiles.U2FProfile
import org.myhightech.u2ftoken.fido2.FIDOToken
import org.myhightech.u2ftoken.fido2.FIDOUserCallback
import org.myhightech.u2ftoken.fido2.FIDOUserInterface
import android.support.annotation.NonNull




class U2FActivity : AbstractBleActivity(), FIDOUserInterface {

    private var mU2FPeripheral: U2FProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_token)
        title = "FIDO2 Token"

        requestPermissions(arrayOf(Manifest.permission.USE_BIOMETRIC), 101)
    }

    override fun setupBlePeripheralProvider() {
        val fidoToken = FIDOToken(
                byteArrayOf(173.toByte(), 51, 225.toByte(), 30, 242.toByte(), 194.toByte(),
                        52, 188.toByte(), 136.toByte(), 149.toByte(), 129.toByte(), 254.toByte(), 124.toByte(),
                        76, 160.toByte(), 205.toByte())
                , this.getPreferences(Context.MODE_PRIVATE), this)

        mU2FPeripheral = U2FProfile(this, fidoToken)
        mU2FPeripheral!!.startAdvertising()
    }

    override fun onDestroy() {
        if (mU2FPeripheral != null) {
            mU2FPeripheral!!.stopAdvertising()
            mU2FPeripheral = null
        }
        super.onDestroy()
    }

    override fun onTokenRegistration(relyingPartyId: String, callback: FIDOUserCallback) {
            BiometricPrompt.Builder(applicationContext)
                    .setTitle(title)
                    .setSubtitle(relyingPartyId)
                    .setDescription(String.format("Do you want to register this device token in %s", relyingPartyId))
                    .setNegativeButton("Cancel", mainExecutor, DialogInterface.OnClickListener { dialog, which ->
                        callback.denied()
                    })
                    .build()
                    .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            callback.granted()
                        }

                        override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence) {
                            super.onAuthenticationHelp(helpCode, helpString)
                            callback.denied()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            callback.denied()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            callback.denied()
                        }
                    })
    }

    override fun registrationCompleted(relyingPartyId: String) {
    }

    override fun onTokenAuthentication(relyingPartyId: String, callback: FIDOUserCallback) {
    }

    override fun authenticationCompeted(relyingPartyId: String) {
    }
}
