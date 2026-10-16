package io.aegis.platform.httpclient.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.httpclient.SyncHttpClientFactory
import io.aegis.platform.httpclient.support.HttpClientErrorTranslator
import io.aegis.platform.httpclient.support.HttpResilienceExecutor
import io.aegis.platform.httpclient.support.RestClientSyncHttpClientFactory
import io.aegis.platform.httpclient.support.WebClientAsyncHttpClientFactory
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

@AutoConfiguration
class PlatformHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    internal fun httpClientErrorTranslator(
        objectMapperProvider: ObjectProvider<ObjectMapper>,
    ): HttpClientErrorTranslator =
        HttpClientErrorTranslator(
            objectMapper = objectMapperProvider.getIfAvailable { ObjectMapper().findAndRegisterModules() },
        )

    @Bean
    @ConditionalOnMissingBean
    internal fun httpResilienceExecutor(
        circuitBreakerRegistryProvider: ObjectProvider<CircuitBreakerRegistry>,
        retryRegistryProvider: ObjectProvider<RetryRegistry>,
    ): HttpResilienceExecutor =
        HttpResilienceExecutor(
            circuitBreakerRegistry = circuitBreakerRegistryProvider.getIfAvailable { CircuitBreakerRegistry.ofDefaults() },
            retryRegistry = retryRegistryProvider.getIfAvailable { RetryRegistry.ofDefaults() },
        )

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(RestClient::class)
    class ServletHttpClientConfiguration {

        @Bean
        @ConditionalOnMissingBean(SyncHttpClientFactory::class)
        internal fun syncHttpClientFactory(
            restClientBuilderProvider: ObjectProvider<RestClient.Builder>,
            resilienceExecutor: HttpResilienceExecutor,
            errorTranslator: HttpClientErrorTranslator,
        ): SyncHttpClientFactory =
            RestClientSyncHttpClientFactory(
                restClientBuilder = restClientBuilderProvider.getIfAvailable { RestClient.builder() },
                resilienceExecutor = resilienceExecutor,
                errorTranslator = errorTranslator,
            )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebClient::class)
    class ReactiveHttpClientConfiguration {

        @Bean
        @ConditionalOnMissingBean(AsyncHttpClientFactory::class)
        internal fun asyncHttpClientFactory(
            webClientBuilderProvider: ObjectProvider<WebClient.Builder>,
            resilienceExecutor: HttpResilienceExecutor,
            errorTranslator: HttpClientErrorTranslator,
        ): AsyncHttpClientFactory =
            WebClientAsyncHttpClientFactory(
                webClientBuilder = webClientBuilderProvider.getIfAvailable { WebClient.builder() },
                resilienceExecutor = resilienceExecutor,
                errorTranslator = errorTranslator,
            )
    }
}
