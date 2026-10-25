package io.aegis.services.gateway.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.aegis.platform.core.context.TerminalContext
import java.time.Duration
import java.time.Instant
import java.util.Date


object JwtTestFixtures {

    val FIXED_NOW: Instant = Instant.parse("2026-01-01T00:00:00.00Z")
    const val ISSUER = "aegis-auth-service"
    const val AUDIENCE = "aegis-api"

    val KEY_1: RSAKey by lazy { generateRsaKey("k1") }
    val KEY_2: RSAKey by lazy { generateRsaKey("k2") }

    fun generateRsaKey(kid: String = "k1"): RSAKey =
        RSAKeyGenerator(2048).keyID(kid).generate()

    fun toJwksJson(vararg rsaKeys: RSAKey): String =
        JWKSet(rsaKeys.map { it.toPublicJWK() }).toString()

    fun createRs256Token(
        signingKey: RSAKey,
        kid: String? = signingKey.keyID,
        subject: String? = "TERM-001",
        storeId: String? = "STORE-001",
        jti: String? = "jti-001",
        expirationTime: Instant? = FIXED_NOW.plus(Duration.ofMinutes(10)),
        scopes: Any? = listOf("payment:write", "payment:read"),
        issuer: String? = ISSUER,
        audience: String? = AUDIENCE,
    ): String {
        val headerBuilder = JWSHeader.Builder(JWSAlgorithm.RS256)
        if (kid != null) headerBuilder.keyID(kid)

        val claims = buildClaims(subject, storeId, jti, expirationTime, scopes, issuer, audience)
        return SignedJWT(headerBuilder.build(), claims).apply {
            sign(RSASSASigner(signingKey))
        }.serialize()
    }

    fun createHs256Token(
        secret: ByteArray = "0123456789abcdef0123456789abcdef".toByteArray(),
        kid: String = "k1",
        expirationTime: Instant = FIXED_NOW.plus(Duration.ofMinutes(10)),
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kid).build()
        val claims = buildClaims("TERM-001", "STORE-001", "jti-001", expirationTime, null, ISSUER, AUDIENCE)
        return SignedJWT(header, claims).apply { sign(MACSigner(secret)) }.serialize()
    }

    private fun buildClaims(
        subject: String?,
        storeId: String?,
        jti: String?,
        expirationTime: Instant?,
        scopes: Any?,
        issuer: String?,
        audience: String?,
    ): JWTClaimsSet {
        val builder = JWTClaimsSet.Builder()
        if (issuer != null) builder.issuer(issuer)
        if (audience != null) builder.audience(audience)
        if (subject != null) builder.subject(subject)
        if (storeId != null) builder.claim(TerminalContext.CLAIM_STORE_ID, storeId)
        if (jti != null) builder.jwtID(jti)
        if (expirationTime != null) builder.expirationTime(Date.from(expirationTime))
        if (scopes != null) builder.claim(TerminalContext.CLAIM_SCOPES, scopes)
        return builder.build()
    }
}