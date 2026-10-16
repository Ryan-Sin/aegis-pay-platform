package io.aegis.platform.httpclient

import io.aegis.platform.httpclient.config.HttpClientSpec
import org.springframework.core.ParameterizedTypeReference

fun interface SyncHttpClientFactory {
    fun create(spec: HttpClientSpec): SyncHttpClient
}

interface SyncHttpClient {
    fun <T: Any> get(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    fun <T: Any> post(
        uri: String,
        body: Any? = null,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    fun <T: Any> put(
        uri: String,
        body: Any? = null,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    fun <T: Any> patch(
        uri: String,
        body: Any? = null,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T

    fun <T: Any> delete(
        uri: String,
        responseType: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): T
}

inline fun <reified T: Any> SyncHttpClient.get(
    uri: String,
    headers: Map<String, String> = emptyMap()
): T = get(uri, object : ParameterizedTypeReference<T>() {}, headers)

inline fun <reified T: Any> SyncHttpClient.post(
    uri: String,
    body: Any? = null,
    headers: Map<String, String> = emptyMap()
): T = post(uri, body, object : ParameterizedTypeReference<T>() {}, headers)

inline fun <reified T: Any> SyncHttpClient.put(
    uri: String,
    body: Any? = null,
    headers: Map<String, String> = emptyMap()
): T = put(uri, body, object : ParameterizedTypeReference<T>() {}, headers)


inline fun <reified T: Any> SyncHttpClient.patch(
    uri: String,
    body: Any? = null,
    headers: Map<String, String> = emptyMap()
): T = patch(uri, body, object : ParameterizedTypeReference<T>() {}, headers)

inline fun <reified T : Any> SyncHttpClient.delete(
    uri: String,
    headers: Map<String, String> = emptyMap(),
): T = delete(uri, object : ParameterizedTypeReference<T>() {}, headers)