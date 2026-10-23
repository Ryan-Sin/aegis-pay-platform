package io.aegis.services.gateway.support

import io.aegis.platform.httpclient.AsyncHttpClient
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import org.springframework.core.ParameterizedTypeReference
import java.util.concurrent.atomic.AtomicInteger

class FakeJwksAsyncHttpClient : AsyncHttpClient {

    private var handler: () -> String = { error("FakeJwksAsyncHttpClient: willReturn() 또는 willThrow()로 응답을 먼저 준비하세요") }

    private val callCount = AtomicInteger(0)

    val fetchCount: Int
        get() = callCount.get()

    fun willReturn(jwksJson: String) {
        handler = { jwksJson }
    }

    fun willThrow(throwable: Throwable) {
        handler = { throw throwable }
    }

    fun asFactory(): AsyncHttpClientFactory = AsyncHttpClientFactory { this }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>
    ): T {
        callCount.incrementAndGet()
        return handler() as T
    }

    override suspend fun <T : Any> post(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>
    ): T = throw UnsupportedOperationException()

    override suspend fun <T : Any> put(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>
    ): T = throw UnsupportedOperationException()

    override suspend fun <T : Any> patch(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>
    ): T = throw UnsupportedOperationException()

    override suspend fun <T : Any> delete(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>
    ): T = throw UnsupportedOperationException()

}