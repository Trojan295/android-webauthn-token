package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import java.io.ByteArrayOutputStream

val tag = "FIDORequest"

abstract class FIDORequest {

    abstract fun response(token: FIDOToken): FIDOResponse

    companion object {
        const val MAKE_CREDENTIALS: Byte = 0x01
        const val GET_ASSERTION: Byte = 0x02
        const val GET_INFO: Byte = 0x04

        fun parse(data: ByteArray): FIDORequest {
            val parser = CBORFactory().createParser(data.sliceArray(1 until data.size))
            return when(data[0]) {
                GET_INFO -> AuthenticatorGetInfoRequest()
                MAKE_CREDENTIALS -> AuthenticatorMakeCredentialsRequest.parse(parser)
                GET_ASSERTION -> AuthenticatorGetAssertionRequest.parse(parser)
                else -> AuthenticatorUnknownRequest()
            }
        }
    }
}


class AuthenticatorUnknownRequest : FIDORequest() {
    override fun response(token: FIDOToken): FIDOResponse {
        return AuthenticatorErrorResponse.invalidCommand()
    }
}


class AuthenticatorGetInfoRequest : FIDORequest() {
    override fun response(token: FIDOToken): FIDOResponse {
        return AuthenticatorGetIntoResponse(arrayOf("FIDO_2_0"), token.aaGuid)
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
        fun notAuthorized(): AuthenticatorErrorResponse {
            return AuthenticatorErrorResponse(0x27)
        }

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
        gen.writeBinary(aaguid)
        gen.writeEndObject()
    }
}
