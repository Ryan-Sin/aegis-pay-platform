package io.aegis.platform.web.exception

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val path: String,
    val errors: List<FieldErrorDetail> = emptyList(),
    val timestamp: Instant = Instant.now(),
) {
    data class FieldErrorDetail(
        val field: String,
        val value: String?,
        val reason: String
    )

    companion object {
        fun of(
            errorCode: ErrorCode,
            path: String,
            message: String = errorCode.message,
            errors: List<FieldErrorDetail> = emptyList(),
        ): ErrorResponse {
            return ErrorResponse(
                code = errorCode.code,
                message = message,
                path = path,
                errors = errors,
            )
        }
    }
}