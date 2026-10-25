package io.aegis.services.gateway.adapter.`in`.tracing

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboundBaggageRejectingPropagatorTest {

    private val propagator = InboundBaggageRejectingPropagator(
        TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()),
    )

    @Test
    @DisplayName("추출 시 trace context는 이어받고 외부 baggage는 버린다.")
    fun extractsTraceContextButDropsBaggage() {
        val carrier = mapOf(
            "traceparent" to "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "baggage" to "terminalId=SPOOFED-TERM,storeId=SPOOFED-STORE",
        )

        val extracted = propagator.extract(Context.root(), carrier, MapGetter)

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", Span.fromContext(extracted).spanContext.traceId)
        assertTrue(Baggage.fromContext(extracted).isEmpty)
    }

    @Test
    @DisplayName("주입은 바꾸지 않는다: 게이트웨이가 설정한 baggage는 하위로 전파된다.")
    fun injectsBaggageSetByGateway() {
        val context = Context.root().with(
            Baggage.builder().put("terminalId", "TERM-001").put("storeId", "STORE-001").build(),
        )
        val carrier = mutableMapOf<String, String>()

        propagator.inject(context, carrier) { c, key, value -> c?.put(key, value) }

        val baggageHeader = carrier["baggage"].orEmpty()
        assertTrue("terminalId=TERM-001" in baggageHeader)
        assertTrue("storeId=STORE-001" in baggageHeader)
    }

    @Test
    @DisplayName("추출 전에 이미 컨텍스트에 있던 baggage는 유지한다.")
    fun keepsBaggageAlreadyInContext() {
        val context = Context.root().with(Baggage.builder().put("terminalId", "TERM-001").build())

        val extracted = propagator.extract(context, mapOf("baggage" to "terminalId=SPOOFED-TERM"), MapGetter)

        assertEquals("TERM-001", Baggage.fromContext(extracted).getEntryValue("terminalId"))
        assertNull(Baggage.fromContext(extracted).getEntryValue("storeId"))
    }

    private object MapGetter : TextMapGetter<Map<String, String>> {
        override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys
        override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
    }
}
