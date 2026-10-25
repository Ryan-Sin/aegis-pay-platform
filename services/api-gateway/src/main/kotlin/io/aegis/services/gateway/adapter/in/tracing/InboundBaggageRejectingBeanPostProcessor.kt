package io.aegis.services.gateway.tracing

import io.opentelemetry.context.propagation.TextMapPropagator
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component

/**
 * Boot가 만든 TextMapPropagator(W3C/B3 composite)를 그대로 두고 추출 규칙만 덧씌운다.
 * 직접 TextMapPropagator 빈을 정의하면 Boot의 propagation 설정(consume/produce)을 다시 구현해야 한다.
 */
@Component
class InboundBaggageRejectingBeanPostProcessor : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
        if (bean is TextMapPropagator && bean !is InboundBaggageRejectingPropagator) {
            InboundBaggageRejectingPropagator(bean)
        } else {
            bean
        }
}
