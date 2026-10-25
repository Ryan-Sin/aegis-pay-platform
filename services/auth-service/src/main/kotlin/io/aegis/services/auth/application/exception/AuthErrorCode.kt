package io.aegis.services.auth.application.exception

import io.aegis.platform.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_CHALLENGE(HttpStatus.UNAUTHORIZED, AuthErrorMessage.INVALID_CHALLENGE),
    TERMINAL_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, AuthErrorMessage.TERMINAL_AUTHENTICATION_FAILED),
    CHALLENGE_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, AuthErrorMessage.CHALLENGE_STORE_UNAVAILABLE),
    TOKEN_SIGNING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, AuthErrorMessage.TOKEN_SIGNING_UNAVAILABLE),
    ;

    override val code: String
        get() = name
}
