package io.aegis.services.gateway.adapter.out.redis

import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.application.exception.GatewayErrorCode
import io.aegis.services.gateway.application.port.out.TokenBlacklistPort
import io.aegis.services.gateway.config.GatewayAuthProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisTokenBlacklistAdapter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val properties: GatewayAuthProperties,
) : TokenBlacklistPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun isBlacklisted(jti: String): Boolean {
        val blacklistKey = "$BLACKLIST_KEY_PREFIX$jti"
        return runCatching {
            withTimeout(properties.blacklistTimeout.toMillis()) {
                redisTemplate.hasKey(blacklistKey).awaitSingle()
            }
        }.getOrElse { ex ->
            if (ex is CancellationException && ex !is TimeoutCancellationException) {
                throw ex
            }
            log.error("Failed to check token blacklist in Redis: jti={}", jti, ex)
            throw BusinessException(GatewayErrorCode.TOKEN_STORE_UNAVAILABLE)
        }
    }

    companion object {
        const val BLACKLIST_KEY_PREFIX = "blacklist:jti:"
    }
}
