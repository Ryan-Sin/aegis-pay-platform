
package io.aegis.platform.webflux.exception

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import io.aegis.platform.core.exception.ErrorCode
import io.aegis.platform.core.exception.ErrorMessage
import io.aegis.platform.core.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.MissingRequestValueException

@RestControllerAdvice
class ReactiveGlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        ex: Exception,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR
        val path = request.uri.path
        log.error("[{}] uri={}, message={}", errorCode.code, path, ex.message, ex)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = path,
                ),
            )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> = clientError(CommonErrorCode.NOT_FOUND, request)

    @ExceptionHandler(MethodNotAllowedException::class)
    fun handleMethodNotAllowed(
        ex: MethodNotAllowedException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> = clientError(CommonErrorCode.METHOD_NOT_ALLOWED, request)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = ex.errorCode
        val path = request.uri.path
        val message = ex.message ?: errorCode.message
        log.warn("[{}] uri={}, message={}, detail={}", errorCode.code, path, message, ex.detail)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = path,
                    message = message,
                ),
            )
    }

    @ExceptionHandler(MissingRequestValueException::class)
    fun handleMissingRequestValue(
        ex: MissingRequestValueException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.MISSING_REQUIRED_HEADER
        val path = request.uri.path
        val formattedMessage = ErrorMessage.format(errorCode.message, ex.name)
        log.warn("[{}] uri={}, message={}", errorCode.code, path, formattedMessage)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = path,
                    message = formattedMessage,
                    errors = listOf(
                        ErrorResponse.FieldErrorDetail(
                            field = ex.name,
                            value = null,
                            reason = formattedMessage,
                        ),
                    ),
                ),
            )
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationException(
        ex: WebExchangeBindException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.INVALID_REQUEST_BODY
        val path = request.uri.path
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            ErrorResponse.FieldErrorDetail(
                field = error.field,
                value = error.rejectedValue?.toString(),
                reason = error.defaultMessage ?: errorCode.message,
            )
        }
        log.warn("[{}] uri={}, errors={}", errorCode.code, path, fieldErrors)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = path,
                    errors = fieldErrors,
                ),
            )
    }

    private fun clientError(
        errorCode: ErrorCode,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        val path = request.uri.path
        log.warn("[{}] uri={}, method={}", errorCode.code, path, request.method)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = path,
                ),
            )
    }
}
