package io.aegis.services.gateway

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.httpclient.exception.HttpClientErrorCode
import io.aegis.services.gateway.support.FakeJwksAsyncHttpClient
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_1
import io.aegis.services.gateway.support.JwtTestFixtures.toJwksJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.time.Instant

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "aegis.gateway.auth.jwks-warm-up-initial-backoff=20ms",
        "aegis.gateway.auth.jwks-warm-up-max-backoff=50ms",
    ],
)
class GatewayReadinessTest {

    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(5))
            .build()
    }

    @Test
    @DisplayName("JWKS를 적재하기 전에는 readiness가 DOWN(503)이고 liveness는 UP이며, auth-service가 복구되면 warm-up이 키를 적재해 readiness가 UP이 된다.")
    fun readinessFollowsJwksWarmUp() {
        client.get().uri("/actuator/health/readiness").exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody().jsonPath("$.status").isEqualTo("DOWN")
        client.get().uri("/actuator/health/liveness").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("UP")

        authService.willReturn(toJwksJson(KEY_1))

        val deadline = Instant.now().plusSeconds(5)
        var status = HttpStatus.SERVICE_UNAVAILABLE.value()
        while (status != HttpStatus.OK.value() && Instant.now().isBefore(deadline)) {
            Thread.sleep(20)
            status = client.get().uri("/actuator/health/readiness").exchange()
                .returnResult(String::class.java).status.value()
        }
        client.get().uri("/actuator/health/readiness").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("UP")
    }

    @TestConfiguration
    class DownAuthServiceConfig {

        @Bean
        fun downJwksAsyncHttpClientFactory(): AsyncHttpClientFactory = authService.asFactory()
    }

    companion object {
        private val authService = FakeJwksAsyncHttpClient().apply {
            willThrow(BusinessException(HttpClientErrorCode.EXTERNAL_API_CIRCUIT_OPEN, "auth-service"))
        }
    }
}
