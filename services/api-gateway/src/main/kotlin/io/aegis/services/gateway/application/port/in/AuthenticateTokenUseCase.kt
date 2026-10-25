package io.aegis.services.gateway.application.port.`in`

import io.aegis.services.gateway.domain.VerifiedJwtClaims

interface AuthenticateTokenUseCase {
    suspend fun authenticate(token: String): VerifiedJwtClaims
}
