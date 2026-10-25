package io.aegis.platform.mvc.exception

import jakarta.servlet.http.HttpServletRequest
import io.aegis.platform.core.exception.*
import io.aegis.platform.core.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR
        log.error("[{}] uri={}, message={}", errorCode.code, request.requestURI, ex.message, ex)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = request.requestURI,
                ),
            )
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = clientError(CommonErrorCode.NOT_FOUND, request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = clientError(CommonErrorCode.METHOD_NOT_ALLOWED, request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = clientError(CommonErrorCode.BAD_REQUEST, request)

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

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = ex.errorCode
        val message = ex.message ?: errorCode.message
        log.warn("[{}] uri={}, message={}, detail={}", errorCode.code, request.requestURI, message, ex.detail)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = request.requestURI,
                    message = message,
                ),
            )
    }

    private fun clientError(
        errorCode: ErrorCode,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("[{}] uri={}, method={}", errorCode.code, request.requestURI, request.method)
        return ResponseEntity
            .status(errorCode.status)
            .body(
                ErrorResponse.of(
                    errorCode = errorCode,
                    path = request.requestURI,
                ),
            )
    }
}
