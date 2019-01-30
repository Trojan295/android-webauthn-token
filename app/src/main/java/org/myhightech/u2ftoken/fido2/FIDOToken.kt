package org.myhightech.u2ftoken.fido2

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.channels.Channel
import org.myhightech.u2ftoken.crypto.AppKey
import java.security.KeyPair
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.ArrayList
import kotlinx.coroutines.*


interface FIDOUserInterface {
    fun onTokenRegistration(relyingPartyId: String, callback: FIDOUserCallback)
    fun registrationCompleted(relyingPartyId: String)
    fun onTokenAuthentication(relyingPartyId: String, callback: FIDOUserCallback)
    fun authenticationCompeted(relyingPartyId: String)
}

interface FIDOUserCallback {
    fun granted()
    fun denied()
}

class FIDOToken(val aaGuid: ByteArray,
                private val prefs: SharedPreferences,
                private val userInterface: FIDOUserInterface) {
    var tag = "FIDOToken"

    private val credentials = mutableMapOf<String, KeyPair>()
    private var signCount = prefs.getInt("signCount", 0)
        set(value) {
            field = value
            prefs.edit()
                    .putInt("signCount", value)
                    .apply()
        }

    private fun getKeyPair(credentialId: ByteArray): KeyPair {
        val keyAlias = credentialId.joinToString("") { x -> String.format("%02X", x) }
        return credentials.getOrPut(keyAlias) {
            Log.i(tag, "creating new key for $keyAlias")
            AppKey.generateAppKey(keyAlias)
        }
    }

    private fun signMessage(keyPair: KeyPair, message: ByteArray): ByteArray {
        signCount++
        return AppKey.signMessage(keyPair, message)
    }

    private fun createFlags(register: Boolean, userPresent: Boolean, userVerified: Boolean): Byte {
        var flags = 0
        if (register) {
            flags += 0x40
        }
        if (userPresent) {
            flags += 0x01
        }
        if (userVerified) {
            flags += 0x04
        }
        return flags.toByte()
    }

    fun register(clientDataHash: ByteArray, relyingPartyId: String, credentialId: ByteArray)
            : Pair<AuthenticatorData, AttestationStatement>? {
        val credentialString = credentialId.joinToString("") { x -> String.format("%02X", x) }

        Log.i(tag, "registering token at ${relyingPartyId} with credential ${credentialString}")

        fun normalizeSize(input: ByteArray): ByteArray {
            val result = ArrayList<Byte>()
            if (input[0] == 0x00.toByte()) {
                result.addAll(input.slice(1 until input.size))
            } else {
                result.addAll(input.toList())
            }
            return result.toByteArray()
        }

        val channel = Channel<Boolean>()
        userInterface.onTokenRegistration(relyingPartyId, object : FIDOUserCallback {
            override fun granted() {
                GlobalScope.launch {
                    channel.send(true)
                    channel.close()
                }
            }
            override fun denied() {
                GlobalScope.launch {
                    channel.send(false)
                    channel.close()
                }
            }
        })
        if (!runBlocking { channel.receive() }) {
            return null
        }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(relyingPartyId.toByteArray())
        val digest = md.digest()

        val keyPair = getKeyPair(credentialId)
        val publicKey = keyPair.public as ECPublicKey
        val x = normalizeSize(publicKey.w.affineX.toByteArray())
        val y = normalizeSize(publicKey.w.affineY.toByteArray())

        val flags = createFlags(true, true, true)

        val authenticatorData = AuthenticatorData(digest, flags, signCount,
                CredentialData(aaGuid, credentialId, ECCredentialPublicKey(x, y)))
        val dataToSign = byteArrayOf(*authenticatorData.serialize(), *clientDataHash)
        val attestationStatement = ES256PackedAttestationStatement(signMessage(keyPair, dataToSign))

        Log.i(tag, "registration object created")

        userInterface.registrationCompleted(relyingPartyId)

        return Pair(authenticatorData, attestationStatement)
    }

    fun authenticate(credentialId: ByteArray, relyingPartyId: String, clientDataHash: ByteArray)
            : Pair<AuthenticatorData, ByteArray>? {


        val channel = Channel<Boolean>()
        userInterface.onTokenAuthentication(relyingPartyId, object : FIDOUserCallback {
            override fun granted() {
                GlobalScope.launch {
                    channel.send(true)
                    channel.close()
                }
            }
            override fun denied() {
                GlobalScope.launch {
                    channel.send(false)
                    channel.close()
                }
            }
        })
        if (!runBlocking { channel.receive() }) {
            return null
        }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(relyingPartyId.toByteArray())
        val digest = md.digest()

        val keyPair = getKeyPair(credentialId)

        val flags = createFlags(false, true, true)
        val authenticatorData = AuthenticatorData(digest, flags, signCount, null)

        val dataToSign = byteArrayOf(*authenticatorData.serialize(), *clientDataHash)
        val signature = signMessage(keyPair, dataToSign)
        Log.i(tag, "assertion object created")

        userInterface.authenticationCompeted(relyingPartyId)

        return Pair(authenticatorData, signature)
    }
}
