package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.fasterxml.jackson.module.kotlin.readValue

object AuthenticatorMakeCredentials {

    class Request(@JsonProperty("1") val clientDataHash: ByteArray,
                  @JsonProperty("2") val rp: PublicKeyCredentialRpEntity,
                  @JsonProperty("3") val user: PublicKeyCredentialUserEntity,
                  @JsonProperty("4") val pubKeyCredParams: Array<PublicKeyCredentialType>,
                  @JsonProperty("5", defaultValue="[]") val excludeList: Array<String>) {

        companion object {
            fun parse(parser: CBORParser): Request {
                return ObjectMapper().readValue(parser)
            }
        }
    }

    class Response(val authData: AuthenticatorData,
                   val attestationStatement: AttestationStatement) : FIDO2Response(0x00) {
        override fun cborSerialize(gen: CBORGenerator) {
            gen.writeStartObject(3)

            gen.writeFieldId(1)
            gen.writeString(attestationStatement.attestationFormat)

            gen.writeFieldId(2)
            gen.writeBinary(authData.serialize())

            gen.writeFieldId(3)
            attestationStatement.generate(gen)

            gen.writeEndObject()
        }
    }

}

