package io.aegis.services.auth.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar

@TestConfiguration(proxyBeanMethods = false)
class ValkeyContainerConfiguration {

    @Bean
    fun valkeyProperties(): DynamicPropertyRegistrar = DynamicPropertyRegistrar { registry ->
        registry.add("spring.data.redis.host") { ValkeyContainer.host }
        registry.add("spring.data.redis.port") { ValkeyContainer.port }
    }
}
