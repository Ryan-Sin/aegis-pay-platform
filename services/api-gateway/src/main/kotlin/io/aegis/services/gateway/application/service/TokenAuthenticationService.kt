package io.aegis.services.gateway.application.service

import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.application.exception.GatewayErrorCode
import io.aegis.services.gateway.application.exception.GatewayErrorMessage
import io.aegis.services.gateway.application.port.`in`.AuthenticateTokenUseCase
import io.aegis.services.gateway.application.port.out.TokenBlacklistPort
import io.aegis.services.gateway.domain.VerifiedJwtClaims
import org.springframework.stereotype.Service

@Service
class TokenAuthenticationService(
    private val jwtTokenVerifier: JwtTokenVerifier,
    private val tokenBlacklistPort: TokenBlacklistPort,
) : AuthenticateTokenUseCase {

    override suspend fun authenticate(token: String): VerifiedJwtClaims {
        val claims = jwtTokenVerifier.verify(token)
        if (tokenBlacklistPort.isBlacklisted(claims.jti)) {
            throw BusinessException(
                GatewayErrorCode.REVOKED_AUTH_TOKEN,
                detail = "${GatewayErrorMessage.REASON_TOKEN_REVOKED} (jti=${claims.jti}, terminalId=${claims.terminalContext.terminalId})",
            )
        }
        return claims
    }
}
