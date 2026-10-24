package io.aegis.platform.httpclient.support

import io.aegis.platform.httpclient.AsyncHttpClient
import io.aegis.platform.httpclient.AsyncHttpClientFactory
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.config.HttpTarget
import io.netty.channel.ChannelOption
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
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
            .also { if (spec.target == HttpTarget.EXTERNAL) it.filter(STRIP_BAGGAGE_FILTER) }
            .build()

        return WebClientAsyncHttpClient(
            spec = spec,
            webClient = webClient,
            resilienceExecutor = resilienceExecutor,
            errorTranslator = errorTranslator,
        )
    }

    companion object {
        // tracing instrumentation이 주입한 baggage는 filter 단계에서만 지울 수 있다.
        private val STRIP_BAGGAGE_FILTER = ExchangeFilterFunction { request, next ->
            next.exchange(ClientRequest.from(request).headers(HttpHeaderPropagator::stripBaggage).build())
        }
    }
}
