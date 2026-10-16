package io.aegis.platform.httpclient.support

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.httpclient.SyncHttpClient
import io.aegis.platform.httpclient.config.HttpClientSpec
import io.aegis.platform.httpclient.exception.HttpClientErrorCode
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

internal class RestClientSyncHttpClient(
    private val spec: HttpClientSpec,
    private val restClient: RestClient,
    private val resilienceExecutor: HttpResilienceExecutor,
    private val errorTranslator: HttpClientErrorTranslator,
) : SyncHttpClient {

    override fun <T : Any> get(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.GET, uri, null, responseType, headers)

    override fun <T : Any> post(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.POST, uri, body, responseType, headers)

    override fun <T : Any> put(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.PUT, uri, body, responseType, headers)

    override fun <T : Any> patch(
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.PATCH, uri, body, responseType, headers)

    override fun <T : Any> delete(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T = execute(HttpMethod.DELETE, uri, null, responseType, headers)

    private fun <T : Any> execute(
        method: HttpMethod,
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T {
        return try {
            resilienceExecutor.executeSync(spec) {
                performRequest(method, uri, body, responseType, headers)
            }
        } catch (ex: RestClientResponseException) {
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

    private fun <T : Any> performRequest(
        method: HttpMethod,
        uri: String,
        body: Any?,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): T {
        val requestSpec = restClient.method(method)
            .uri(uri)
            .headers { httpHeaders ->
                HttpHeaderPropagator.applyHeaders(httpHeaders, headers)
            }

        if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON).body(body)
        }

        return requestSpec.retrieve().body(responseType)
            ?: throw BusinessException(
                HttpClientErrorCode.EXTERNAL_API_EMPTY_RESPONSE,
                spec.targetName,
                uri,
            )
    }
}