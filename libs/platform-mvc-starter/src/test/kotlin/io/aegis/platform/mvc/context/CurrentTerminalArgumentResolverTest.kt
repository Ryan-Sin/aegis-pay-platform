package io.aegis.platform.mvc.context

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest

class CurrentTerminalArgumentResolverTest {

    private val resolverWithoutTracing = CurrentTerminalArgumentResolver(baggageManager = null)

    @Suppress("unused")
    private fun requiredEndpoint(@CurrentTerminal terminal: TerminalContext) = Unit

    @Suppress("unused")
    private fun optionalEndpoint(@CurrentTerminal(required = false) terminal: TerminalContext?) = Unit

    @Suppress("unused")
    private fun noAnnotationEndpoint(terminal: TerminalContext) = Unit

    @Suppress("unused")
    private fun wrongTypeEndpoint(@CurrentTerminal terminal: String) = Unit

    private fun parameterOf(methodName: String, type: Class<*>): MethodParameter =
        MethodParameter(this::class.java.getDeclaredMethod(methodName, type), 0)

    private val requiredParam = parameterOf("requiredEndpoint", TerminalContext::class.java)
    private val optionalParam = parameterOf("optionalEndpoint", TerminalContext::class.java)

    private fun webRequestWithLegacyHeaders() = ServletWebRequest(
        MockHttpServletRequest().apply {
            addHeader(TerminalContext.HEADER_TERMINAL_ID, "T-001")
            addHeader(TerminalContext.HEADER_STORE_ID, "S-100")
        },
    )

    @Test
    @DisplayName("@CurrentTerminal + TerminalContext 타입 조합에만 리졸버가 동작한다.")
    fun supportsOnlyAnnotatedTerminalContext() {
        assertTrue(resolverWithoutTracing.supportsParameter(requiredParam))
        assertTrue(resolverWithoutTracing.supportsParameter(optionalParam))
        assertFalse(resolverWithoutTracing.supportsParameter(parameterOf("noAnnotationEndpoint", TerminalContext::class.java)))
        assertFalse(resolverWithoutTracing.supportsParameter(parameterOf("wrongTypeEndpoint", String::class.java)))
    }

    @Test
    @DisplayName("tracing이 없으면 baggage를 읽을 수 없으므로, 예전 X-Terminal-Id 헤더가 있어도 401 MISSING_TERMINAL_CONTEXT다.")
    fun rejectsWhenTracingIsAbsentEvenWithLegacyHeaders() {
        val ex = assertThrows<BusinessException> {
            resolverWithoutTracing.resolveArgument(requiredParam, null, webRequestWithLegacyHeaders(), null)
        }

        assertEquals(CommonErrorCode.MISSING_TERMINAL_CONTEXT, ex.errorCode)
        assertTrue(ex.detail.orEmpty().contains("tracing=false"))
    }

    @Test
    @DisplayName("required=false면 단말 컨텍스트가 없을 때 null이다.")
    fun returnsNullForOptionalWhenAbsent() {
        assertNull(resolverWithoutTracing.resolveArgument(optionalParam, null, webRequestWithLegacyHeaders(), null))
    }
}
