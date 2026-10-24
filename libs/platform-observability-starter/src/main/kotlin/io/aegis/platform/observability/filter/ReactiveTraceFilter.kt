package io.aegis.platform.observability.filter

import io.aegis.platform.observability.RequestIds
import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.context.integration.Slf4jThreadLocalAccessor
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import io.micrometer.tracing.Tracer
import org.springframework.core.Ordered
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.ContextView

/**
 * Request ID를 검증·생성해서 요청 헤더(하위 전파), 응답 헤더, MDC에 싣는다(ADR-0002).
 *
 * MDC에는 직접 쓰지 않는다. Reactor Context의 [Slf4jThreadLocalAccessor.KEY]에 넣으면
 * 등록된 accessor가 연산자를 실행하는 스레드마다 MDC를 복원하고 정리한다.
 */
class ReactiveTraceFilter(
    private val tracer: Tracer?,
) : WebFilter, Ordered {

    private val snapshotFactory = ContextSnapshotFactory.builder().build()

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val requestId = RequestIds.resolve(exchange.request.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID))

        val mutatedExchange = exchange.mutate()
            .request { request -> request.headers { it.set(TraceContextConstants.HEADER_REQUEST_ID, requestId) } }
            .build()
        mutatedExchange.response.headers.set(TraceContextConstants.HEADER_REQUEST_ID, requestId)

        return Mono.deferContextual { contextView ->
            currentTraceId(contextView)?.let {
                mutatedExchange.response.headers.set(TraceContextConstants.HEADER_TRACE_ID, it)
            }
            chain.filter(mutatedExchange)
        }.contextWrite { context ->
            context.put(Slf4jThreadLocalAccessor.KEY, mapOf(TraceContextConstants.MDC_REQUEST_ID to requestId))
        }
    }

    // 서버 observation은 ThreadLocal이 아니라 Reactor Context에 있으므로 snapshot으로 잠시 복원해서 읽는다.
    private fun currentTraceId(contextView: ContextView): String? {
        val tracer = tracer ?: return null
        return snapshotFactory.setThreadLocalsFrom<ContextView>(contextView, ObservationThreadLocalAccessor.KEY).use {
            tracer.currentSpan()?.context()?.traceId()
        }
    }

    companion object {
        // 서버 observation(HttpWebHandlerAdapter)은 모든 WebFilter보다 먼저 시작된다. 그 안에서 가장 먼저 실행한다.
        const val FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10
    }
}
