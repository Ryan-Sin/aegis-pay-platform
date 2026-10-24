package io.aegis.services.gateway.tracing

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter

/**
 * 외부 요청에서 baggage를 추출하지 않는 propagator. trace context(traceparent)는 그대로 이어받는다.
 *
 * 서버 observation은 모든 WebFilter/GlobalFilter보다 먼저 요청 헤더에서 컨텍스트를 추출한다.
 * 그래서 HeaderSanitizer가 `baggage` 헤더를 지워도 이미 추출된 값이 하위 호출에 다시 주입된다.
 * 단말 컨텍스트 baggage는 게이트웨이만 JWT 검증 결과로 설정해야 하므로(ARCHITECTURE 5장) 추출 단계에서 버린다.
 */
class InboundBaggageRejectingPropagator(
    private val delegate: TextMapPropagator,
) : TextMapPropagator {

    override fun fields(): Collection<String> = delegate.fields()

    override fun <C> inject(context: Context, carrier: C?, setter: TextMapSetter<C>) {
        delegate.inject(context, carrier, setter)
    }

    override fun <C> extract(context: Context, carrier: C?, getter: TextMapGetter<C>): Context {
        val baggageBeforeExtract = Baggage.fromContext(context)
        return delegate.extract(context, carrier, getter).with(baggageBeforeExtract)
    }
}
