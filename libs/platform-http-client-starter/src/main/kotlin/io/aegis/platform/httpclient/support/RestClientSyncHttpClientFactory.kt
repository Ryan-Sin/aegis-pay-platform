package io.aegis.platform.httpclient.support

import io.aegis.platform.httpclient.SyncHttpClient
import io.aegis.platform.httpclient.SyncHttpClientFactory
import io.aegis.platform.httpclient.config.HttpClientSpec
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
            .build()

        return RestClientSyncHttpClient(
            spec = spec,
            restClient = restClient,
            resilienceExecutor = resilienceExecutor,
            errorTranslator = errorTranslator,
        )
    }
}
