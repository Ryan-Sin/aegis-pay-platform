package io.aegis.platform.mvc.exception

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import io.aegis.platform.core.exception.ErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.test.*

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest().apply {
        requestURI = "/api/v1/payments"
    }

    @Suppress("unused")
    private fun dummyEndpoint(param: String) = Unit

    private val dummyParameter: MethodParameter by lazy {
        val method = this::class.java.getDeclaredMethod("dummyEndpoint", String::class.java)
        MethodParameter(method, 0)
    }
    @Test
    @DisplayName("없는 경로(NoResourceFoundException)는 500이 아니라 404 NOT_FOUND다")
    fun handlesNoResourceFoundAsNotFound() {
        val ex = NoResourceFoundException(HttpMethod.GET, "/no-such-path", "no-such-path")

        val response = handler.handleNotFound(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(CommonErrorCode.NOT_FOUND.code, assertNotNull(response.body).code)
    }

    @Test
    @DisplayName("형식이 깨진 요청 본문(JSON 파싱 실패)은 500이 아니라 400 BAD_REQUEST다")
    fun handlesUnreadableBodyAsBadRequest() {
        val ex = HttpMessageNotReadableException("JSON parse error", MockHttpInputMessage("{".toByteArray()))

        val response = handler.handleMessageNotReadable(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(CommonErrorCode.BAD_REQUEST.code, assertNotNull(response.body).code)
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED다")
    fun handlesMethodNotSupported() {
        val ex = HttpRequestMethodNotSupportedException("DELETE", listOf("GET"))

        val response = handler.handleMethodNotSupported(ex, request)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        assertEquals(CommonErrorCode.METHOD_NOT_ALLOWED.code, assertNotNull(response.body).code)
    }

    @Test
    @DisplayName("필수 헤더 누락 예외 처리 시 400 상태코드와 누락된 헤더명을 errors 목록에 담아 반환한다")
    fun handlesMissingRequestHeaderException() {
        val ex = MissingRequestHeaderException(TerminalContext.HEADER_TERMINAL_ID, dummyParameter)

        val response = handler.handleMissingHeader(ex, request)
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(CommonErrorCode.MISSING_REQUIRED_HEADER.code, body.code)
        assertEquals("/api/v1/payments", body.path)
        assertTrue(body.message.contains(TerminalContext.HEADER_TERMINAL_ID))
        assertEquals(1, body.errors.size)
        assertEquals(TerminalContext.HEADER_TERMINAL_ID, body.errors.first().field)
    }

    @Test
    @DisplayName("요청 본문 검증 실패 시 필드 에러 목록을 변환하며 defaultMessage가 null이면 기본 에러 메시지로 대체한다")
    fun handlesValidationExceptionWithAndWithoutDefaultMessage() {
        val bindingResult = BeanPropertyBindingResult(Any(), "paymentRequest").apply {
            addError(FieldError("paymentRequest", "amount", -100, false, null, null, "0보다 커야 합니다"))
            addError(FieldError("paymentRequest", "orderId", null, false, null, null, null))
        }
        val ex = MethodArgumentNotValidException(dummyParameter, bindingResult)

        val response = handler.handleValidationException(ex, request)
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(CommonErrorCode.INVALID_REQUEST_BODY.code, body.code)
        assertEquals(2, body.errors.size)

        val amountError = body.errors[0]
        assertEquals("amount", amountError.field)
        assertEquals("-100", amountError.value)
        assertEquals("0보다 커야 합니다", amountError.reason)

        val orderIdError = body.errors[1]
        assertEquals("orderId", orderIdError.field)
        assertNull(orderIdError.value)
        assertEquals(CommonErrorCode.INVALID_REQUEST_BODY.message, orderIdError.reason)
    }

    @Test
    @DisplayName("서비스 로직에서 BusinessException 발생 시 해당 ErrorCode의 상태코드(예: 409)와 포맷팅된 도메인 메시지를 반환한다")
        fun handlesBusinessException() {
            val domainErrorCode = object : ErrorCode {
                override val code: String = "DUPLICATE_PAYMENT"
                override val status: HttpStatus = HttpStatus.CONFLICT
                override val message: String = "이미 처리된 결제 요청입니다. (orderId=%s)"
            }
            val ex = BusinessException(domainErrorCode, "ORDER-1001")

            val response = handler.handleBusinessException(ex, request)
            val body = assertNotNull(response.body)

            assertEquals(HttpStatus.CONFLICT, response.statusCode)
            assertEquals("DUPLICATE_PAYMENT", body.code)
            assertTrue(body.message.contains("ORDER-1001"))
        }

        @Test
        @DisplayName("BusinessException의 내부 로깅용 detail은 응답 body에 노출되지 않는다")
        fun doesNotLeakBusinessExceptionDetail() {
            val internalDetail = "lock-key=payment:ORDER-1001 acquired by node-2"
            val ex = BusinessException(CommonErrorCode.BAD_REQUEST, detail = internalDetail)

            val response = handler.handleBusinessException(ex, request)
            val body = assertNotNull(response.body)

            assertFalse(body.message.contains(internalDetail))
            assertTrue(body.errors.none { it.reason.contains(internalDetail) })
        }

        @Test
        @DisplayName("예상치 못한 서버 예외 발생 시 500을 반환하며 내부 예외 상세 메시지는 외부로 노출하지 않는다")
        fun handlesUnexpectedExceptionWithoutLeakingInternalDetails() {
            val internalSecretMessage = "HikariPool-1 - Connection refused to 10.0.1.55:5432"
            val ex = IllegalStateException(internalSecretMessage)

            val response = handler.handleUnexpectedException(ex, request)
            val body = assertNotNull(response.body)

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR.code, body.code)
            assertFalse(body.message.contains(internalSecretMessage))
        }
}