package org.myhightech.u2ftoken.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec


class AppKey {

    companion object {

        fun generateAppKey(keyAlias: String): KeyPair {

            val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            keyPairGenerator.initialize(
                    KeyGenParameterSpec.Builder(
                            keyAlias,
                            KeyProperties.PURPOSE_SIGN)
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setUserAuthenticationRequired(true)
                            .setUserAuthenticationValidityDurationSeconds(120)
                            .build())

            return keyPairGenerator.generateKeyPair()
        }

        fun signMessage(pair: KeyPair, message: ByteArray): ByteArray {
            val ecdsaSign = Signature.getInstance("SHA256withECDSA")
            ecdsaSign.initSign(pair.private)
            ecdsaSign.update(message)
            return ecdsaSign.sign()
        }
    }
}