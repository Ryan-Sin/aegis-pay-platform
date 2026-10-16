package io.aegis.platform.observability.filter

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class ServletTraceMdcFilter(
    private val tracer: Tracer?,
    private val baggageManager: BaggageManager?,
): OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = (request.getHeader(TraceContextConstants.HEADER_REQUEST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString())

        val traceId = tracer?.currentSpan()?.context()?.traceId()

        response.setHeader(TraceContextConstants.HEADER_REQUEST_ID, requestId)
        if(!traceId.isNullOrBlank()) {
            response.setHeader(TraceContextConstants.HEADER_TRACE_ID, traceId)
        }

        val baggageScope = baggageManager
            ?.createBaggageInScope(TraceContextConstants.HEADER_REQUEST_ID, requestId)

        baggageScope.use {
            filterChain.doFilter(request, response)
        }
    }
}