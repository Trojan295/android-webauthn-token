package org.myhightech.u2ftoken.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class AppKey {

    companion object {
        fun generateAppKey(keyAlias: String): ECPublicKey {
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

            return keyPairGenerator.generateKeyPair().public as ECPublicKey
        }

        fun signMessage(keyAlias: String, message: ByteArray): ByteArray {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
            val entry = ks.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            val ecdsaSign = Signature.getInstance("SHA256withECDSA")
            ecdsaSign.initSign(entry.privateKey)
            ecdsaSign.update(message)
            return ecdsaSign.sign()
        }
    }
}