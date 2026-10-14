package io.aegis.platform.web.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        ex: MissingRequestHeaderException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.MISSING_REQUIRED_HEADER
        val formattedMessage = ErrorMessage.format(errorCode.message, ex.headerName)
        log.warn("[{}] uri={}, message={}", errorCode.message, request.requestURI, formattedMessage)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = request.requestURI,
                    message = formattedMessage,
                    errors = listOf(
                        ErrorResponse.FieldErrorDetail(
                            field = ex.headerName,
                            value = null,
                            reason = formattedMessage
                        )
                    )
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.INVALID_REQUEST_BODY
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            ErrorResponse.FieldErrorDetail(
                field = error.field,
                value = error.rejectedValue?.toString(),
                reason = error.defaultMessage ?: errorCode.message,
            )
        }
        log.warn("[{}] uri={}, errors={}", errorCode.code, request.requestURI, fieldErrors)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = request.requestURI,
                    errors = fieldErrors,
                ),
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.BAD_REQUEST
        log.warn("[{}] uri={}, message={}", errorCode.code, request.requestURI, ex.message)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = request.requestURI,
                    message = ex.message ?: errorCode.message,
                ),
            )
    }
}