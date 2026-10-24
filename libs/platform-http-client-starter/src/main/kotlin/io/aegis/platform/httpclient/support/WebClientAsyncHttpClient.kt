package io.aegis.platform.httpclient.support

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.httpclient.AsyncHttpClient
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.exception.HttpClientErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

internal class WebClientAsyncHttpClient(
    private val spec: HttpClientSpec,
    private val webClient: WebClient,
    private val resilienceExecutor: HttpResilienceExecutor,
    private val errorTranslator: HttpClientErrorTranslator,
) : AsyncHttpClient {

    override suspend fun <T : Any> get(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.GET, uri, null, responseType, headers)

    override suspend fun <T : Any> post(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.POST, uri, body, responseType, headers)

    override suspend fun <T : Any> put(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.PUT, uri, body, responseType, headers)

    override suspend fun <T : Any> patch(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.PATCH, uri, body, responseType, headers)

    override suspend fun <T : Any> delete(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.DELETE, uri, null, responseType, headers)

    private suspend fun <T : Any> execute(
        method: HttpMethod,
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T {
        return try {
            resilienceExecutor.executeSuspend(spec) {
                performRequest(method, uri, body, responseType, headers)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: WebClientResponseException) {
            throw errorTranslator.translateHttpStatus(
                targetName = spec.targetName,
                statusCode = ex.statusCode.value(),
                responseBody = ex.responseBodyAsString,
            )
        } catch (ex: Throwable) {
            throw errorTranslator.translateException(
                targetName = spec.targetName,
                uri = uri,
                throwable = ex,
            )
        }
    }

    private suspend fun <T : Any> performRequest(
        method: HttpMethod,
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T {
        val requestSpec = webClient.method(method)
            .uri(uri)
            .headers { httpHeaders ->
                HttpHeaderPropagator.applyHeaders(httpHeaders, headers, spec.target)
            }

        val responseSpec = if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve()
        } else {
            requestSpec.retrieve()
        }

        return responseSpec.bodyToMono(responseType).awaitSingleOrNull()
            ?: throw BusinessException(
                HttpClientErrorCode.EXTERNAL_API_EMPTY_RESPONSE,
                spec.targetName,
                uri,
            )
    }
}