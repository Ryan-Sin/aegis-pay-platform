package io.aegis.platform.web.config

import io.aegis.platform.web.context.CurrentTerminalArgumentResolver
import io.aegis.platform.web.exception.GlobalExceptionHandler
import io.aegis.platform.web.filter.SensitiveHeaderMaskingFilter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class PlatformWebAutoConfiguration: WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CurrentTerminalArgumentResolver())
    }

    @Bean
    @ConditionalOnMissingBean
    fun globalExceptionHandler(): GlobalExceptionHandler {
        return GlobalExceptionHandler()
    }

    @Bean
    @ConditionalOnMissingBean
    fun sensitiveHeaderMaskingFilter(): SensitiveHeaderMaskingFilter {
        return SensitiveHeaderMaskingFilter()
    }
}