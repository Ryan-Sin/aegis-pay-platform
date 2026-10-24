package io.aegis.platform.httpclient.support

import io.aegis.platform.httpclient.SyncHttpClient
import io.aegis.platform.httpclient.SyncHttpClientFactory
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.config.HttpTarget
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

internal class RestClientSyncHttpClientFactory(
    private val restClientBuilder: RestClient.Builder,
    private val resilienceExecutor: HttpResilienceExecutor,
    private val errorTranslator: HttpClientErrorTranslator,
) : SyncHttpClientFactory {

    override fun create(spec: HttpClientSpec): SyncHttpClient {
        val jdkHttpClient = HttpClient.newBuilder()
            .connectTimeout(spec.connectTimeout)
            .build()

        val requestFactory = JdkClientHttpRequestFactory(jdkHttpClient).apply {
            setReadTimeout(spec.readTimeout)
        }

        val restClient = restClientBuilder.clone()
            .baseUrl(spec.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeaders { headers ->
                spec.defaultHeaders.forEach(headers::set)
            }
            .also { if (spec.target == HttpTarget.EXTERNAL) it.requestInterceptor(STRIP_BAGGAGE_INTERCEPTOR) }
            .build()

        return RestClientSyncHttpClient(
            spec = spec,
            restClient = restClient,
            resilienceExecutor = resilienceExecutor,
            errorTranslator = errorTranslator,
        )
    }

    companion object {
        // tracing instrumentation이 주입한 baggage는 interceptor 단계에서만 지울 수 있다.
        private val STRIP_BAGGAGE_INTERCEPTOR = ClientHttpRequestInterceptor { request, body, execution ->
            HttpHeaderPropagator.stripBaggage(request.headers)
            execution.execute(request, body)
        }
    }
}
