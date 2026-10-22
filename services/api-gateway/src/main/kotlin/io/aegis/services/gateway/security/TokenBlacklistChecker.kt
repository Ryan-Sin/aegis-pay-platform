package io.aegis.services.gateway.security

import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.exception.GatewayErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component

@Component
class TokenBlacklistChecker(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val properties: GatewayAuthProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun verifyNotBlacklisted(claims: VerifiedJwtClaims) {
        val blacklistKey = "$BLACKLIST_KEY_PREFIX${claims.jti}"
        val isBlacklisted = runCatching {
            withTimeout(properties.blacklistTimeout.toMillis()) {
                redisTemplate.hasKey(blacklistKey).awaitSingle()
            }
        }.getOrElse { ex ->
            if (ex is CancellationException && ex !is TimeoutCancellationException) {
                throw ex
            }
            log.error("Failed to check token blacklist in Redis: jti={}", claims.jti, ex)
            throw BusinessException(GatewayErrorCode.TOKEN_STORE_UNAVAILABLE)
        }

        if (isBlacklisted) {
            throw BusinessException(
                GatewayErrorCode.REVOKED_AUTH_TOKEN,
                detail = "${GatewayErrorMessage.REASON_TOKEN_REVOKED} (jti=${claims.jti}, terminalId=${claims.terminalContext.terminalId})",
            )
        }
    }

    companion object {
        const val BLACKLIST_KEY_PREFIX = "blacklist:jti:"
    }
}
