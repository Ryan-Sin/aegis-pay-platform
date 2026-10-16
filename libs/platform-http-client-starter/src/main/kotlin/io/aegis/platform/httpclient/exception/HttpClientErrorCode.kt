package io.aegis.platform.httpclient.exception

import io.aegis.platform.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class HttpClientErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, HttpClientErrorMessage.EXTERNAL_API_TIMEOUT),
    EXTERNAL_API_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, HttpClientErrorMessage.EXTERNAL_API_CIRCUIT_OPEN),
    EXTERNAL_API_CLIENT_ERROR(HttpStatus.BAD_REQUEST, HttpClientErrorMessage.EXTERNAL_API_CLIENT_ERROR),
    EXTERNAL_API_SERVER_ERROR(HttpStatus.BAD_GATEWAY, HttpClientErrorMessage.EXTERNAL_API_SERVER_ERROR),
    EXTERNAL_API_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, HttpClientErrorMessage.EXTERNAL_API_EMPTY_RESPONSE),
    ;

    override val code: String
        get() = name
}