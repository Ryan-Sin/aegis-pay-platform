package io.aegis.platform.observability.filter

import io.aegis.platform.observability.TraceContextConstants
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ServletTraceMdcFilterTest {

    private val filter = ServletTraceMdcFilter(tracer = null)

    @Test
    @DisplayName("요청 처리 중에는 MDC에 requestId가 있고, 끝나면 지워진다.")
    fun putsRequestIdInMdcDuringRequestOnly() {
        val request = MockHttpServletRequest().apply { addHeader(TraceContextConstants.HEADER_REQUEST_ID, "req-12345678") }
        val response = MockHttpServletResponse()
        var seenInChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ -> seenInChain = MDC.get(TraceContextConstants.MDC_REQUEST_ID) })

        assertEquals("req-12345678", seenInChain)
        assertEquals("req-12345678", response.getHeader(TraceContextConstants.HEADER_REQUEST_ID))
        assertNull(MDC.get(TraceContextConstants.MDC_REQUEST_ID))
    }

    @Test
    @DisplayName("체인에서 예외가 나도 MDC는 지워진다(스레드 풀 재사용 시 누수 방지).")
    fun clearsMdcEvenWhenChainThrows() {
        val request = MockHttpServletRequest().apply { addHeader(TraceContextConstants.HEADER_REQUEST_ID, "req-12345678") }

        assertFailsWith<IllegalStateException> {
            filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ -> throw IllegalStateException("boom") })
        }

        assertNull(MDC.get(TraceContextConstants.MDC_REQUEST_ID))
    }

    @Test
    @DisplayName("형식이 틀린 ID는 새 UUID로 교체해서 MDC와 응답 헤더에 싣는다.")
    fun replacesInvalidId() {
        val request = MockHttpServletRequest().apply { addHeader(TraceContextConstants.HEADER_REQUEST_ID, "\${jndi:x}") }
        val response = MockHttpServletResponse()
        var seenInChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ -> seenInChain = MDC.get(TraceContextConstants.MDC_REQUEST_ID) })

        UUID.fromString(seenInChain)
        assertEquals(seenInChain, response.getHeader(TraceContextConstants.HEADER_REQUEST_ID))
    }
}
