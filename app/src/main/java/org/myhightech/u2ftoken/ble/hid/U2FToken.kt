package org.myhightech.u2ftoken.ble.hid

import android.util.Log
import com.google.common.primitives.Ints
import org.myhightech.u2ftoken.crypto.AppKey
import java.util.*
import java.security.interfaces.ECPublicKey
import kotlin.experimental.and


class U2FToken {

    private val tag = U2FToken::class.java.simpleName

    val pair = AppKey.generateAppKey()

    var counter: Int = 0

    fun process(input: ByteArray): ByteArray {
        Log.v(tag, "process input: ${Arrays.toString(input)}")
        val request = U2FProtocol.parseRequest(input)
        val response = processRequest(request)
        val output = response.serialize()
        Log.v(tag, "process output: ${Arrays.toString(output)}")
        return output
    }

    private fun decodeECCPublicKeyX509(publicKey: ECPublicKey): ByteArray {

        fun normalizeSize(input: ByteArray): ByteArray {
            val result = ArrayList<Byte>()
            if (input[0] == 0x00.toByte()) {
                result.addAll(input.slice(1 until input.size))
            } else {
                result.addAll(input.toList())
            }
            return result.toByteArray()
        }

        val publicKeyBytes = ArrayList<Byte>(65)
        val w = publicKey.w
        val x = normalizeSize(w.affineX.toByteArray())
        val y = normalizeSize(w.affineY.toByteArray())
        publicKeyBytes.add(0x04)
        publicKeyBytes.addAll(x.toTypedArray())
        publicKeyBytes.addAll(y.toTypedArray())

        return publicKeyBytes.toByteArray()
    }

    private fun processRequest(request: U2FProtocol.U2FRequest): U2FProtocol.U2FResponse {
        return when {
            request.command == U2FCommand.VERSION -> {
                Log.i(tag, "processRequest command: VERSION")
                U2FProtocol.U2FResponse("U2F_V2".toByteArray(), U2FStatusCode.SW_NO_ERROR)
            }
            request.command == U2FCommand.REGISTER -> {
                Log.i(tag, "processRequest command: REGISTER")

                val challenge = request.data.sliceArray(IntRange(0, 31))
                val application = request.data.sliceArray(IntRange(32, 63))
                val certificate = AppKey.generateCertificate(pair)
                val keyHandle = byteArrayOf(0x02)
                val uncompressedPublicKey = decodeECCPublicKeyX509(pair.public as ECPublicKey)

                val dataToSign = ArrayList<Byte>()
                dataToSign.add(0x00)
                dataToSign.addAll(application.toTypedArray())
                dataToSign.addAll(challenge.toTypedArray())
                dataToSign.addAll(keyHandle.toTypedArray())
                dataToSign.addAll(uncompressedPublicKey.toTypedArray())

                val signature = AppKey.signMessage(pair, dataToSign.toByteArray())

                val response = ArrayList<Byte>()
                response.add(0x05)
                response.addAll(uncompressedPublicKey.toTypedArray())
                response.add(keyHandle.size.toByte())
                response.addAll(keyHandle.toTypedArray())
                response.addAll(certificate.encoded.toTypedArray())
                response.addAll(signature.toTypedArray())

                U2FProtocol.U2FResponse(response.toByteArray(), U2FStatusCode.SW_NO_ERROR)
            }
            request.command == U2FCommand.AUTHENTICATE -> {
                Log.i(tag, "processRequest command: AUTHENTICATE")
                val controlByte = request.parameter1
                val challenge = request.data.sliceArray(0 until 32)
                val application = request.data.sliceArray(32 until 64)
                //val keyHandleLength = request.data[64]
                //val keyHandle = request.data.sliceArray(65 until 65 + keyHandleLength)

                when (controlByte) {
                    0x07.toByte() -> U2FProtocol.U2FResponse(byteArrayOf(), U2FStatusCode.SW_CONDITIONS_NOT_SATISFIED)

                    0x03.toByte(), 0x08.toByte() -> {
                        val userPresence = controlByte and 0x01.toByte()

                        val dataToSign = ArrayList<Byte>()
                        dataToSign.addAll(application.toTypedArray())
                        dataToSign.add(userPresence)
                        dataToSign.addAll(Ints.toByteArray(counter).toTypedArray())
                        dataToSign.addAll(challenge.toTypedArray())
                        val signature =  AppKey.signMessage(pair, dataToSign.toByteArray())

                        val response = ArrayList<Byte>()
                        response.add(userPresence)
                        response.addAll(Ints.toByteArray(counter).toTypedArray())
                        response.addAll(signature.toTypedArray())

                        counter += 1
                        U2FProtocol.U2FResponse(response.toByteArray(), U2FStatusCode.SW_NO_ERROR)
                    }

                    else -> U2FProtocol.U2FResponse(byteArrayOf(), U2FStatusCode.SW_WRONG_DATA)
                }
            }
            else -> {
                Log.i(tag, "process command: UNKNOWN")
                U2FProtocol.U2FResponse(byteArrayOf(), U2FStatusCode.SW_INS_NOT_SUPPORTED)
            }
        }
    }
}