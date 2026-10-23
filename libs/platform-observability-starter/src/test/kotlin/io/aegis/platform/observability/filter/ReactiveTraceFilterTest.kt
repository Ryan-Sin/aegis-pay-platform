package io.aegis.platform.observability.filter

import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.tracing.otel.bridge.OtelBaggageManager
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReactiveTraceFilterTest {

    private val currentTraceContext = OtelCurrentTraceContext()
    private val baggageManager = OtelBaggageManager(
        currentTraceContext,
        listOf(TraceContextConstants.HEADER_REQUEST_ID),
        emptyList(),
    )
    private val filter = ReactiveTraceFilter(
        tracer = null,
        baggageManager = baggageManager,
    )

    @AfterEach
    fun tearDown() {
        Hooks.resetOnEachOperator()
    }

    @Test
    @DisplayName("비동기 스레드 전환 시에도 Baggage가 전파되어야 하며 요청 종료 후 호출 스레드에 누수되지 않아야 한다")
    fun shouldPropagateBaggageAcrossThreadsWithoutLeakingOnCallerThread() {
        Hooks.enableAutomaticContextPropagation()

        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/payments")
                .header(TraceContextConstants.HEADER_REQUEST_ID, "req-123")
                .build(),
        )

        val baggageInsideAsyncChain = AtomicReference<String?>()

        val chain = WebFilterChain {
            Mono.fromCallable {
                baggageInsideAsyncChain.set(
                    baggageManager.getBaggage(TraceContextConstants.HEADER_REQUEST_ID)?.get(),
                )
            }
                .subscribeOn(Schedulers.boundedElastic())
                .then()
        }

        filter.filter(exchange, chain).block()

        val leakedBaggageOnCallerThread = baggageManager.getBaggage(TraceContextConstants.HEADER_REQUEST_ID)?.get()
        assertNull(leakedBaggageOnCallerThread, "호출 스레드에 닫히지 않은 Baggage가 누수되었습니다!")
        assertEquals("req-123", baggageInsideAsyncChain.get())
    }
}
