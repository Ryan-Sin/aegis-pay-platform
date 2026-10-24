package io.aegis.services.gateway

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.observability.TraceContextConstants
import io.aegis.services.gateway.exception.GatewayErrorCode
import io.aegis.services.gateway.exception.GatewayErrorMessage
import io.aegis.services.gateway.support.FakeJwksAsyncHttpClient
import io.aegis.services.gateway.support.JwtTestFixtures.KEY_1
import io.aegis.services.gateway.support.JwtTestFixtures.createHs256Token
import io.aegis.services.gateway.support.JwtTestFixtures.createRs256Token
import io.aegis.services.gateway.support.JwtTestFixtures.toJwksJson
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Autowired
    private lateinit var tracer: Tracer

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        downstreamRequestHeaders.set(null)
        `when`(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false))
        client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(5))
            .build()
    }

    @Test
    @DisplayName("외부에서 위조한 X-Terminal-*, X-Store-*, baggage 헤더는 제거되고 JWT 클레임 값으로 교체된다.")
    fun replacesSpoofedTrustHeadersWithJwtClaims() {
        client.get().uri("/api/v1/payments/123")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken()}")
            .header(TerminalContext.HEADER_TERMINAL_ID, "SPOOFED-TERM")
            .header(TerminalContext.HEADER_STORE_ID, "SPOOFED-STORE")
            .header(TerminalContext.HEADER_TERMINAL_SCOPES, "admin")
            .header("x-store-role", "owner")
            .header("baggage", "terminalId=SPOOFED-TERM,storeId=SPOOFED-STORE")
            .exchange()
            .expectStatus().isOk

        val received = assertNotNull(downstreamRequestHeaders.get(), "하위 서비스로 요청이 전달되어야 합니다")
        assertEquals(listOf("TERM-001"), received[TerminalContext.HEADER_TERMINAL_ID])
        assertEquals(listOf("STORE-001"), received[TerminalContext.HEADER_STORE_ID])
        assertEquals(listOf("payment:write,payment:read"), received[TerminalContext.HEADER_TERMINAL_SCOPES])
        assertNull(received["X-Store-Role"])
        assertNull(received["Baggage"])
    }

    @Test
    @DisplayName("permit-all 경로는 토큰 없이 통과하지만 위조 헤더는 여전히 제거된다.")
    fun stripsSpoofedHeadersOnPermitAllPath() {
        client.post().uri("/api/v1/auth/token")
            .header(TerminalContext.HEADER_TERMINAL_ID, "SPOOFED-TERM")
            .header("baggage", "terminalId=SPOOFED-TERM")
            .exchange()
            .expectStatus().isOk

        val received = assertNotNull(downstreamRequestHeaders.get())
        assertNull(received[TerminalContext.HEADER_TERMINAL_ID])
        assertNull(received["Baggage"])
    }

    @Test
    @DisplayName("permit-all이 아닌 /api/v1/auth/** 경로(logout 등)는 토큰이 필요하다.")
    fun requiresTokenOnNonPermitAllAuthPath() {
        expectError(client.post().uri("/api/v1/auth/logout").exchange(), GatewayErrorCode.MISSING_AUTH_TOKEN)
        assertNull(downstreamRequestHeaders.get())
    }

    @Test
    @DisplayName("Authorization 헤더가 없거나 Bearer 형식이 아니면 401 MISSING_AUTH_TOKEN이다.")
    fun rejectsMissingOrNonBearerToken() {
        expectError(client.get().uri("/api/v1/payments/1").exchange(), GatewayErrorCode.MISSING_AUTH_TOKEN)
        expectError(
            client.get().uri("/api/v1/payments/1").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz").exchange(),
            GatewayErrorCode.MISSING_AUTH_TOKEN,
        )
        expectError(
            client.get().uri("/api/v1/payments/1").header(HttpHeaders.AUTHORIZATION, "Bearer   ").exchange(),
            GatewayErrorCode.MISSING_AUTH_TOKEN,
        )
        assertNull(downstreamRequestHeaders.get())
    }

    @Test
    @DisplayName("검증에 실패한 토큰은 401이고, 응답에는 내부 원인(detail)이 노출되지 않는다.")
    fun hidesRejectionReasonFromResponse() {
        val body = expectError(
            client.get().uri("/api/v1/payments/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${createHs256Token()}")
                .exchange(),
            GatewayErrorCode.INVALID_AUTH_TOKEN,
        )

        assertFalse(body.contains(GatewayErrorMessage.REASON_UNSUPPORTED_ALGORITHM))
        assertFalse(body.contains("HS256"))
        assertNull(downstreamRequestHeaders.get())
    }

    @Test
    @DisplayName("블랙리스트에 등록된 jti는 401 REVOKED_AUTH_TOKEN이다.")
    fun rejectsRevokedToken() {
        `when`(redisTemplate.hasKey("blacklist:jti:jti-001")).thenReturn(Mono.just(true))

        val body = expectError(
            client.get().uri("/api/v1/payments/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken()}")
                .exchange(),
            GatewayErrorCode.REVOKED_AUTH_TOKEN,
        )

        assertFalse(body.contains("jti-001"))
        assertNull(downstreamRequestHeaders.get())
    }

    @Test
    @DisplayName("Redis 조회가 실패하거나 timeout(200ms)을 넘기면 503 TOKEN_STORE_UNAVAILABLE로 fail-closed한다.")
    fun failsClosedWhenTokenStoreUnavailable() {
        `when`(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(IllegalStateException("redis down")))
        expectError(
            client.get().uri("/api/v1/payments/1").header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken()}").exchange(),
            GatewayErrorCode.TOKEN_STORE_UNAVAILABLE,
        )

        `when`(redisTemplate.hasKey(anyString())).thenReturn(Mono.never())
        expectError(
            client.get().uri("/api/v1/payments/1").header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken()}").exchange(),
            GatewayErrorCode.TOKEN_STORE_UNAVAILABLE,
        )

        assertNull(downstreamRequestHeaders.get())
    }

    @Test
    @DisplayName("OpenTelemetry Tracer가 연결되어 있다(K-1).")
    fun wiresOpenTelemetryTracer() {
        assertNotSame(Tracer.NOOP, tracer)
        assertTrue(tracer.javaClass.name.contains("Otel"), "OTel bridge Tracer가 아닙니다: ${tracer.javaClass.name}")
    }

    @Test
    @DisplayName("게이트웨이는 클라이언트 trace를 이어받아 자기 span을 만들고, Request ID와 함께 하위로 전파한다(K-4).")
    fun continuesTraceWithOwnSpanAndPropagatesRequestId() {
        val clientTraceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val clientSpanId = "00f067aa0ba902b7"

        val response = client.get().uri("/api/v1/payments/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken()}")
            .header(TraceContextConstants.HEADER_REQUEST_ID, "req-12345678")
            .header("traceparent", "00-$clientTraceId-$clientSpanId-01")
            .exchange()
            .expectStatus().isOk
            .returnResult(Void::class.java)

        assertEquals("req-12345678", response.responseHeaders.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        assertEquals(clientTraceId, response.responseHeaders.getFirst(TraceContextConstants.HEADER_TRACE_ID))

        val received = assertNotNull(downstreamRequestHeaders.get())
        assertEquals("req-12345678", received.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        val (_, traceId, parentSpanId, _) = assertNotNull(received.getFirst("traceparent")).split("-")
        assertEquals(clientTraceId, traceId)
        assertNotEquals(clientSpanId, parentSpanId, "게이트웨이가 span을 만들지 않고 traceparent를 그대로 넘겼습니다")
    }

    @Test
    @DisplayName("형식이 틀린 X-Request-Id는 교체되고, 응답과 하위 서비스는 같은 새 ID를 받는다.")
    fun replacesInvalidRequestIdForDownstream() {
        val response = client.get().uri("/api/v1/payments/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken()}")
            .header(TraceContextConstants.HEADER_REQUEST_ID, "\${jndi:ldap://x}")
            .exchange()
            .expectStatus().isOk
            .returnResult(Void::class.java)

        val responseId = assertNotNull(response.responseHeaders.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        UUID.fromString(responseId)
        assertEquals(responseId, downstreamRequestHeaders.get()?.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
    }

    private fun validToken(): String =
        createRs256Token(KEY_1, expirationTime = Instant.now().plus(Duration.ofMinutes(10)))

    private fun expectError(
        response: WebTestClient.ResponseSpec,
        errorCode: GatewayErrorCode,
    ): String {
        val result = response
            .expectStatus().isEqualTo(errorCode.status)
            .expectBody()
            .jsonPath("$.code").isEqualTo(errorCode.code)
            .returnResult()
        return String(assertNotNull(result.responseBody))
    }

    @TestConfiguration
    class FakeAuthServiceConfig {

        @Bean
        fun fakeJwksAsyncHttpClientFactory(): AsyncHttpClientFactory =
            FakeJwksAsyncHttpClient().apply { willReturn(toJwksJson(KEY_1)) }.asFactory()
    }

    companion object {
        private val downstreamRequestHeaders = AtomicReference<Headers?>()

        // 하위 서비스(auth-service, payment-ledger-service) 역할. 게이트웨이가 넘긴 헤더를 기록한다.
        private val downstream: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                downstreamRequestHeaders.set(exchange.requestHeaders)
                exchange.sendResponseHeaders(HttpStatus.OK.value(), -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routeToDownstream(registry: DynamicPropertyRegistry) {
            val downstreamUrl = "http://localhost:${downstream.address.port}"
            registry.add("AUTH_SERVICE_URL") { downstreamUrl }
            registry.add("PAYMENT_LEDGER_SERVICE_URL") { downstreamUrl }
        }

        @JvmStatic
        @AfterAll
        fun stopDownstream() {
            downstream.stop(0)
        }
    }
}
