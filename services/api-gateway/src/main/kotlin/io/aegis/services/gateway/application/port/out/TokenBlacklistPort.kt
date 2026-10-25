package io.aegis.services.gateway.application.port.out

interface TokenBlacklistPort {
    suspend fun isBlacklisted(jti: String): Boolean
}
