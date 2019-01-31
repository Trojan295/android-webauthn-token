package org.myhightech.u2ftoken.fido2

import com.fasterxml.jackson.dataformat.cbor.CBORGenerator

class AuthenticatorErrorResponse(status: Byte) : FIDO2Response(status) {
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