package io.aegis.services.auth.adapter.out.redis

import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.auth.application.exception.AuthErrorCode
import io.aegis.services.auth.support.ValkeyContainer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedisChallengeStoreTest {

    private val redisTemplate = ValkeyContainer.redisTemplate()
    private val store = RedisChallengeStore(redisTemplate)

    @Test
    @DisplayName("저장한 nonce는 한 번만 꺼낼 수 있다.")
    fun consumesOnlyOnce() {
        val nonce = UUID.randomUUID().toString()
        store.save(nonce, "TERM-001", Duration.ofSeconds(60))

        assertEquals("TERM-001", store.consume(nonce))
        assertNull(store.consume(nonce))
    }

    @Test
    @DisplayName("nonce에는 TTL이 걸려 있다.")
    fun setsTtl() {
        val nonce = UUID.randomUUID().toString()
        store.save(nonce, "TERM-001", Duration.ofSeconds(60))

        val ttlSeconds = redisTemplate.getExpire("${RedisChallengeStore.KEY_PREFIX}$nonce")
        assertTrue(ttlSeconds in 1..60, "ttl=$ttlSeconds")
    }

    @Test
    @DisplayName("같은 nonce를 동시에 20번 꺼내도 성공은 정확히 1번이다.")
    fun concurrentConsumeSucceedsOnce() {
        val nonce = UUID.randomUUID().toString()
        store.save(nonce, "TERM-001", Duration.ofSeconds(60))
        val executor = Executors.newFixedThreadPool(20)

        val results = try {
            executor.invokeAll(List(20) { Callable { store.consume(nonce) } }).map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, results.count { it == "TERM-001" })
    }

    @Test
    @DisplayName("Redis에 연결할 수 없으면 503 CHALLENGE_STORE_UNAVAILABLE이다.")
    fun failsClosedWhenRedisIsUnavailable() {
        val unreachable = LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", 1))
            .apply { afterPropertiesSet() }
        val brokenStore = RedisChallengeStore(StringRedisTemplate(unreachable).apply { afterPropertiesSet() })

        val ex = assertFailsWith<BusinessException> { brokenStore.consume("nonce") }

        assertEquals(AuthErrorCode.CHALLENGE_STORE_UNAVAILABLE, ex.errorCode)
        unreachable.destroy()
    }
}
