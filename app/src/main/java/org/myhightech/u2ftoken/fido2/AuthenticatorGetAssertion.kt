package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.fasterxml.jackson.module.kotlin.readValue

object AuthenticatorGetAssertion {

    class Request(@JsonProperty("1") val rpId: String,
                  @JsonProperty("2") val clientDataHash: ByteArray,
                  @JsonProperty("3") val allowList: List<PublicKeyCredentialDescriptors>) {
        companion object {
            fun parse(parser: CBORParser): Request {
                return ObjectMapper().readValue(parser)
            }
        }
    }

    class Response(val credential: PublicKeyCredentialDescriptors,
                   val authenticatorData: AuthenticatorData,
                   val signature: ByteArray) : FIDO2Response(0x00) {
        override fun cborSerialize(gen: CBORGenerator) {
            gen.writeStartObject(3)

            gen.writeFieldId(1)
            credential.serialize(gen)

            gen.writeFieldId(2)
            gen.writeBinary(authenticatorData.serialize())

            gen.writeFieldId(3)
            gen.writeBinary(signature)

            gen.writeEndObject()
        }
    }

}

