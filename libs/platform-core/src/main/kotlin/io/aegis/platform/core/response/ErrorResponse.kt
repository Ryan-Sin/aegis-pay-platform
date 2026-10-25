package io.aegis.platform.core.response

import io.aegis.platform.core.exception.ErrorCode
import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val code: String,
    val message: String,
    val path: String,
    val errors: List<FieldErrorDetail> = emptyList(),
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