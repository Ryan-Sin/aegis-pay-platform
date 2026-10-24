package io.aegis.platform.observability.filter

import io.aegis.platform.observability.RequestIds
import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Request ID를 검증·생성해서 응답 헤더와 MDC에 싣는다(ADR-0002).
 * servlet은 요청 하나를 한 스레드가 끝까지 처리하므로 MDC에 직접 쓰고 finally에서 반드시 지운다.
 */
class ServletTraceMdcFilter(
    private val tracer: Tracer?,
) : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = FILTER_ORDER

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = RequestIds.resolve(request.getHeader(TraceContextConstants.HEADER_REQUEST_ID))
        response.setHeader(TraceContextConstants.HEADER_REQUEST_ID, requestId)
        tracer?.currentSpan()?.context()?.traceId()?.let {
            response.setHeader(TraceContextConstants.HEADER_TRACE_ID, it)
        }

        MDC.put(TraceContextConstants.MDC_REQUEST_ID, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TraceContextConstants.MDC_REQUEST_ID)
        }
    }

    companion object {
        // Boot의 ServerHttpObservationFilter(HIGHEST_PRECEDENCE + 1) 다음에 실행해야 현재 span을 읽을 수 있다.
        const val FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10
    }
}
