package io.aegis.platform.webflux.config

import io.aegis.platform.webflux.exception.ReactiveGlobalExceptionHandler
import io.aegis.platform.webflux.filter.ReactiveSensitiveHeaderMaskingFilter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class PlatformWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun reactiveGlobalExceptionHandler(): ReactiveGlobalExceptionHandler {
        return ReactiveGlobalExceptionHandler()
    }

    @Bean
    @ConditionalOnMissingBean
    fun reactiveSensitiveHeaderMaskingFilter(): ReactiveSensitiveHeaderMaskingFilter {
        return ReactiveSensitiveHeaderMaskingFilter()
    }

}