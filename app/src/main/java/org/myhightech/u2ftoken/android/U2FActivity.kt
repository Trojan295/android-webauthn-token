package org.myhightech.u2ftoken.android

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import org.myhightech.u2ftoken.R
import org.myhightech.u2ftoken.ble.profiles.BLEProfile
import org.myhightech.u2ftoken.ble.services.DeviceInformationService
import org.myhightech.u2ftoken.ble.services.FIDO2AuthenticatorService
import org.myhightech.u2ftoken.fido2.FIDO2Token
import org.myhightech.u2ftoken.fido2.FIDO2UserCallback
import org.myhightech.u2ftoken.fido2.FIDO2UserInterface


class U2FActivity : AbstractBleActivity(), FIDO2UserInterface {

    private var mBLEPeripheral: BLEProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_token)
        title = "FIDO2 Token"

        requestPermissions(arrayOf(Manifest.permission.USE_BIOMETRIC), 101)
    }

    override fun setupBlePeripheralProvider() {


        val fidoToken = FIDO2Token(
                byteArrayOf(173.toByte(), 51, 225.toByte(), 30, 242.toByte(), 194.toByte(),
                        52, 188.toByte(), 136.toByte(), 149.toByte(), 129.toByte(), 254.toByte(), 124.toByte(),
                        76, 160.toByte(), 205.toByte())
                , this.getPreferences(Context.MODE_PRIVATE), this)

        mBLEPeripheral = BLEProfile(this, sequenceOf(DeviceInformationService(), FIDO2AuthenticatorService(fidoToken)))
        mBLEPeripheral!!.startAdvertising()
    }

    override fun onDestroy() {
        if (mBLEPeripheral != null) {
            mBLEPeripheral!!.stopAdvertising()
            mBLEPeripheral = null
        }
        super.onDestroy()
    }

    override fun onTokenRegistration(relyingPartyId: String, callback: FIDO2UserCallback) {
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

    override fun onTokenAuthentication(relyingPartyId: String, callback: FIDO2UserCallback) {
        BiometricPrompt.Builder(applicationContext)
                .setTitle(title)
                .setSubtitle(relyingPartyId)
                .setDescription(String.format("Do you want to authenticate in %s", relyingPartyId))
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

    override fun authenticationCompeted(relyingPartyId: String) {
    }
}
