package io.aegis.platform.observability.config

import io.aegis.platform.observability.filter.ReactiveTraceFilter
import io.aegis.platform.observability.filter.ServletTraceMdcFilter
import io.micrometer.tracing.BaggageManager
import io.micrometer.tracing.Tracer
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@AutoConfiguration
class PlatformObservabilityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    class ServletObservabilityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun servletTraceMdcFilter(
            tracerProvider: ObjectProvider<Tracer>,
            baggageManagerProvider: ObjectProvider<BaggageManager>
        ): ServletTraceMdcFilter = ServletTraceMdcFilter(
            tracer = tracerProvider.ifAvailable,
            baggageManager = baggageManagerProvider.ifAvailable,
        )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(Hooks::class)
    class ReactiveObservabilityConfiguration {

        @PostConstruct
        fun enableAutomaticContextPropagation() {
            Hooks.enableAutomaticContextPropagation()
        }

        @Bean
        @ConditionalOnMissingBean
        fun reactiveTraceFilter(
            tracerProvider: ObjectProvider<Tracer>,
            baggageManagerProvider: ObjectProvider<BaggageManager>,
        ): ReactiveTraceFilter {
            return ReactiveTraceFilter(
                tracer = tracerProvider.ifAvailable,
                baggageManager = baggageManagerProvider.ifAvailable,
            )
        }
    }
}