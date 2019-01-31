package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.google.common.primitives.Ints
import com.google.common.primitives.Shorts
import java.io.ByteArrayOutputStream

class AuthenticatorData(val rpIdHash: ByteArray,
                        val flags: Byte,
                        val signCount: Int,
                        val credentialData: CredentialData?) {

    fun serialize(): ByteArray {
        if (credentialData != null) {
            return byteArrayOf(*rpIdHash, flags, *Ints.toByteArray(signCount), *credentialData.serialize())
        }
        return byteArrayOf(*rpIdHash, flags, *Ints.toByteArray(signCount))
    }
}

class ECCredentialPublicKey(val x: ByteArray,
                            val y: ByteArray) {
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        val gen = CBORFactory().createGenerator(out)
        cborSerialize(gen)
        gen.close()
        return out.toByteArray()
    }

    private fun cborSerialize(gen: CBORGenerator) {
        gen.writeStartObject(5)
        gen.writeFieldId(1)
        gen.writeNumber(2)
        gen.writeFieldId(3)
        gen.writeNumber(-7)
        gen.writeFieldId(-1)
        gen.writeNumber(1)
        gen.writeFieldId(-2)
        gen.writeBinary(x)
        gen.writeFieldId(-3)
        gen.writeBinary(y)
    }
}

class CredentialData(val aaguid: ByteArray,
                     val credentialId: ByteArray,
                     val credentialPublicKey: ECCredentialPublicKey) {

    fun serialize(): ByteArray {
        return byteArrayOf(*aaguid, *Shorts.toByteArray(credentialId.size.toShort()),
                *credentialId, *credentialPublicKey.serialize())
    }
}

class PublicKeyCredentialDescriptors(@JsonProperty("id") val id: ByteArray,
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

class PublicKeyCredentialRpEntity(@JsonProperty("id") val id: String,
                                  @JsonProperty("name") val name: String,
                                  @JsonProperty("icon", required = false) val icon: String?)

class PublicKeyCredentialUserEntity(@JsonProperty("id") val id: ByteArray,
                                    @JsonProperty("name") val name: String,
                                    @JsonProperty("displayName") val displayName: String)

class PublicKeyCredentialType(@JsonProperty("alg") val algorithm: Int,
                              @JsonProperty("type") val type: String)

interface AttestationStatement {
    val attestationFormat: String
    fun generate(gen: CBORGenerator)
}

class ES256PackedAttestationStatement(val signature: ByteArray) : AttestationStatement {
    val algId = -7
    override val attestationFormat: String = "packed"

    override fun generate(gen: CBORGenerator) {
        gen.writeStartObject(2)
        gen.writeFieldName("alg")
        gen.writeNumber(algId)
        gen.writeFieldName("sig")
        gen.writeBinary(signature)
        gen.writeEndObject()
    }
}