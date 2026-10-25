package io.aegis.platform.httpclient.support

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.config.HttpTarget
import io.aegis.platform.observability.TraceContextConstants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpHeaderPropagatorTest {

    @BeforeEach
    fun setUp() {
        MDC.put(TraceContextConstants.MDC_REQUEST_ID, "req-12345678")
        MDC.put(TraceContextConstants.MDC_TERMINAL_ID, "TERM-001")
        MDC.put(TraceContextConstants.MDC_STORE_ID, "STORE-001")
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    @DisplayName("내부 대상에는 MDC의 Request ID만 헤더로 전파한다. 단말 컨텍스트는 헤더로 복사하지 않는다(baggage가 담당, ADR-0007).")
    fun propagatesOnlyRequestIdToInternalTarget() {
        val headers = HttpHeaders()

        HttpHeaderPropagator.applyHeaders(headers, emptyMap(), HttpTarget.INTERNAL)

        assertEquals("req-12345678", headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        assertNull(headers.getFirst(TerminalContext.HEADER_TERMINAL_ID))
        assertNull(headers.getFirst(TerminalContext.HEADER_STORE_ID))
    }

    @Test
    @DisplayName("외부 대상용 정리는 baggage 헤더와 baggage 필드 이름의 개별 헤더(terminalId, storeId)를 모두 지운다.")
    fun stripsBaggageAndFieldHeaders() {
        val headers = HttpHeaders().apply {
            set("baggage", "terminalId=TERM-001")
            set("Terminalid", "TERM-001")
            set("storeid", "STORE-001")
            set("traceparent", "00-abc-def-01")
        }

        HttpHeaderPropagator.stripBaggage(headers)

        assertNull(headers.getFirst("baggage"))
        assertNull(headers.getFirst(TerminalContext.BAGGAGE_TERMINAL_ID))
        assertNull(headers.getFirst(TerminalContext.BAGGAGE_STORE_ID))
        assertEquals("00-abc-def-01", headers.getFirst("traceparent"))
    }

    @Test
    @DisplayName("외부 대상에는 MDC 값을 전파하지 않고, 호출 측이 명시한 헤더만 보낸다.")
    fun doesNotPropagateMdcToExternalTarget() {
        val headers = HttpHeaders()

        HttpHeaderPropagator.applyHeaders(headers, mapOf("X-Van-Merchant-No" to "M-1"), HttpTarget.EXTERNAL)

        assertNull(headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))
        assertNull(headers.getFirst(TerminalContext.HEADER_TERMINAL_ID))
        assertNull(headers.getFirst(TerminalContext.HEADER_STORE_ID))
        assertEquals("M-1", headers.getFirst("X-Van-Merchant-No"))
    }

    @Test
    @DisplayName("호출 측이 명시한 헤더가 MDC 값보다 우선한다.")
    fun customHeaderOverridesMdc() {
        val headers = HttpHeaders()

        HttpHeaderPropagator.applyHeaders(
            headers,
            mapOf(TraceContextConstants.HEADER_REQUEST_ID to "req-override"),
            HttpTarget.INTERNAL,
        )

        assertEquals(listOf("req-override"), headers.getValuesAsList(TraceContextConstants.HEADER_REQUEST_ID))
    }

    @Test
    @DisplayName("대상을 지정하지 않은 HttpClientSpec은 외부 대상으로 취급한다(누락 시 내부 정보 미전파).")
    fun defaultsToExternalTarget() {
        assertEquals(HttpTarget.EXTERNAL, HttpClientSpec(targetName = "van", baseUrl = "http://van").target)
    }
}
