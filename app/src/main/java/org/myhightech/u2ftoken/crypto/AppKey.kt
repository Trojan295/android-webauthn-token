package org.myhightech.u2ftoken.crypto

import org.spongycastle.asn1.x500.X500Name
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo
import org.spongycastle.cert.X509v3CertificateBuilder
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.*
import android.security.keystore.KeyProperties
import android.security.keystore.KeyGenParameterSpec


class AppKey {

    companion object {

        fun generateAppKey(): KeyPair {

            val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            keyPairGenerator.initialize(
                    KeyGenParameterSpec.Builder(
                            "u2fkeytest",
                            KeyProperties.PURPOSE_SIGN)
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setUserAuthenticationRequired(true)
                            .setUserAuthenticationValidityDurationSeconds(5 * 60)
                            .build())

            return keyPairGenerator.generateKeyPair()
        }

        fun generateCertificate(pair: KeyPair): X509Certificate {
            val subPubKeyInfo = SubjectPublicKeyInfo.getInstance(pair.public.encoded)
            val name = X500Name("C=PL,O=Org")
            val from = Date()
            val to = Date(from.time + 365 * 86400000L)
            val sn = BigInteger(64, SecureRandom())
            val v3CertGen = X509v3CertificateBuilder(name, sn, from, to, name, subPubKeyInfo)

            val signer = JcaContentSignerBuilder("SHA256WithECDSA")
                    .build(pair.private)

            val certificateHolder = v3CertGen.build(signer)
            return JcaX509CertificateConverter().getCertificate(certificateHolder)
        }

        fun signMessage(pair: KeyPair, message: ByteArray): ByteArray {
            val ecdsaSign = Signature.getInstance("SHA256withECDSA")
            ecdsaSign.initSign(pair.private)
            ecdsaSign.update(message)
            return ecdsaSign.sign()
        }
    }
}