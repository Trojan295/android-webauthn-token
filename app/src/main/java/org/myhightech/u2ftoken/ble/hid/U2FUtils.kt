package org.myhightech.u2ftoken.ble.hid

import kotlin.random.Random

object U2FUtils {
    private val random = Random(System.currentTimeMillis())

    fun getFreeChannelId(used: Set<Int>): Int {
        var channel = random.nextInt()
        while(used.contains(channel)) {
            channel = random.nextInt()
        }
        return channel
    }
}