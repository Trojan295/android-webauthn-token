package org.myhightech.u2ftoken.ble.hid

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.myhightech.u2ftoken.fido2.AuthenticatorGetIntoResponse
import org.myhightech.u2ftoken.fido2.AuthenticatorMakeCredentialsRequest
import java.util.*

class CBORTest {

    @Test
    fun deserializeMakeCredentials() {
        val data = byteArrayOf(-91, 1, 88, 32, -73, -9, -43, 118, -3, -114, 64, -74, -112, 19, -24, 101, -40, 18, -128, 13, 61, -2, -71, -39, 4, 98, 49, -28, 116, -57, -99, -24, -19, -107, 62, -77, 2, -94, 98, 105, 100, 108, 119, 101, 98, 97, 117, 116, 104, 110, 46, 111, 114, 103, 100, 110, 97, 109, 101, 108, 87, 101, 98, 65, 117, 116, 104, 110, 46, 111, 114, 103, 3, -93, 98, 105, 100, 80, 3, -93, -48, -78, -101, -108, 11, -7, 28, -96, 125, -108, -94, -94, -126, 91, 100, 110, 97, 109, 101, 101, 100, 97, 115, 100, 100, 107, 100, 105, 115, 112, 108, 97, 121, 78, 97, 109, 101, 101, 100, 97, 115, 100, 100, 4, -126, -94, 99, 97, 108, 103, 38, 100, 116, 121, 112, 101, 106, 112, 117, 98, 108, 105, 99, 45, 107, 101, 121, -94, 99, 97, 108, 103, 57, 1, 0, 100, 116, 121, 112, 101, 106, 112, 117, 98, 108, 105, 99, 45, 107, 101, 121, 5, -128)
        val parser = CBORFactory().createParser(data)
        val request = AuthenticatorMakeCredentialsRequest.parse(parser)

        val response = request.response()
        val bytes = response.serialize()

        for (b in bytes) {
            print(String.format("%02x ", b))
        }
        print("\n")
    }

    @Test
    fun serializeGetInfo() {
        val AAGUID = byteArrayOf(173.toByte(), 51, 225.toByte(), 30, 242.toByte(), 194.toByte(),
                52, 188.toByte(), 136.toByte(), 149.toByte(), 129.toByte(), 254.toByte(), 124.toByte(),
                76, 160.toByte(), 205.toByte())

        val res = AuthenticatorGetIntoResponse(arrayOf("FIDO_2_0"), AAGUID)
        val bytes = res.serialize()
        assertArrayEquals(
                byteArrayOf(0, -94, 1, -127, 104, 70, 73, 68, 79, 95, 50, 95, 48, 3, 80, -83, 51, -31, 30, -14, -62, 52, -68, -120, -107, -127, -2, 124, 76, -96, -51),
                bytes
        )
    }
}