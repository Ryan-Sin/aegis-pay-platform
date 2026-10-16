package io.aegis.platform.httpclient.config

import io.aegis.platform.core.exception.ErrorMessage
import io.aegis.platform.httpclient.exception.HttpClientErrorMessage
import java.time.Duration

data class HttpClientSpec(
    val targetName: String,
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(3),
    val circuitBreakerEnabled: Boolean = true,
    val retryEnabled: Boolean = false,
    val defaultHeaders: Map<String, String> = emptyMap(),
) {
    init {
        require(targetName.isNotBlank()) {
            ErrorMessage.format(HttpClientErrorMessage.INVALID_BLANK_PROPERTY, "targetName")
        }
        require(baseUrl.isNotBlank()) {
            ErrorMessage.format(HttpClientErrorMessage.INVALID_BLANK_PROPERTY, "baseUrl")
        }
        require(!connectTimeout.isNegative && !connectTimeout.isZero) {
            ErrorMessage.format(HttpClientErrorMessage.INVALID_TIMEOUT_PROPERTY, "connectTimeout")
        }
        require(!readTimeout.isNegative && !readTimeout.isZero) {
            ErrorMessage.format(HttpClientErrorMessage.INVALID_TIMEOUT_PROPERTY, "readTimeout")
        }
    }
}
