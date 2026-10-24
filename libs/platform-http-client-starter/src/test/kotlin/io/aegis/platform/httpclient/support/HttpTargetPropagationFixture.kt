package io.aegis.platform.httpclient.support

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.config.HttpTarget
import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.tracing.Tracer
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.SpringBootApplication
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootApplication
class HttpClientTestApplication

/**
 * 실제 Boot tracing instrumentation(OTel)이 baggage·traceparent를 주입한 상태에서
 * 내부/외부 대상이 받는 헤더를 확인하기 위한 공용 fixture(K-3).
 */
object HttpTargetPropagationFixture {

    private val received = AtomicReference<Headers?>()

    // 하위 대상(내부 서비스 또는 VAN) 역할. 받은 요청 헤더를 기록한다.
    private val downstream: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                received.set(exchange.requestHeaders)
                val body = "ok".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }

    fun reset() {
        received.set(null)
    }

    fun internalSpec() = HttpClientSpec(
        targetName = "ledger",
        baseUrl = downstreamUrl(),
        target = HttpTarget.INTERNAL,
        circuitBreakerEnabled = false,
    )

    // target을 지정하지 않는다: 기본값이 EXTERNAL인지도 함께 확인한다.
    fun externalSpec() = HttpClientSpec(
        targetName = "van",
        baseUrl = downstreamUrl(),
        circuitBreakerEnabled = false,
    )

    /** 게이트웨이를 거친 요청을 처리하는 중인 상태(span, baggage, MDC)를 만들고 trace id를 돌려준다. */
    fun withRequestContext(tracer: Tracer, call: () -> Unit): String {
        val span = tracer.nextSpan().name("test-request").start()
        try {
            tracer.withSpan(span).use {
                tracer.createBaggageInScope("terminalId", "TERM-001").use {
                    MDC.put(TraceContextConstants.MDC_REQUEST_ID, "req-12345678")
                    MDC.put(TraceContextConstants.MDC_TERMINAL_ID, "TERM-001")
                    MDC.put(TraceContextConstants.MDC_STORE_ID, "STORE-001")
                    try {
                        call()
                    } finally {
                        MDC.clear()
                    }
                }
            }
        } finally {
            span.end()
        }
        return span.context().traceId()
    }

    fun assertInternalContextPropagated(traceId: String) {
        val headers = assertNotNull(received.get(), "하위 대상이 요청을 받지 못했습니다")
        assertEquals("req-12345678", headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        assertEquals("TERM-001", headers.getFirst(TerminalContext.HEADER_TERMINAL_ID))
        assertEquals("STORE-001", headers.getFirst(TerminalContext.HEADER_STORE_ID))
        assertTrue(headers.getFirst("baggage").orEmpty().contains("terminalId=TERM-001"), "baggage: ${headers["baggage"]}")
        assertTrue(headers.getFirst("traceparent").orEmpty().contains(traceId), "traceparent: ${headers["traceparent"]}")
    }

    fun assertOnlyTraceContextPropagated(traceId: String) {
        val headers = assertNotNull(received.get(), "하위 대상이 요청을 받지 못했습니다")
        assertNull(headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        assertNull(headers.getFirst(TerminalContext.HEADER_TERMINAL_ID))
        assertNull(headers.getFirst(TerminalContext.HEADER_STORE_ID))
        assertNull(headers.getFirst("baggage"), "외부 대상에 baggage가 전달되었습니다")
        assertTrue(headers.getFirst("traceparent").orEmpty().contains(traceId), "traceparent: ${headers["traceparent"]}")
    }

    private fun downstreamUrl() = "http://localhost:${downstream.address.port}"
}
