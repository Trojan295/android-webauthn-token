package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import java.io.ByteArrayOutputStream

abstract class FIDO2Response(val status: Byte) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        val gen = CBORFactory().createGenerator(out)
        cborSerialize(gen)
        gen.close()
        val objBytes = out.toByteArray()
        return byteArrayOf(status, *objBytes)
    }

    abstract fun cborSerialize(gen: CBORGenerator)
}
