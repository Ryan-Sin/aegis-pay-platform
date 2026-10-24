package io.aegis.platform.observability.filter

import io.aegis.platform.observability.TraceContextConstants
import io.aegis.platform.observability.config.PlatformObservabilityAutoConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.MDC
import org.springframework.core.PropagationContextElement
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactiveTraceFilterTest {

    private val filter = ReactiveTraceFilter(tracer = null)

    @Test
    @DisplayName("스레드가 바뀐 뒤에도 MDC에서 requestId를 읽을 수 있고, 요청이 끝나면 스레드에 남지 않는다.")
    fun propagatesMdcAcrossThreadsWithoutLeaking() {
        val callerThread = Thread.currentThread()
        val seenThread = AtomicReference<Thread>()
        val chain = WebFilterChain {
            Mono.just(Unit)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext {
                    seenThread.set(Thread.currentThread())
                    assertEquals("req-12345678", MDC.get(TraceContextConstants.MDC_REQUEST_ID))
                }
                .then()
        }

        filter.filter(exchangeWithRequestId("req-12345678"), chain).block()

        assertNotEquals(callerThread, seenThread.get())
        assertNull(MDC.get(TraceContextConstants.MDC_REQUEST_ID), "호출 스레드에 requestId가 남았습니다")
        val leakedOnWorker = Mono.fromCallable { MDC.get(TraceContextConstants.MDC_REQUEST_ID) ?: "none" }
            .subscribeOn(Schedulers.boundedElastic())
            .block()
        assertEquals("none", leakedOnWorker, "작업 스레드에 이전 요청의 requestId가 남았습니다")
    }

    @Test
    @DisplayName("유효한 ID는 요청 헤더(하위 전파)와 응답 헤더에 그대로 실린다.")
    fun keepsValidIdOnRequestAndResponse() {
        val forwarded = AtomicReference<ServerWebExchange>()
        val exchange = exchangeWithRequestId("req-12345678")

        filter.filter(exchange, captureExchange(forwarded)).block()

        assertEquals("req-12345678", forwarded.get().request.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        assertEquals("req-12345678", exchange.response.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
    }

    @ParameterizedTest
    @ValueSource(strings = ["short", "        ", "\${jndi:x}", "req-1234\r\nX-Injected: 1"])
    @DisplayName("형식이 틀린 ID는 새 UUID로 교체되고, 하위로 넘기는 요청 헤더도 교체된 값이다.")
    fun replacesInvalidIdOnRequestAndResponse(invalidId: String) {
        val forwarded = AtomicReference<ServerWebExchange>()
        val exchange = exchangeWithRequestId(invalidId)

        filter.filter(exchange, captureExchange(forwarded)).block()

        val responseId = exchange.response.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID)
        UUID.fromString(responseId)
        assertEquals(listOf(responseId), forwarded.get().request.headers.getValuesAsList(TraceContextConstants.HEADER_REQUEST_ID))
    }

    @Test
    @DisplayName("헤더가 없으면 새 UUID를 생성한다.")
    fun generatesIdWhenAbsent() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments").build())

        filter.filter(exchange) { Mono.empty() }.block()

        UUID.fromString(exchange.response.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
    }

    @Test
    @DisplayName("동시 요청 200건에서 각 요청은 자기 requestId만 본다.")
    fun isolatesRequestIdsUnderConcurrency() {
        val mismatches = Flux.range(1, 200)
            .flatMap { i ->
                val requestId = "req-%08d".format(i)
                val seen = AtomicReference<String?>()
                val chain = WebFilterChain {
                    Mono.delay(Duration.ofMillis(Random.nextLong(1, 20)))
                        .publishOn(Schedulers.boundedElastic())
                        .doOnNext { seen.set(MDC.get(TraceContextConstants.MDC_REQUEST_ID)) }
                        .then()
                }
                filter.filter(exchangeWithRequestId(requestId), chain)
                    .then(Mono.fromSupplier { requestId to seen.get() })
            }
            .filter { (expected, seen) -> expected != seen }
            .collectList()
            .block()!!

        assertTrue(mismatches.isEmpty(), "다른 요청의 requestId를 본 경우: $mismatches")
    }

    @Test
    @DisplayName("코루틴(mono {})은 PropagationContextElement를 붙여야 MDC가 전파되고, 디스패처를 바꿔도 유지된다.")
    fun propagatesMdcIntoCoroutineWithPropagationContextElement() {
        val withoutElement = AtomicReference<String?>()
        val withElement = AtomicReference<String?>()
        val afterDispatcherSwitch = AtomicReference<String?>()
        val chain = WebFilterChain {
            mono { withoutElement.set(MDC.get(TraceContextConstants.MDC_REQUEST_ID)) }
                .then(
                    mono(PropagationContextElement()) {
                        withElement.set(MDC.get(TraceContextConstants.MDC_REQUEST_ID))
                        withContext(Dispatchers.IO) {
                            afterDispatcherSwitch.set(MDC.get(TraceContextConstants.MDC_REQUEST_ID))
                        }
                    },
                )
                .then()
        }

        filter.filter(exchangeWithRequestId("req-12345678"), chain).block()

        assertNull(withoutElement.get(), "PropagationContextElement 없이도 전파된다면 이 테스트의 전제가 바뀐 것이다")
        assertEquals("req-12345678", withElement.get())
        assertEquals("req-12345678", afterDispatcherSwitch.get())
    }

    private fun exchangeWithRequestId(requestId: String): MockServerWebExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/payments")
                .header(TraceContextConstants.HEADER_REQUEST_ID, requestId)
                .build(),
        )

    private fun captureExchange(target: AtomicReference<ServerWebExchange>) = WebFilterChain { exchange ->
        target.set(exchange)
        Mono.empty()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerContextPropagation() {
            PlatformObservabilityAutoConfiguration.registerContextPropagation()
        }
    }
}
