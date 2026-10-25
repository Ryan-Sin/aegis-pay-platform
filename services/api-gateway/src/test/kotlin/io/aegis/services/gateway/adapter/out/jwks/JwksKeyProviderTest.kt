package io.aegis.services.gateway.adapter.out.jwks

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.httpclient.exception.HttpClientErrorCode
import io.aegis.services.gateway.application.exception.GatewayErrorCode
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.aegis.services.gateway.support.FakeJwksAsyncHttpClient
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_1
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_2
import io.aegis.services.gateway.support.JwtTestFixtures.toJwksJson
import io.aegis.services.gateway.support.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwksKeyProviderTest {

    private val clock = MutableClock()
    private val http = FakeJwksAsyncHttpClient()
    private var provider: JwksKeyProvider? = null

    private fun createProvider(
        properties: GatewayAuthProperties = GatewayAuthProperties(),
    ): JwksKeyProvider =
        JwksKeyProvider(properties, http.asFactory(), clock)
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
    @DisplayName("JWKS에 새 kid가 추가되면 캐시 미스 시 강제 갱신으로 새 키를 찾는다")
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
    fun keepsStaleKeysWhenForcedRefreshFails() = runBlocking {
        // given: k1이 캐시된 뒤 auth-service 장애 발생
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.getPublicKey("k1")
        http.willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_TIMEOUT, "auth-service", JwksKeyProvider.JWKS_URI))

        // when: 모르는 kid로 강제 갱신이 일어나지만 실패
        val ex = assertFailsWith<BusinessException> { provider.getPublicKey("forged") }

        // then: 503(JWKS_FETCH_FAILED)이 아니라 401로 분류하고, 기존 키는 그대로 쓴다
        assertEquals(GatewayErrorCode.INVALID_AUTH_TOKEN, ex.errorCode)
        assertEquals(KEY_1.toRSAPublicKey(), provider.getPublicKey("k1"))
        assertEquals(2, http.fetchCount)
    }

    @ParameterizedTest
    @MethodSource("initialLoadFailures")
    @DisplayName("최초 적재가 실패하면 원인과 상관없이 JWKS_FETCH_FAILED(503)로 변환한다.")
    fun translatesInitialLoadFailure(arrange: (FakeJwksAsyncHttpClient) -> Unit) = runBlocking {
        arrange(http)
        val provider = createProvider()

        val ex = assertFailsWith<BusinessException> { provider.getPublicKey("k1") }

        assertEquals(GatewayErrorCode.JWKS_FETCH_FAILED, ex.errorCode)
    }

    @Test
    @DisplayName("최초 적재 실패는 캐시되지 않고 다음 요청에서 다시 조회한다.")
    fun doesNotCacheInitialLoadFailure() = runBlocking {
        http.willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_CIRCUIT_OPEN, "auth-service"))
        val provider = createProvider()
        assertFailsWith<BusinessException> { provider.getPublicKey("k1") }

        // when: auth-service 복구
        http.willReturn(toJwksJson(KEY_1))

        // then: cooldown과 무관하게 다시 적재된다.
        // Caffeine은 실패한 future를 whenComplete 콜백에서 비동기로 제거하므로 제거 직전의 짧은 구간은 기다린다.
        val key = withTimeout(1_000) {
            var result = runCatching { provider.getPublicKey("k1") }
            while (result.isFailure) {
                delay(10)
                result = runCatching { provider.getPublicKey("k1") }
            }
            result.getOrThrow()
        }
        assertEquals(KEY_1.toRSAPublicKey(), key)
        assertEquals(2, http.fetchCount)
    }

    @Test
    @DisplayName("위조 kid 요청이 동시에 몰려도 강제 갱신은 한 번만 일어난다.")
    fun forcesRefreshOnlyOnceUnderConcurrentUnknownKids() = runBlocking {
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.getPublicKey("k1")

        val results = withContext(Dispatchers.Default) {
            (1..100).map {
                async { runCatching { provider.getPublicKey("forged-$it") } }
            }.awaitAll()
        }

        assertTrue(results.all { (it.exceptionOrNull() as? BusinessException)?.errorCode == GatewayErrorCode.INVALID_AUTH_TOKEN })
        assertEquals(2, http.fetchCount)
    }

    @Test
    @DisplayName("warm-up이 끝나면 키가 적재되어 첫 요청도 auth-service를 호출하지 않는다.")
    fun warmUpLoadsKeysBeforeFirstRequest() = runBlocking {
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        assertFalse(provider.isKeysLoaded)

        provider.warmUp()

        assertTrue(provider.isKeysLoaded)
        assertEquals(KEY_1.toRSAPublicKey(), provider.getPublicKey("k1"))
        assertEquals(1, http.fetchCount)
    }

    @Test
    @DisplayName("warm-up은 auth-service가 복구될 때까지 재시도하고, 그 전까지는 적재 전 상태다.")
    fun warmUpRetriesUntilAuthServiceRecovers() = runBlocking {
        http.willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_CIRCUIT_OPEN, "auth-service"))
        val provider = createProvider(
            GatewayAuthProperties(
                jwksWarmUpInitialBackoff = Duration.ofMillis(10),
                jwksWarmUpMaxBackoff = Duration.ofMillis(20),
            ),
        )

        val job = provider.startWarmUp()
        withTimeout(2_000) { while (http.fetchCount < 3) delay(5) }
        assertFalse(provider.isKeysLoaded)

        http.willReturn(toJwksJson(KEY_1))
        withTimeout(2_000) { job.join() }

        assertTrue(provider.isKeysLoaded)
        assertEquals(KEY_1.toRSAPublicKey(), provider.getPublicKey("k1"))
    }

    @Test
    @DisplayName("한 번 적재된 뒤에는 갱신이 실패해도 적재 상태를 유지한다(readiness가 auth-service 장애를 따라 흔들리지 않게).")
    fun staysLoadedWhenLaterRefreshFails() = runBlocking {
        http.willReturn(toJwksJson(KEY_1))
        val provider = createProvider()
        provider.warmUp()

        http.willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_TIMEOUT, "auth-service", JwksKeyProvider.JWKS_URI))
        assertFailsWith<BusinessException> { provider.getPublicKey("forged") }

        assertTrue(provider.isKeysLoaded)
        assertEquals(2, http.fetchCount)
    }

    companion object {
        @JvmStatic
        fun initialLoadFailures(): List<Named<(FakeJwksAsyncHttpClient) -> Unit>> = listOf(
            Named.of("timeout") { it.willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_TIMEOUT, "auth-service", JwksKeyProvider.JWKS_URI)) },
            Named.of("circuit open") { it.willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_CIRCUIT_OPEN, "auth-service")) },
            Named.of("HTML 응답") { it.willReturn("<html>502 Bad Gateway</html>") },
            Named.of("빈 응답") { it.willReturn("") },
            Named.of("키 없는 JWKS") { it.willReturn("""{"keys":[]}""") },
        )
    }
}
