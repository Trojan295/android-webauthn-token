package org.myhightech.u2ftoken.fido2

import android.content.SharedPreferences
import android.util.Log
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import kotlinx.coroutines.channels.Channel
import org.myhightech.u2ftoken.crypto.AppKey
import java.security.MessageDigest
import kotlinx.coroutines.*
import java.util.*


interface FIDO2UserInterface {
    fun onTokenRegistration(relyingPartyId: String, callback: FIDO2UserCallback)
    fun registrationCompleted(relyingPartyId: String)
    fun onTokenAuthentication(relyingPartyId: String, callback: FIDO2UserCallback)
    fun authenticationCompeted(relyingPartyId: String)
}

interface FIDO2UserCallback {
    fun granted()
    fun denied()
}

interface FIDO2TokenCallback {
    fun sendMessage(message: ByteArray)
    fun sendKeepAlive()
}

class FIDO2Token(val aaGuid: ByteArray,
                 private val prefs: SharedPreferences,
                 private val userInterface: FIDO2UserInterface) {

    private val tag = javaClass.simpleName

    private var signCount = prefs.getInt("signCount", 0)
        set(value) {
            field = value
            prefs.edit()
                    .putInt("signCount", value)
                    .apply()
        }

    private fun signMessage(keyAlias: String, message: ByteArray): ByteArray {
        Log.v(tag, "signMessage")
        signCount++
        return AppKey.signMessage(keyAlias, message)
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

    fun getInfo(callback: FIDO2TokenCallback) {
        val response = AuthenticatorGetIntoResponse(arrayOf("FIDO_2_0"), aaGuid)
        Log.v(tag, "sending GET_INFO response")
        callback.sendMessage(response.serialize())
    }

    fun register(clientDataHash: ByteArray, relyingPartyId: String, credentialId: ByteArray,
                 callback: FIDO2TokenCallback) {
        Log.v(tag, "register FIDO2 token")
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
        userInterface.onTokenRegistration(relyingPartyId, object : FIDO2UserCallback {
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

        if (!runBlocking {
                    var result: Boolean? = null
                    while (result == null) {
                        delay(500L)
                        callback.sendKeepAlive()
                        result = channel.poll()
                    }
                    result
                }!!
        ) {
            Log.v(tag, "register, user denied")
            return
        }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(relyingPartyId.toByteArray())
        val digest = md.digest()

        val publicKey = AppKey.generateAppKey(relyingPartyId)
        val x = normalizeSize(publicKey.w.affineX.toByteArray())
        val y = normalizeSize(publicKey.w.affineY.toByteArray())

        Log.v(tag, "register, public key: ${Arrays.toString(publicKey.encoded)}")

        val flags = createFlags(register = true, userPresent = true, userVerified = true)

        val authenticatorData = AuthenticatorData(digest, flags, signCount,
                CredentialData(aaGuid, credentialId, ECCredentialPublicKey(x, y)))
        val dataToSign = byteArrayOf(*authenticatorData.serialize(), *clientDataHash)
        val attestationStatement = ES256PackedAttestationStatement(signMessage(relyingPartyId, dataToSign))

        val response = AuthenticatorMakeCredentials.Response(authenticatorData, attestationStatement)
        Log.v(tag, "register, sending response")
        callback.sendMessage(response.serialize())

        userInterface.registrationCompleted(relyingPartyId)
    }

    fun authenticate(credential: PublicKeyCredentialDescriptors, relyingPartyId: String, clientDataHash: ByteArray,
                     callback: FIDO2TokenCallback) {
        Log.v(tag, "authenticate using FIDO2 token at $relyingPartyId")
        val channel = Channel<Boolean>()
        userInterface.onTokenAuthentication(relyingPartyId, object : FIDO2UserCallback {
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

        if (!runBlocking {
                    var result: Boolean? = null
                    while (result == null) {
                        delay(500L)
                        callback.sendKeepAlive()
                        result = channel.poll()
                    }
                    result
                }!!
        ) {
            Log.v(tag, "authenticate, user denied")
            return
        }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(relyingPartyId.toByteArray())
        val digest = md.digest()

        val flags = createFlags(false, true, true)
        val authenticatorData = AuthenticatorData(digest, flags, signCount, null)

        val dataToSign = byteArrayOf(*authenticatorData.serialize(), *clientDataHash)
        val signature = signMessage(relyingPartyId, dataToSign)

        val response = AuthenticatorGetAssertion.Response(credential, authenticatorData, signature)
        Log.v(tag, "authenticate, sending response for $relyingPartyId")
        callback.sendMessage(response.serialize())

        userInterface.authenticationCompeted(relyingPartyId)
    }

    fun dispatch(data: ByteArray, tokenCallback: FIDO2TokenCallback) {
        val parser = CBORFactory().createParser(data.sliceArray(1 until data.size))
        Log.v(tag, "dispatch, command ${data[0]}")
        when(data[0]) {
            GET_INFO -> getInfo(tokenCallback)
            MAKE_CREDENTIALS -> {
                val req = AuthenticatorMakeCredentials.Request.parse(parser)
                register(req.clientDataHash, req.rp.id, req.user.id, tokenCallback)
            }
            GET_ASSERTION -> {
                val req = AuthenticatorGetAssertion.Request.parse(parser)
                authenticate(req.allowList[0], req.rpId, req.clientDataHash, tokenCallback)
            }
            else -> {
                val response = AuthenticatorErrorResponse.invalidCommand()
                tokenCallback.sendMessage(response.serialize())
            }
        }
    }

    companion object {
        const val MAKE_CREDENTIALS: Byte = 0x01
        const val GET_ASSERTION: Byte = 0x02
        const val GET_INFO: Byte = 0x04
    }
}
