package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.io.ByteArrayOutputStream

interface FIDOResponse {
    fun toCbor(): ByteArray
}


class GetIntoResponse(val versions: Array<String>,
                      val aaguid: ByteArray) : FIDOResponse {
    override fun toCbor(): ByteArray {
        val out = ByteArrayOutputStream()
        val gen = CBORFactory().createGenerator(out)
        gen.writeStartObject(2)

        gen.writeFieldId(0x01)
        gen.writeStartArray(versions.size)
        for (version in versions) {
            gen.writeString(version)
        }
        gen.writeEndArray()

        gen.writeFieldId(0x03)
        gen.writeBinary(aaguid)

        gen.writeEndObject()
        gen.close()
        return out.toByteArray()
    }
}