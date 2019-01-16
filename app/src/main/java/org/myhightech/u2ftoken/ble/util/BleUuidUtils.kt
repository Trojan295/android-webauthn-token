package org.myhightech.u2ftoken.ble.util

import android.os.ParcelUuid

import java.util.UUID

object BleUuidUtils {

    private val UUID_LONG_STYLE_PREFIX = "0000"
    private val UUID_LONG_STYLE_POSTFIX = "-0000-1000-8000-00805F9B34FB"

    fun fromString(uuidString: String): UUID {
        try {
            return UUID.fromString(uuidString)
        } catch (e: IllegalArgumentException) {
            // may be a short style
            return UUID.fromString(UUID_LONG_STYLE_PREFIX + uuidString + UUID_LONG_STYLE_POSTFIX)
        }

    }

    fun fromShortValue(uuidShortValue: Int): UUID {
        return UUID.fromString(UUID_LONG_STYLE_PREFIX + String.format("%04X", uuidShortValue and 0xffff) + UUID_LONG_STYLE_POSTFIX)
    }

    fun parcelFromShortValue(uuidShortValue: Int): ParcelUuid {
        return ParcelUuid.fromString(UUID_LONG_STYLE_PREFIX + String.format("%04X", uuidShortValue and 0xffff) + UUID_LONG_STYLE_POSTFIX)
    }

    fun toShortValue(uuid: UUID): Int {
        return (uuid.mostSignificantBits shr 32 and 0xffff).toInt()
    }

    fun matches(src: UUID, dst: UUID): Boolean {
        if (isShortUuid(src) || isShortUuid(dst)) {
            // at least one instance is short style: check only 16bits
            val srcShortUUID = src.mostSignificantBits and 0x0000ffff00000000L
            val dstShortUUID = dst.mostSignificantBits and 0x0000ffff00000000L

            return srcShortUUID == dstShortUUID
        } else {
            return src == dst
        }
    }

    private fun isShortUuid(src: UUID): Boolean {
        return src.mostSignificantBits and -0xffff00000001L == 0L && src.leastSignificantBits == 0L
    }
}
