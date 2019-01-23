package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.primitives.Ints
import com.google.common.primitives.Shorts
import java.io.ByteArrayOutputStream


val AAGUID = byteArrayOf(173.toByte(), 51, 225.toByte(), 30, 242.toByte(), 194.toByte(),
        52, 188.toByte(), 136.toByte(), 149.toByte(), 129.toByte(), 254.toByte(), 124.toByte(),
        76, 160.toByte(), 205.toByte())

abstract class FIDORequest {

    abstract fun response(): FIDOResponse

    companion object {
        const val MAKE_CREDENTIALS: Byte = 0x01
        const val GET_INFO: Byte = 0x04

        fun parse(data: ByteArray): FIDORequest {

            val parser = CBORFactory().createParser(data.sliceArray(1 until data.size))

            return when(data[0]) {
                GET_INFO -> AuthenticatorGetInfoRequest()
                MAKE_CREDENTIALS -> AuthenticatorMakeCredentialsRequest.parse(parser)
                else -> AuthenticatorUnknownRequest()
            }
        }
    }
}


class AuthenticatorUnknownRequest : FIDORequest() {
    override fun response(): FIDOResponse {
        return AuthenticatorErrorResponse.invalidCommand()
    }
}


class AuthenticatorGetInfoRequest : FIDORequest() {

    override fun response(): FIDOResponse {
        return AuthenticatorGetIntoResponse(arrayOf("FIDO_2_0"), AAGUID)
    }
}

data class PublicKeyCredentialRpEntity(@JsonProperty("id") val id: String,
                                       @JsonProperty("name") val name: String)

data class PublicKeyCredentialUserEntity(@JsonProperty("id") val id: ByteArray,
                                         @JsonProperty("name") val name: String,
                                         @JsonProperty("displayName") val displayName: String)

data class PublicKeyCredentialType(@JsonProperty("alg") val algorithm: Int,
                                   @JsonProperty("type") val type: String)

data class AuthenticatorMakeCredentialsRequest(@JsonProperty("1") val clientDataHash: ByteArray,
                                               @JsonProperty("2") val rp: PublicKeyCredentialRpEntity,
                                               @JsonProperty("3") val user: PublicKeyCredentialUserEntity,
                                               @JsonProperty("4") val pubKeyCredParams: Array<PublicKeyCredentialType>,
                                               @JsonProperty("5", defaultValue="[]") val excludeList: Array<String>) : FIDORequest() {

    companion object {
        fun parse(parser: CBORParser): AuthenticatorMakeCredentialsRequest {
            return ObjectMapper().readValue(parser)
        }
    }

    override fun response(): FIDOResponse {
        return AuthenticatorMakeCredentialsResponse("none",
                AuthenticatorData(byteArrayOf(1,2,3), 0b01000101, 1,
                        CredentialData(AAGUID, byteArrayOf(1), byteArrayOf(1,2,3))))
    }

}


abstract class FIDOResponse(val status: Byte) {
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

class AuthenticatorErrorResponse(status: Byte) : FIDOResponse(status) {
    override fun cborSerialize(gen: CBORGenerator) {
    }

    companion object {

        fun invalidCommand(): AuthenticatorErrorResponse {
            return AuthenticatorErrorResponse(0x01)
        }
    }
}


class AuthenticatorGetIntoResponse(val versions: Array<String>,
                                   val aaguid: ByteArray) : FIDOResponse(0x00) {
    override fun cborSerialize(gen: CBORGenerator) {
        gen.writeStartObject(2)

        gen.writeFieldId(1)
        gen.writeStartArray(versions.size)
        for (v in versions) {
            gen.writeString(v)
        }
        gen.writeEndArray()

        gen.writeFieldId(3)
        gen.writeBinary(AAGUID)
        gen.writeEndObject()
    }

    fun toHashMap(): Map<Int, Any> {
        return hashMapOf(
                1 to versions,
                3 to aaguid
        )
    }

}

class CredentialData(val aaguid: ByteArray,
                     val credentialId: ByteArray,
                     val credentialPublicKey: ByteArray) {

    fun serialize(): ByteArray {
        return byteArrayOf(*aaguid, *Shorts.toByteArray(credentialId.size.toShort()),
                *credentialId, *credentialPublicKey)
    }
}

class AuthenticatorData(val rpIdHash: ByteArray,
                        val flags: Byte,
                        val signCount: Int,
                        val credentialData: CredentialData) {

    fun serialize(): ByteArray {
        return byteArrayOf(*rpIdHash, flags, *Ints.toByteArray(signCount), *credentialData.serialize())
    }
}

class AuthenticatorMakeCredentialsResponse(val fmt: String,
                                           val authData: AuthenticatorData) : FIDOResponse(0x00) {
    override fun cborSerialize(gen: CBORGenerator) {
        gen.writeStartObject(3)

        gen.writeFieldId(1)
        gen.writeString(fmt)

        gen.writeFieldId(2)
        gen.writeBinary(authData.serialize())

        gen.writeFieldId(3)
        gen.writeStartObject(0)
        gen.writeEndObject()

        gen.writeEndObject()
    }
}