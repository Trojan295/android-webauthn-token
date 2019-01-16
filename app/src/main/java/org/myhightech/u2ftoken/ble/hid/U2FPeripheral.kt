package org.myhightech.u2ftoken.ble.hid

import android.content.Context
import android.util.Log
import java.util.Arrays
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.arrayListOf
import kotlin.collections.copyInto
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set
import kotlin.collections.toSet

class U2FPeripheral @Throws(UnsupportedOperationException::class)
constructor(context: Context) : HidPeripheral(context, true, true, false, 20) {

    private val tag = U2FPeripheral::class.java.simpleName

    private val reportMap = byteArrayOf(
            USAGE_PAGE(2), 0xD0.toByte(), 0xF1.toByte(),
            USAGE(1), 0x01,
            COLLECTION(1), 0x01,
            USAGE(1), 0x20,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(2), 0xFF.toByte(), 0x00,
            REPORT_SIZE(1), 0x08,
            REPORT_COUNT(1), 0x40,
            INPUT(1), 0x02,
            USAGE(1), 0x21,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(2), 0xFF.toByte(), 0x00,
            REPORT_SIZE(1), 0x08,
            REPORT_COUNT(1), 0x40,
            OUTPUT(1), 0x02,
            END_COLLECTION(0))

    private val channels: HashMap<Int, MutableList<U2FHID.U2FHIDPacket>> = HashMap()
    private val U2FHID_BROADCAST_CHANNEL = 0xffffffff.toInt()

    private val token = U2FToken()

    override fun getReportMap(): ByteArray {
        return reportMap
    }

    override fun onOutputReport(outputReport: ByteArray?) {
        Log.i(tag, "onOutputReport data: " + Arrays.toString(outputReport))

        outputReport?.let {or ->
            val packet = U2FHID.deserializeU2FPacket(or)

            if (packet.channel == U2FHID_BROADCAST_CHANNEL) {
                val request = U2FHID.combinePackets(arrayListOf(packet))
                processU2FPacket(request)
            } else {
                channels[packet.channel]?.let {
                    it.add(packet)
                    try {
                        val request = U2FHID.combinePackets(it)
                        it.clear()
                        processU2FPacket(request)
                    } catch (e: IndexOutOfBoundsException) {
                        // ignore, because some packets are missing
                        // FIXME improve this one somehow
                    }

                }
            }
        }
    }

    private fun allocateChannel(): Int {
        val channel = U2FUtils.getFreeChannelId(channels.keys.toSet())
        channels[channel] = arrayListOf()
        Log.i(tag, "allocated channel: $channel")
        return channel
    }

    private fun processU2FPacket(inputPacket: U2FHID.U2FHIDRequest) {
        when {
            inputPacket.command == U2FHID.U2FHIDCommand.U2F_INIT.value -> {
                Log.v(tag, "processU2FPacket command: U2F_INIT")
                val channel = allocateChannel()
                val nonce = inputPacket.data
                val responsePacket = U2FHID.U2FHIDInitResponse(nonce, channel,
                        2.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0x01.toByte())
                sendPacket(inputPacket.channel, inputPacket.command, responsePacket)
            }

            inputPacket.command == U2FHID.U2FHIDCommand.U2F_MSG.value -> {
                Log.v(tag, "processU2FPacket command: U2F_MSG")
                val output = token.process(inputPacket.data)
                val responsePacket = U2FHID.U2FHIDMessageResponse(output)
                sendPacket(inputPacket.channel, inputPacket.command, responsePacket)
            }

            else -> {
                Log.v(tag, "processU2FPacket command: ${inputPacket.command}, UNKNOWN")
                sendPacket(inputPacket.channel, inputPacket.command, U2FHID.U2FHIDErrorResponse())
            }
        }
    }

    private fun sendPacket(channel: Int, command: Byte, packet: U2FHID.U2FHIDResponse) {
        val responsePackets = packet.splitPacket(channel, command)
        responsePackets.map { it.serialize() }
                .map { normalizeInputReport(it) }
                .forEach { addInputReport(it) }
    }

    private fun normalizeInputReport(bytes: ByteArray): ByteArray {
        if (bytes.size > 64) {
            throw ArrayIndexOutOfBoundsException("Input packet too large: ${bytes.size} bytes")
        }
        val output = ByteArray(64)
        bytes.copyInto(output)
        return output
    }
}
