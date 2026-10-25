package io.aegis.services.auth.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar

@TestConfiguration(proxyBeanMethods = false)
class LocalStackKmsConfiguration {
    @Bean
    fun localStackKmsProperties(): DynamicPropertyRegistrar = DynamicPropertyRegistrar { registry ->
        registry.add("aegis.auth.kms.endpoint") { LocalStackKms.endpoint }
        registry.add("aegis.auth.kms.region") { LocalStackKms.REGION }
        registry.add("aegis.auth.kms.access-key-id") { LocalStackKms.container.accessKey }
        registry.add("aegis.auth.kms.secret-access-key") { LocalStackKms.container.secretKey }
    }
}
