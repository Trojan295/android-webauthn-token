package org.myhightech.u2ftoken.ble.hid

import com.google.common.primitives.Shorts
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class U2FHIDPacketTest {

    @Test
    fun testDeserializeU2FInitPacket() {
        val packetBytes = byteArrayOf(0, -1, -1, -1, -1, -122, 0, 8, 94, 113, -63, -66, 45, -21, 64, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val packet = U2FHID.deserializeU2FPacket(packetBytes) as U2FHID.U2FHIDInitPacket

        assertEquals(0xffffffff.toInt(), packet.channel)
        assertEquals(0x86.toByte(), packet.command)
        assertEquals(8.toShort(), packet.length)
        assertArrayEquals(packetBytes.sliceArray(IntRange(8, 64)), packet.data)
    }

    @Test
    fun testSerializeU2FInitPacket() {
        val packet = U2FHID.U2FHIDInitPacket(0xffffffff.toInt(), 10.toByte(), 2, byteArrayOf(1, 2))

        val packetBytes = packet.serialize()

        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), packetBytes.sliceArray(0..3))
        assertEquals(10.toByte(), packetBytes[4])
        assertArrayEquals(Shorts.toByteArray(2), packetBytes.sliceArray(5..6))
        assertArrayEquals(byteArrayOf(1,2), packetBytes.sliceArray(7..8))
    }

    @Test
    fun testDeserializeU2FContPacket() {
        val packetBytes = byteArrayOf(0, -1, -1, -1, -1, 1, 0, 73, 0, 1, 3, 0, 0, 0, 64, -117, -49, -69, -119, -34, 17, -32, -33, 5, -85, -90, -25, -31, -121, 111, 97, -80, 117, -14, 77, -46, -48, -79, -8, 1, -45, 103, 12, 74, 92, 57, 44, 85, 103, 59, 81, 56, -52, -112, -45, -73, -13, 43, -3, -83, 106, 56, -88, -19, -41)

        val packet = U2FHID.deserializeU2FPacket(packetBytes) as U2FHID.U2FHIDContPacket

        assertEquals(0xffffffff.toInt(), packet.channel)
        assertEquals(0x01.toByte(), packet.sequence)
        assertArrayEquals(packetBytes.sliceArray(IntRange(6, 64)), packet.data)
    }

    @Test
    fun testSerializeU2FContPacket() {
        val packet = U2FHID.U2FHIDContPacket(0xffffffff.toInt(), 1, byteArrayOf(1, 2))

        val packetBytes = packet.serialize()

        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), packetBytes.sliceArray(0..3))
        assertEquals(1.toByte(), packetBytes[4])
        assertArrayEquals(byteArrayOf(1,2), packetBytes.sliceArray(5..6))
    }

    @Test
    fun testCombineU2FPackets() {
        val packets = arrayListOf(
                U2FHID.U2FHIDInitPacket(0xffffffff.toInt(), -122, 4.toShort(), byteArrayOf(1,2,3)),
                U2FHID.U2FHIDContPacket(0xffffffff.toInt(), 1, byteArrayOf(4,0,0))
        )

        val packet = U2FHID.combinePackets(packets)

        assertEquals(0xffffffff.toInt(), packet.channel)
        assertEquals((-122).toByte(), packet.command)
        assertArrayEquals(byteArrayOf(1,2,3,4), packet.data)
    }

    @Test
    fun testSplitU2FPackets() {
        val payload = (1..100).map { it.toByte() }.toByteArray()
        val bigPacket = U2FHID.U2FHIDMessageResponse(payload)

        val packets = bigPacket.splitPacket(0xffffffff.toInt(), (-125).toByte())

        assertEquals(2, packets.size)
        val initPacket = packets[0] as U2FHID.U2FHIDInitPacket
        val contPacket = packets[1] as U2FHID.U2FHIDContPacket

        assertEquals(100.toShort(), initPacket.length)
        assertEquals(0xffffffff.toInt(), initPacket.channel)
        assertEquals((-125).toByte(), initPacket.command)
        assertArrayEquals((1..57).map { it.toByte() }.toByteArray(), initPacket.data)

        assertEquals(0xffffffff.toInt(), contPacket.channel)
        assertEquals(0.toByte(), contPacket.sequence)
        assertArrayEquals((58..100).map { it.toByte() }.toByteArray(), contPacket.data)
    }

    @Test
    fun testSerializeU2FInitPacketResponse() {
        val nonce = byteArrayOf(8, 94, 113, -63, -66, 45, -21, 64)
        val packet = U2FHID.U2FHIDInitResponse(nonce, 0x12345678, 1.toByte(),
        2.toByte(), 3.toByte(), 4.toByte(), 5.toByte())

        val packetBytes = packet.serializePayload()

        assertArrayEquals(nonce, packetBytes.sliceArray(IntRange(0, 7)))
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), packetBytes.sliceArray(IntRange(8, 11)))
        assertEquals(1.toByte(), packetBytes[12])
        assertEquals(2.toByte(), packetBytes[13])
        assertEquals(3.toByte(), packetBytes[14])
        assertEquals(4.toByte(), packetBytes[15])
        assertEquals(5.toByte(), packetBytes[16])
    }
}