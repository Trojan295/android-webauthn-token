package org.myhightech.u2ftoken.ble.hid

import com.google.common.primitives.Ints
import com.google.common.primitives.Shorts
import kotlin.experimental.and

object U2FHID {

    enum class U2FHIDCommand (val value: Byte) {
        U2F_INIT(0x86.toByte()),
        U2F_MSG(0x83.toByte()),
    }

    abstract class U2FHIDPacket(
            val channel: Int,
            val data: ByteArray) {

        fun dataSize(): Short {
            return data.size.toShort()
        }

        abstract fun serialize(): ByteArray
    }

    class U2FHIDInitPacket(channel: Int,
                           val command: Byte,
                           val length: Short,
                           data: ByteArray) : U2FHIDPacket(channel, data) {
        override fun serialize(): ByteArray {
            val bytes = ByteArray(7+dataSize())
            Ints.toByteArray(channel).copyInto(bytes)
            bytes[4] = command
            Shorts.toByteArray(length).copyInto(bytes, 5)
            data.copyInto(bytes, 7)
            return bytes
        }
    }

    class U2FHIDContPacket(channel: Int,
                           val sequence: Byte,
                           data: ByteArray) : U2FHIDPacket(channel, data) {
        override fun serialize(): ByteArray {
            val bytes = ByteArray(5+dataSize())
            Ints.toByteArray(channel).copyInto(bytes)
            bytes[4] = sequence
            data.copyInto(bytes, 5)
            return bytes
        }
    }

    class U2FHIDRequest(val channel: Int,
                        val command: Byte,
                        val data: ByteArray)

    abstract class U2FHIDResponse {
        fun splitPacket(channel: Int, command: Byte): List<U2FHIDPacket> {
            val packets = ArrayList<U2FHIDPacket>()

            val payload = serializePayload()

            if (payload.size > 57) {
                var offset = 57
                var seq = 0
                val init = U2FHIDInitPacket(channel, command, payload.size.toShort(),
                        payload.sliceArray(0..56))
                packets.add(init)

                while (offset < payload.size) {
                    var payloadSize = payload.size - offset
                    if (payloadSize > 59) {
                        payloadSize = 59
                    }

                    val cont = U2FHIDContPacket(channel, seq.toByte(),
                            payload.sliceArray(offset until offset + payloadSize))
                    packets.add(cont)
                    seq += 1
                    offset += payloadSize
                }
            } else {
                val init = U2FHIDInitPacket(channel, command, payload.size.toShort(),
                        payload)
                packets.add(init)
            }

            return packets
        }

        abstract fun serializePayload(): ByteArray
    }

    class U2FHIDErrorResponse() : U2FHIDResponse() {
        override fun serializePayload(): ByteArray {
            return byteArrayOf(0x01) // always return COMMAND_UNKNOWN error
        }
    }

    class U2FHIDInitResponse(private val nonce: ByteArray,
                             private val cid: Int,
                             private val protocolVersion: Byte,
                             private val majorVersion: Byte,
                             private val minorVersion: Byte,
                             private val deviceVersion: Byte,
                             private val flags: Byte) : U2FHIDResponse() {

        override fun serializePayload(): ByteArray {
            val bytes = ByteArray(17)
            nonce.copyInto(bytes,0)
            Ints.toByteArray(cid).copyInto(bytes, 8)
            bytes[12] = protocolVersion
            bytes[13] = majorVersion
            bytes[14] = minorVersion
            bytes[15] = deviceVersion
            bytes[16] = flags
            return bytes
        }
    }

    class U2FHIDMessageResponse(
            private val data: ByteArray): U2FHIDResponse() {
        override fun serializePayload(): ByteArray {
            return data
        }
    }

    fun deserializeU2FPacket(packetBytes: ByteArray): U2FHIDPacket {
        val cid = Ints.fromByteArray(packetBytes.sliceArray(IntRange(1,4)))
        val cmd = packetBytes[5]

        return if (cmd and 0x80.toByte() == 0x80.toByte()) {
            val length = Shorts.fromByteArray(packetBytes.sliceArray(IntRange(6,7)))
            val data = packetBytes.sliceArray(IntRange(8, packetBytes.size-1))
            U2FHIDInitPacket(cid, cmd, length, data)
        } else {
            val sequence = cmd and 0x7F
            val data = packetBytes.sliceArray(IntRange(6, packetBytes.size-1))
            U2FHIDContPacket(cid, sequence, data)
        }
    }

    fun combinePackets(packets: MutableList<U2FHIDPacket>): U2FHIDRequest {
        val init = packets.first() as U2FHIDInitPacket

        val packetSize = init.length.toInt()
        val maxSize = packets.map { x -> x.dataSize().toInt() }.reduce{x,y -> x.plus(y)}
        val data = ByteArray(maxSize)

        var offset = 0
        for (i in 0 until packets.size) {
            val packet = packets[i]
            packets[i].data.copyInto(data,offset)
            offset += packet.dataSize()
        }
        return U2FHIDRequest(init.channel, init.command, data.sliceArray(IntRange(0, packetSize - 1)))
    }
}
