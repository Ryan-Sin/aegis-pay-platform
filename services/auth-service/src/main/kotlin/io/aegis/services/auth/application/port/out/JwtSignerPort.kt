package io.aegis.services.auth.application.port.out

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

interface JwtSignerPort {
    fun sign(claims: JWTClaimsSet): SignedJWT

    fun publicJwkSet(): JWKSet
}
