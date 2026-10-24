package io.aegis.platform.httpclient.config

import io.aegis.platform.core.exception.ErrorMessage
import io.aegis.platform.httpclient.exception.HttpClientErrorMessage
import java.time.Duration

/**
 * @property target 내부 식별자(Request ID, 단말 컨텍스트, baggage)를 전파할지 정한다.
 *   기본값은 [HttpTarget.EXTERNAL]이다. 지정을 빠뜨렸을 때 VAN 같은 외부 대상으로 내부 정보가 새지 않게 한다.
 */
data class HttpClientSpec(
    val targetName: String,
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(3),
    val circuitBreakerEnabled: Boolean = true,
    val retryEnabled: Boolean = false,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val target: HttpTarget = HttpTarget.EXTERNAL,
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
