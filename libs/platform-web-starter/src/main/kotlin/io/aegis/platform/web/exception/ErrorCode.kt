package io.aegis.platform.web.exception

import org.springframework.http.HttpStatus

interface ErrorCode {
    val code: String
    val status: HttpStatus
    val message: String
}

enum class CommonErrorCode(
    override val status: HttpStatus,
    override val message: String,
): ErrorCode {
    MISSING_REQUIRED_HEADER(HttpStatus.BAD_REQUEST, ErrorMessage.MISSING_REQUIRED_HEADER),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, ErrorMessage.INVALID_REQUEST_BODY),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, ErrorMessage.BAD_REQUEST),
    SERVLET_REQUEST_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessage.SERVLET_REQUEST_NOT_FOUND),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessage.INTERNAL_SERVER_ERROR);

    override val code: String
        get() = name
}