package io.aegis.platform.httpclient.support

import io.aegis.platform.httpclient.config.HttpClientSpec
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry

internal class HttpResilienceExecutor(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
) {
    fun <T> executeSync(
        spec: HttpClientSpec,
        block: () -> T,
    ): T {
        var decorated = block
        if (spec.circuitBreakerEnabled) {
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(spec.targetName)
            val prev = decorated
            decorated = { circuitBreaker.executeSupplier(prev) }
        }
        if (spec.retryEnabled) {
            val retry = retryRegistry.retry(spec.targetName)
            val prev = decorated
            decorated = { retry.executeSupplier(prev) }
        }
        return decorated.invoke()
    }

    suspend fun <T> executeSuspend(
        spec: HttpClientSpec,
        block: suspend () -> T,
    ): T {
        var decorated = block
        if (spec.circuitBreakerEnabled) {
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(spec.targetName)
            val prev = decorated
            decorated = { circuitBreaker.executeSuspendFunction { prev() } }
        }
        if (spec.retryEnabled) {
            val retry = retryRegistry.retry(spec.targetName)
            val prev = decorated
            decorated = { retry.executeSuspendFunction { prev() } }
        }
        return decorated.invoke()
    }
}