package io.aegis.platform.observability.filter

import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.Tracer
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

class ReactiveTraceFilter(
    private val tracer: Tracer?,
    private val baggageManager: BaggageManager?,
) : WebFilter {

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val requestId = exchange.request.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        val traceId = tracer?.currentSpan()?.context()?.traceId()

        exchange.response.headers[TraceContextConstants.HEADER_REQUEST_ID] = requestId
        if (!traceId.isNullOrBlank()) {
            exchange.response.headers[TraceContextConstants.HEADER_TRACE_ID] = traceId
        }

        val baggageScope = baggageManager
            ?.createBaggageInScope(TraceContextConstants.HEADER_REQUEST_ID, requestId)

        return chain.filter(exchange)
            .doFinally {
                baggageScope?.close()
            }
    }
}