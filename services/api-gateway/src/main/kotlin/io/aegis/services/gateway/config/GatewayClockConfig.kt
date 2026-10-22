package io.aegis.services.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class GatewayClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
