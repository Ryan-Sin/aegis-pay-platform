package io.aegis.platform.observability.filter
import io.aegis.platform.observability.TraceContextConstants
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ThreadLocalAccessor
import io.micrometer.tracing.BaggageInScope
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

    init {
        if (baggageManager != null) {
            ContextRegistry.getInstance().registerThreadLocalAccessor(
                RequestIdBaggageThreadLocalAccessor(baggageManager),
            )
        }
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> = Mono.defer {
        val requestId = exchange.request.headers.getFirst(TraceContextConstants.HEADER_REQUEST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        val traceId = tracer?.currentSpan()?.context()?.traceId()

        exchange.response.headers.set(TraceContextConstants.HEADER_REQUEST_ID, requestId)
        if (!traceId.isNullOrBlank()) {
            exchange.response.headers.set(TraceContextConstants.HEADER_TRACE_ID, traceId)
        }

        chain.filter(exchange)
            .contextWrite { context ->
                context.put(TraceContextConstants.HEADER_REQUEST_ID, requestId)
            }
    }

    private class RequestIdBaggageThreadLocalAccessor(
        private val baggageManager: BaggageManager,
    ) : ThreadLocalAccessor<String> {

        private val scopeStack = ThreadLocal.withInitial { ArrayDeque<BaggageInScope>() }

        override fun key(): Any = TraceContextConstants.HEADER_REQUEST_ID

        override fun getValue(): String? =
            baggageManager.getBaggage(TraceContextConstants.HEADER_REQUEST_ID)?.get()

        override fun setValue(value: String) {
            val scope = baggageManager.createBaggageInScope(TraceContextConstants.HEADER_REQUEST_ID, value)
            scopeStack.get().addLast(scope)
        }

        override fun setValue() {
            closeCurrentScope()
        }

        override fun restore(previousValue: String) {
            closeCurrentScope()
        }

        private fun closeCurrentScope() {
            val stack = scopeStack.get()
            stack.removeLastOrNull()?.close()
            if (stack.isEmpty()) {
                scopeStack.remove()
            }
        }
    }
}