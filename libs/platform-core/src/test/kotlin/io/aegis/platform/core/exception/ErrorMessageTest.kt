package io.aegis.platform.core.exception

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorMessageTest {

    @Test
    @DisplayName("포맷 지정자(%s)와 인자가 모두 없으면 원본 템플릿을 그대로 반환한다.")
    fun shouldReturnOriginalTemplateWhenNoPlaceholdersAndNoArgs() {
        val result = ErrorMessage.format(ErrorMessage.BAD_REQUEST)
        assertEquals(ErrorMessage.BAD_REQUEST, result)
    }

    @Test
    @DisplayName("동적 인자에 null이 전달되어도 NPE 없이 문자열 null로 안전하게 포맷팅된다.")
    fun shouldFormatSafelyWhenArgumentIsNull() {
        val result = ErrorMessage.format(ErrorMessage.MISSING_REQUIRED_HEADER, null)

        assertFalse(result.contains("%s"))
        assertTrue(result.contains("null"))
    }

    @Test
    @DisplayName("포맷 지정자(%s)가 있는 템플릿에 인자를 누락(0개)하면 풀백 문자열을 반환한다.")
    fun shouldFallbackWhenArgumentsAreOmitted(){
        val result = ErrorMessage.format(ErrorMessage.MISSING_REQUIRED_HEADER)

        assertTrue(result.contains(ErrorMessage.MISSING_REQUIRED_HEADER))
        assertTrue(result.contains("args="))
    }

    @Test
    @DisplayName("포맷 지정자(%s) 개수보다 적은 인자를 넘겨도 예외가 터지지 않고 원본 템플릿과 인자를 보존한다.")
    fun shouldFallbackGracefullyWhenFormatArgumentsAreInsufficient() {
        val twoPlaceholderTemplate = "%s -> %s"
        val arg = "mock-van"

        val result = ErrorMessage.format(twoPlaceholderTemplate, arg)

        assertTrue(result.contains(twoPlaceholderTemplate))
        assertTrue(result.contains(arg))
    }

    @Test
    @DisplayName("포맷 지정자(%s) 개수보다 많은 인자를 넘기면 초과 인자까지 버리지 않고 폴백에 보존한다")
    fun shouldFallbackAndPreserveExtraArgumentsWhenTooManyArgumentsProvided() {
        val onePlaceholderTemplate = "target=%s"

        val result = ErrorMessage.format(onePlaceholderTemplate, "mock-van", "/pay", 500)

        assertTrue(result.contains(onePlaceholderTemplate))
        assertTrue(result.contains("mock-van"))
        assertTrue(result.contains("/pay"))
        assertTrue(result.contains("500"))
    }
}