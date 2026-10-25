package io.aegis.platform.webflux.exception

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.server.MethodNotAllowedException
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ReactiveGlobalExceptionHandlerTest {

    private val handler = ReactiveGlobalExceptionHandler()
    private val request = MockServerHttpRequest.get("/no-such-path").build()

    @Test
    @DisplayName("없는 경로(NoResourceFoundException)는 500이 아니라 404 NOT_FOUND다.")
    fun mapsNoResourceFoundToNotFound() {
        val ex = NoResourceFoundException(URI.create("/no-such-path"), "no-such-path")

        val response = handler.handleNoResourceFound(ex, request)
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(CommonErrorCode.NOT_FOUND.code, body.code)
        assertEquals("/no-such-path", body.path)
    }

    @Test
    @DisplayName("지원하지 않는 메서드(MethodNotAllowedException)는 405 METHOD_NOT_ALLOWED다.")
    fun mapsMethodNotAllowed() {
        val ex = MethodNotAllowedException(HttpMethod.DELETE, listOf(HttpMethod.GET))

        val response = handler.handleMethodNotAllowed(ex, request)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        assertEquals(CommonErrorCode.METHOD_NOT_ALLOWED.code, assertNotNull(response.body).code)
    }

    @Test
    @DisplayName("BusinessException의 detail은 응답 본문에 나가지 않는다.")
    fun doesNotExposeDetail() {
        val ex = BusinessException(CommonErrorCode.NOT_FOUND, detail = "blocked path=/internal")

        val body = assertNotNull(handler.handleBusinessException(ex, request).body)

        assertEquals(CommonErrorCode.NOT_FOUND.code, body.code)
        assertFalse(body.message.contains("/internal"))
    }

    @Test
    @DisplayName("분류되지 않은 예외는 500이고 원인 메시지를 노출하지 않는다.")
    fun mapsUnexpectedExceptionToInternalServerError() {
        val response = handler.handleUnexpectedException(IllegalStateException("db password=secret"), request)
        val body = assertNotNull(response.body)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertFalse(body.message.contains("secret"))
    }
}
