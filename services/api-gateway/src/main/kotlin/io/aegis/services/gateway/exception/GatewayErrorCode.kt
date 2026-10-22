package io.aegis.services.gateway.exception

import io.aegis.platform.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class GatewayErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    MISSING_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, GatewayErrorMessage.MISSING_AUTH_TOKEN),
    INVALID_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, GatewayErrorMessage.INVALID_AUTH_TOKEN),
    EXPIRED_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, GatewayErrorMessage.EXPIRED_AUTH_TOKEN),
    REVOKED_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, GatewayErrorMessage.REVOKED_AUTH_TOKEN),
    JWKS_FETCH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorMessage.JWKS_FETCH_FAILED),
    TOKEN_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorMessage.TOKEN_STORE_UNAVAILABLE),
    ;

    override val code: String
        get() = name
}
