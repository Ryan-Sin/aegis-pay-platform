package io.aegis.platform.mvc.context

import io.aegis.platform.core.context.TerminalContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.context.request.ServletWebRequest

class CurrentTerminalArgumentResolverTest {

    private val resolver = CurrentTerminalArgumentResolver()

    @Suppress("unused")
    private fun requiredEndpoint(@CurrentTerminal terminal: TerminalContext) = Unit

    @Suppress("unused")
    private fun optionalEndpoint(@CurrentTerminal(required = false) terminal: TerminalContext?) = Unit

    @Suppress("unused")
    private fun noAnnotationEndpoint(terminal: TerminalContext) = Unit

    @Suppress("unused")
    private fun wrongTypeEndpoint(@CurrentTerminal terminal: String) = Unit

    private fun parameterOf(methodName: String, type: Class<*>): MethodParameter {
        val method = this::class.java.getDeclaredMethod(methodName, type)
        return MethodParameter(method, 0)
    }

    private val requiredParam = parameterOf("requiredEndpoint", TerminalContext::class.java)
    private val optionalParam = parameterOf("optionalEndpoint", TerminalContext::class.java)

    private fun webRequestWith(vararg headers: Pair<String, String>): ServletWebRequest {
        val request = MockHttpServletRequest().apply {
            headers.forEach { (name, value) -> addHeader(name, value) }
        }
        return ServletWebRequest(request)
    }

    private fun resolve(param: MethodParameter, webRequest: ServletWebRequest): Any? =
        resolver.resolveArgument(param, null, webRequest, null)

    @Test
    @DisplayName("@CurrentTerminal + TerminalContext 타입 조합에만 리졸버가 동작한다.")
    fun supportsOnlyAnnotatedTerminalContext() {
        assertTrue(resolver.supportsParameter(requiredParam))
        assertTrue(resolver.supportsParameter(optionalParam))
        assertFalse(resolver.supportsParameter(parameterOf("noAnnotationEndpoint", TerminalContext::class.java)))
        assertFalse(resolver.supportsParameter(parameterOf("wrongTypeEndpoint", String::class.java)))
    }

    @Test
    @DisplayName("신뢰 헤더가 모두 있으면 TerminalContext를 생성하고 scopes의 공백/빈 토큰을 정리한다.")
    fun resolvesTerminalContextAndNormalizesScopes() {
        val webRequest = webRequestWith(
            TerminalContext.HEADER_TERMINAL_ID to "T-001",
            TerminalContext.HEADER_STORE_ID to "S-100",
            TerminalContext.HEADER_TERMINAL_SCOPES to " read , ,pay"
        )

        val context = resolve(requiredParam, webRequest) as TerminalContext

        assertEquals("T-001", context.terminalId)
        assertEquals("S-100", context.storeId)
        assertEquals(setOf("read", "pay"), context.scopes)
    }

    @Test
    @DisplayName("scopes 헤더가 없으면 예외 없이 빈 Set으로 생성된다")
    fun resolvesEmptyScopesWhenHeaderAbsent() {
        val webRequest = webRequestWith(
            TerminalContext.HEADER_TERMINAL_ID to "T-001",
            TerminalContext.HEADER_STORE_ID to "S-100",
        )

        val context = resolve(requiredParam, webRequest) as TerminalContext

        assertTrue(context.scopes.isEmpty())
    }

    @Test
    @DisplayName("Terminal-Id가 공백 문자열이면 누락으로 간주하고 해당 헤더명을 담아 예외를 던진다")
    fun throwsWhenTerminalIdIsBlank() {
        val webRequest = webRequestWith(
            TerminalContext.HEADER_TERMINAL_ID to "   ",
            TerminalContext.HEADER_STORE_ID to "S-100",
        )

        val ex = assertThrows<MissingRequestHeaderException> {
            resolve(requiredParam, webRequest)
        }

        assertEquals(TerminalContext.HEADER_TERMINAL_ID, ex.headerName)
    }

    @Test
    @DisplayName("Store-Id만 누락되면 Store-Id 헤더명을 정확히 지목한다")
    fun throwsWithStoreHeaderNameWhenOnlyStoreIdMissing() {
        val webRequest = webRequestWith(
            TerminalContext.HEADER_TERMINAL_ID to "T-001",
        )

        val ex = assertThrows<MissingRequestHeaderException> {
            resolve(requiredParam, webRequest)
        }

        assertEquals(TerminalContext.HEADER_STORE_ID, ex.headerName)
    }

    @Test
    @DisplayName("required=false이면 헤더가 없어도 예외 없이 null을 반환한다")
    fun returnsNullWhenNotRequiredAndHeadersMissing() {
        val result = resolve(optionalParam, webRequestWith())

        assertNull(result)
    }

    @Test
    @DisplayName("required=false여도 필수 헤더 중 하나만 전달되면 불완전 요청으로 간주하여 예외를 던진다.")
    fun throwsWhenNotRequiredButOnlyHeaderPresent() {
        val webRequest = webRequestWith(
            TerminalContext.HEADER_TERMINAL_ID to "T-001",
        )

        val ex = assertThrows<MissingRequestHeaderException>{
            resolve(optionalParam, webRequest)
        }

        assertEquals(TerminalContext.HEADER_STORE_ID, ex.headerName)
    }
}