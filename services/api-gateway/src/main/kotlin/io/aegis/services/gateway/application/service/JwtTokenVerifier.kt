package io.aegis.services.gateway.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.exception.GatewayErrorMessage
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class JwtTokenVerifier(
    private val jwksKeyProvider: JwksKeyProvider,
    private val clock: Clock,
) {

    suspend fun verify(token: String): VerifiedJwtClaims {
        val signedJwt = runCatching { SignedJWT.parse(token) }
            .getOrElse {
                rejectInvalidToken(GatewayErrorMessage.REASON_JWT_PARSE_FAILED)
            }

        if (signedJwt.header.algorithm != JWSAlgorithm.RS256) {
            rejectInvalidToken(GatewayErrorMessage.REASON_UNSUPPORTED_ALGORITHM)
        }

        val kid = signedJwt.header.keyID?.takeIf { it.isNotBlank() }
            ?: rejectInvalidToken(GatewayErrorMessage.REASON_MISSING_KID_HEADER)

        val publicKey = jwksKeyProvider.getPublicKey(kid)
        return verifySignatureAndExtractClaims(signedJwt, publicKey)
    }

    private fun verifySignatureAndExtractClaims(
        signedJwt: SignedJWT,
        publicKey: RSAPublicKey,
    ): VerifiedJwtClaims {
        val isSignatureValid = runCatching {
            signedJwt.verify(RSASSAVerifier(publicKey))
        }.getOrDefault(false)

        if (!isSignatureValid) {
            rejectInvalidToken(GatewayErrorMessage.REASON_INVALID_SIGNATURE)
        }

        val claims = runCatching { signedJwt.jwtClaimsSet }
            .getOrElse {
                rejectInvalidToken(GatewayErrorMessage.REASON_CLAIMS_PARSE_FAILED)
            }

        val expirationTime = claims.expirationTime
        val nowWithLeeway = Instant.now(clock).minus(CLOCK_SKEW_LEEWAY)
        if (expirationTime == null || nowWithLeeway.isAfter(expirationTime.toInstant())) {
            throw BusinessException(
                GatewayErrorCode.EXPIRED_AUTH_TOKEN,
                detail = GatewayErrorMessage.REASON_TOKEN_EXPIRED,
            )
        }

        val terminalId = claims.subject?.takeIf { it.isNotBlank() }
            ?: rejectInvalidToken(GatewayErrorMessage.REASON_MISSING_SUB_CLAIM)

        val storeId = runCatching { claims.getStringClaim(TerminalContext.CLAIM_STORE_ID) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: rejectInvalidToken(GatewayErrorMessage.REASON_MISSING_STORE_ID_CLAIM)

        val jti = claims.jwtid?.takeIf { it.isNotBlank() }
            ?: rejectInvalidToken(GatewayErrorMessage.REASON_MISSING_JTI_CLAIM)

        val scopes = extractScopes(claims)

        return VerifiedJwtClaims(
            terminalContext = TerminalContext(
                terminalId = terminalId,
                storeId = storeId,
                scopes = scopes,
            ),
            jti = jti,
        )
    }

    private fun rejectInvalidToken(reason: String): Nothing {
        throw BusinessException(GatewayErrorCode.INVALID_AUTH_TOKEN, detail = reason)
    }

    private fun extractScopes(claims: JWTClaimsSet): Set<String> {
        val listClaim: List<String>? = runCatching { claims.getStringListClaim(TerminalContext.CLAIM_SCOPES) }.getOrNull()
        if (!listClaim.isNullOrEmpty()) {
            return listClaim.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

        val stringClaim = runCatching { claims.getStringClaim(TerminalContext.CLAIM_SCOPES) }.getOrNull()
        if (!stringClaim.isNullOrBlank()) {
            return stringClaim.split(",", " ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        return emptySet()
    }

    companion object {
        private val CLOCK_SKEW_LEEWAY: Duration = Duration.ofSeconds(30)
    }
}
