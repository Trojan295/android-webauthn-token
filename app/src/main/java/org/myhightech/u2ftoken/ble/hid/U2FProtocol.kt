package org.myhightech.u2ftoken.ble.hid

import com.google.common.primitives.Shorts

enum class U2FCommand(val code: Byte) {
    REGISTER(0x01),
    AUTHENTICATE(0x02),
    VERSION(0x03)
}

enum class U2FStatusCode(val code: Short) {
    SW_NO_ERROR(0x9000.toShort()),
    SW_CONDITIONS_NOT_SATISFIED(0x6985.toShort()),
    SW_INS_NOT_SUPPORTED(0x6D00.toShort()),
    SW_WRONG_DATA(0x6A80.toShort())
}

object U2FProtocol {
    class U2FRequest(val command: U2FCommand,
                     val parameter1: Byte,
                     val parameter2: Byte,
                     val data: ByteArray,
                     val responseLength: Int?)

    fun parseRequest(input: ByteArray): U2FRequest {

        val length = Shorts.fromByteArray(input.sliceArray(IntRange(5,6)))
        val data = input.sliceArray(IntRange(7, 7+length-1))

        var responseLength: Int? = null
        if (5+length != input.size) {
            responseLength = input[6+length].toInt()
        }

        val request = U2FRequest(
                U2FCommand.values().find { x -> x.code == input[1] }!!,
                input[2], input[3], data, responseLength)
        return request
    }

    class U2FResponse(val data: ByteArray, val code: U2FStatusCode) {
        fun serialize(): ByteArray {
            val response = ByteArray(data.size+2)
            data.copyInto(response)
            Shorts.toByteArray(code.code).copyInto(response, data.size)
            return response
        }
    }
}