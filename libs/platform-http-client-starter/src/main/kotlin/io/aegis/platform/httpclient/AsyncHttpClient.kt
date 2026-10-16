package io.aegis.platform.httpclient

import io.aegis.platform.httpclient.config.HttpClientSpec
import org.springframework.core.ParameterizedTypeReference

fun interface AsyncHttpClientFactory {
    fun create(spec: HttpClientSpec): AsyncHttpClient
}

interface AsyncHttpClient {
    suspend fun <T : Any> get(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    suspend fun <T : Any> post(
        uri: String,
        body: Any? = null,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    suspend fun <T : Any> put(
        uri: String,
        body: Any? = null,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    suspend fun <T : Any> patch(
        uri: String,
        body: Any? = null,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    suspend fun <T : Any> delete(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T
}

suspend inline fun <reified T : Any> AsyncHttpClient.get(
    uri: String,
    headers: Map<String, String> = emptyMap(),
): T = get(uri, object : ParameterizedTypeReference<T>() {}, headers)

suspend inline fun <reified T : Any> AsyncHttpClient.post(
    uri: String,
    body: Any? = null,
    headers: Map<String, String> = emptyMap(),
): T = post(uri, body, object : ParameterizedTypeReference<T>() {}, headers)

suspend inline fun <reified T : Any> AsyncHttpClient.put(
    uri: String,
    body: Any? = null,
    headers: Map<String, String> = emptyMap(),
): T = put(uri, body, object : ParameterizedTypeReference<T>() {}, headers)

suspend inline fun <reified T : Any> AsyncHttpClient.patch(
    uri: String,
    body: Any? = null,
    headers: Map<String, String> = emptyMap(),
): T = patch(uri, body, object : ParameterizedTypeReference<T>() {}, headers)

suspend inline fun <reified T : Any> AsyncHttpClient.delete(
    uri: String,
    headers: Map<String, String> = emptyMap(),
): T = delete(uri, object : ParameterizedTypeReference<T>() {}, headers)