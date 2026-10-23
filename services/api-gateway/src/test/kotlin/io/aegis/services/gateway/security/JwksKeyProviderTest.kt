package io.aegis.services.gateway.security

import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.support.FakeJwksAsyncHttpClient
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_1
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_2
import io.aegis.services.gateway.support.JwtTestFixtures.toJwksJson
import io.aegis.services.gateway.support.MutableClock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwksKeyProviderTest {

    private val clock = MutableClock()
    private val http = FakeJwksAsyncHttpClient()
    private var provider: JwksKeyProvider? = null

    private fun createProvider(): JwksKeyProvider =
        JwksKeyProvider(GatewayAuthProperties(), http.asFactory(), clock)
            .also { provider = it }

    @AfterEach
    fun tearDown() {
        provider?.close()
    }

    @Test
    @DisplayName("이미 캐시된 kid는 auth-service를 다시 호출하지 않는다.")
    fun returnsCachedKeyWithoutRefetch() = runBlocking {
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()

        provider.getPublicKey("k1")
        provider.getPublicKey("k1")

        assertEquals(1, http.fetchCount)
    }

    @Test
    @DisplayName("JWKS에 새 kid가 추가되면 캐시 캐시 미스 시 강제 갱신으로 새 키를 찾는다")
    fun resolvesRotatedKeyByForcedRefresh() = runBlocking {
        // given: 처음에는 k1만 게시되어 있고, 캐시에 적재된 상태
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.getPublicKey("k1")

        // when: auth-service가 k2를 추가로 게시한 뒤 k2로 서명된 토큰이 들어옴
        http.willReturn(toJwksJson(KEY_1, KEY_2))
        val key = provider.getPublicKey("k2")

        // then
        assertEquals(KEY_2.toRSAPublicKey(), key)
        assertEquals(2, http.fetchCount)
    }

    @Test
    @DisplayName("모르는 kid가 cooldown 안에 반복되면 강제 갱신은 한 번만 일어난다.")
    fun forcesRefreshOnlyOncePerCooldown() = runBlocking {
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.getPublicKey("k1")

        // when & then: kid 10회 -> 모두 401
        repeat(10) {
            val ex = assertFailsWith<BusinessException> { provider.getPublicKey("forged") }
            assertEquals(GatewayErrorCode.INVALID_AUTH_TOKEN, ex.errorCode)
        }

        // then: 최초 적재 1 + 강제 갱신 1
        assertEquals(2, http.fetchCount)
    }

    @Test
    @DisplayName("cooldown이 지나면 모르는 kid에 대해 다시 강제 갱신한다.")
    fun forcesRefreshAgainAfterCooldown() = runBlocking {
        // given: 위조 kid로 강제 갱신을 한 번 소진한 상태
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.getPublicKey("k1")
        assertFailsWith<BusinessException> { provider.getPublicKey("forged") }

        // when: 29초 경과 -> 아직 cooldown 안
        clock.advance(Duration.ofSeconds(29))
        assertFailsWith<BusinessException> { provider.getPublicKey("forged") }

        // then
        assertEquals(2, http.fetchCount)

        // when: 2초 더 (총 31초) -> cooldown 지남
        clock.advance(Duration.ofSeconds(2))
        assertFailsWith<BusinessException> { provider.getPublicKey("forged") }

        assertEquals(3, http.fetchCount)
    }

    @Test
    @DisplayName("강제 갱신이 실패해도 위조 kid는 401로 거부하고 기존 키는 계속 사용한다.")
    fun keepsStaleKeysForcedRefreshFails() = runBlocking {
        // given: k1이 캐시된 뒤 auth-service 장애 발생
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.getPublicKey("k1")
        http.willThrow(BusinessException("au"))
    }
}