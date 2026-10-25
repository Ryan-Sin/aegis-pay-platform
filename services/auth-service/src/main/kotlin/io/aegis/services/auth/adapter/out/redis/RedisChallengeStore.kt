package io.aegis.services.auth.adapter.out.redis

import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.application.exception.AuthErrorMessage
import io.aegis.services.auth.application.port.out.ChallengeStorePort
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisChallengeStore(
    private val redisTemplate: StringRedisTemplate,
) : ChallengeStorePort {

    override fun save(nonce: String, terminalId: String, ttl: Duration) {
        execute { redisTemplate.opsForValue().set(key(nonce), terminalId, ttl) }
    }

    override fun consume(nonce: String): String? =
        execute { redisTemplate.opsForValue().getAndDelete(key(nonce)) }

    private fun <T> execute(action: () -> T): T =
        try {
            action()
        } catch (ex: DataAccessException) {
            throw BusinessException(
                AuthErrorCode.CHALLENGE_STORE_UNAVAILABLE,
                detail = AuthErrorMessage.REASON_CHALLENGE_STORE_FAILED,
                cause = ex,
            )
        }

    private fun key(nonce: String): String = "$KEY_PREFIX$nonce"

    companion object {
        const val KEY_PREFIX = "auth:challenge:"
    }
}
