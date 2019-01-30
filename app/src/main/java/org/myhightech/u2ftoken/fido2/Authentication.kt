package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.fasterxml.jackson.module.kotlin.readValue

data class PublicKeyCredentialDescriptors(@JsonProperty("id") val id: ByteArray,
                                          @JsonProperty("type") val type: String) {
    fun serialize(gen: CBORGenerator) {
        gen.writeStartObject(2)

        gen.writeFieldName("id")
        gen.writeBinary(id)

        gen.writeFieldName("type")
        gen.writeString(type)

        gen.writeEndObject()
    }
}

data class AuthenticatorGetAssertionRequest(@JsonProperty("1") val rpId: String,
                                            @JsonProperty("2") val clientDataHash: ByteArray,
                                            @JsonProperty("3") val allowList: List<PublicKeyCredentialDescriptors>)
        : FIDORequest() {

    companion object {
        fun parse(parser: CBORParser): AuthenticatorGetAssertionRequest {
            return ObjectMapper().readValue(parser)
        }
    }

    override fun response(token: FIDOToken): FIDOResponse {
        val credential = allowList[0]
        return token.authenticate(credential.id, rpId,
                clientDataHash)
                ?.let {
                    val (authData, signature) = it
                    AuthenticatorGetAssertionResponse(credential, authData, signature)
                }
                ?: AuthenticatorErrorResponse.notAuthorized()
    }
}

class AuthenticatorGetAssertionResponse(val credential: PublicKeyCredentialDescriptors,
                                        val authenticatorData: AuthenticatorData,
                                        val signature: ByteArray) : FIDOResponse(0x00) {
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