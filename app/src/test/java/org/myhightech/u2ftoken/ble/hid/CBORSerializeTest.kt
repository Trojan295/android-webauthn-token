package org.myhightech.u2ftoken.ble.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.myhightech.u2ftoken.ble.services.FIDO2AuthenticatorService

class CBORSerializeTest {
    @Test
    fun testGetInfoResponse() {
        val aaguid = byteArrayOf(173.toByte(), 51, 225.toByte(), 30, 242.toByte(), 194.toByte(),
                52, 188.toByte(), 136.toByte(), 149.toByte(), 129.toByte(), 254.toByte(), 124.toByte(),
                76, 160.toByte(), 205.toByte())
        val data = FIDO2AuthenticatorService.GetIntoResponse(arrayOf("FIDO_2_0"),
                aaguid)

        val res = data.toCbor()

        assertArrayEquals(byteArrayOf(-94, 1, -127, 104, 70, 73, 68, 79, 95, 50, 95, 48, 3, 80,
                -83, 51, -31, 30, -14, -62, 52, -68, -120, -107, -127, -2, 124, 76, -96, -51),
                res)
    }
}