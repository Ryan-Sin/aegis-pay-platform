package io.aegis.services.gateway.application.port.out

import java.security.interfaces.RSAPublicKey

interface LoadJwtPublicKeyPort {
    suspend fun getPublicKey(kid: String): RSAPublicKey
}
