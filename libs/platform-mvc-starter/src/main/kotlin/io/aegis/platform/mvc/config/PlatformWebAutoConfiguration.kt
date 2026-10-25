package io.aegis.platform.mvc.config

import io.aegis.platform.mvc.context.CurrentTerminalArgumentResolver
import io.aegis.platform.mvc.exception.GlobalExceptionHandler
import io.aegis.platform.mvc.filter.SensitiveHeaderMaskingFilter
import io.micrometer.tracing.BaggageManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class PlatformWebAutoConfiguration(
    private val baggageManager: ObjectProvider<BaggageManager>,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CurrentTerminalArgumentResolver(baggageManager.ifAvailable))
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