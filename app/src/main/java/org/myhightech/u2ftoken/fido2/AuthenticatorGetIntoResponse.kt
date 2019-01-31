package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.dataformat.cbor.CBORGenerator

class AuthenticatorGetIntoResponse(val versions: Array<String>,
                                   val aaguid: ByteArray) : FIDO2Response(0x00) {
    override fun cborSerialize(gen: CBORGenerator) {
        gen.writeStartObject(2)

        gen.writeFieldId(1)
        gen.writeStartArray(versions.size)
        for (v in versions) {
            gen.writeString(v)
        }
        gen.writeEndArray()

        gen.writeFieldId(3)
        gen.writeBinary(aaguid)
        gen.writeEndObject()
    }
}