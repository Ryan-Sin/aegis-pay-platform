package io.aegis.platform.observability.config

import io.aegis.platform.observability.TraceContextConstants
import io.aegis.platform.observability.filter.ReactiveTraceFilter
import io.aegis.platform.observability.filter.ServletTraceMdcFilter
import io.micrometer.context.ContextRegistry
import io.micrometer.context.integration.Slf4jThreadLocalAccessor
import io.micrometer.tracing.Tracer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import java.util.concurrent.atomic.AtomicBoolean

@AutoConfiguration
class PlatformObservabilityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    class ServletObservabilityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun servletTraceMdcFilter(tracerProvider: ObjectProvider<Tracer>): ServletTraceMdcFilter =
            ServletTraceMdcFilter(tracer = tracerProvider.ifAvailable)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(Hooks::class)
    class ReactiveObservabilityConfiguration {

        init {
            registerContextPropagation()
        }

        @Bean
        @ConditionalOnMissingBean
        fun reactiveTraceFilter(tracerProvider: ObjectProvider<Tracer>): ReactiveTraceFilter =
            ReactiveTraceFilter(tracer = tracerProvider.ifAvailable)
    }

    companion object {
        private val contextPropagationRegistered = AtomicBoolean(false)

        /**
         * [ContextRegistry]와 [Hooks]는 JVM 전역이다. 테스트처럼 ApplicationContext가 여러 번 만들어져도
         * 한 번만 등록한다. MDC accessor는 지정한 키만 다루는 selective 모드로 등록해서
         * Micrometer Tracing이 MDC에 넣는 traceId/spanId를 덮어쓰지 않게 한다.
         */
        fun registerContextPropagation() {
            if (!contextPropagationRegistered.compareAndSet(false, true)) return
            ContextRegistry.getInstance().registerThreadLocalAccessor(
                Slf4jThreadLocalAccessor(TraceContextConstants.MDC_REQUEST_ID),
            )
            Hooks.enableAutomaticContextPropagation()
        }
    }
}
