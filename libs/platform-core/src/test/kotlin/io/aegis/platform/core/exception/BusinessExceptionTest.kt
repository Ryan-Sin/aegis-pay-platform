package io.aegis.platform.core.exception

import io.aegis.platform.core.context.TerminalContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessExceptionTest {

    @Test
    @DisplayName("동적 인자(%s)가 포함도니 ErrorCode로 BusinessException 생성 시 메시지가 포맷팅된다")
    fun shouldFormatMessageWithArguments() {
        val exception = BusinessException(
            CommonErrorCode.MISSING_REQUIRED_HEADER,
            TerminalContext.HEADER_TERMINAL_ID
        )

        assertFalse(exception.message.contains("%s"))
        assertTrue(exception.message.contains(TerminalContext.HEADER_TERMINAL_ID))
    }

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