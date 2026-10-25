package io.aegis.platform.observability.config

import io.aegis.platform.core.context.TerminalContext
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class PlatformObservabilityEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (environment.propertySources.contains(PROPERTY_SOURCE_NAME)) {
            return
        }
        environment.propertySources.addLast(MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS))
    }

    companion object {
        const val PROPERTY_SOURCE_NAME = "aegisObservabilityDefaults"

        val DEFAULTS: Map<String, Any> = mapOf(
            "management.tracing.baggage.correlation.fields" to
                "${TerminalContext.BAGGAGE_TERMINAL_ID},${TerminalContext.BAGGAGE_STORE_ID}",
        )
    }
}
