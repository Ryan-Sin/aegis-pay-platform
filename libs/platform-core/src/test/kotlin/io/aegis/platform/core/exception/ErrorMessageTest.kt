package io.aegis.platform.core.exception

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorMessageTest {

    @Test
    @DisplayName("동적 인자에 null이 전달되어도 NPE 없이 문자열로 안전하게 포맷팅된다.")
    fun shouldFormatSafelyWhenArgumentIsNull() {
        val exception = BusinessException(
            CommonErrorCode.MISSING_REQUIRED_HEADER,
            null,
        )

        assertFalse(exception.message.contains("%s"))
        assertTrue(exception.message.contains("null"))
    }

    @Test
    @DisplayName("포맷 지정자(%s) 개수보다 적은 인자를 넘겨도 예외가 터지지 않고 원본 템플릿과 인자를 보존한다.")
    fun shouldFallbackGracefullWhenFormatArgumentsAreInsufficient() {
        val towPlaceholderTemplate = "%s -> %s"
        val arg = "mock-van"

        val result = ErrorMessage.format(towPlaceholderTemplate, arg)

        assertTrue(result.contains(towPlaceholderTemplate))
        assertTrue(result.contains(arg))
    }
}