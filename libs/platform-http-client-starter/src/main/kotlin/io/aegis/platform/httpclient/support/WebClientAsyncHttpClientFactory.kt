package io.aegis.platform.httpclient.support

import io.aegis.platform.httpclient.AsyncHttpClient
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.netty.channel.ChannelOption
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

internal class WebClientAsyncHttpClientFactory(
    private val webClientBuilder: WebClient.Builder,
    private val resilienceExecutor: HttpResilienceExecutor,
    private val errorTranslator: HttpClientErrorTranslator,
) : AsyncHttpClientFactory {

    override fun create(spec: HttpClientSpec): AsyncHttpClient {
        val nettyHttpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, spec.connectTimeout.toMillis().toInt())
            .responseTimeout(spec.readTimeout)

        val webClient = webClientBuilder.clone()
            .baseUrl(spec.baseUrl)
            .clientConnector(ReactorClientHttpConnector(nettyHttpClient))
            .defaultHeaders { headers ->
                spec.defaultHeaders.forEach(headers::set)
            }
            .build()

        return WebClientAsyncHttpClient(
            spec = spec,
            webClient = webClient,
            resilienceExecutor = resilienceExecutor,
            errorTranslator = errorTranslator,
        )
    }
}
