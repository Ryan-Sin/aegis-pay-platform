package io.aegis.platform.core.exception

import io.aegis.platform.core.context.TerminalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessExceptionTest {

    @Test
    @DisplayName("동적 인자(%s)가 포함된 ErrorCode로 BusinessException 생성 시 메시지가 포맷팅된다")
    fun shouldFormatMessageWithArguments() {
        val exception = BusinessException(
            CommonErrorCode.MISSING_REQUIRED_HEADER,
            TerminalContext.HEADER_TERMINAL_ID,
        )

        assertEquals(CommonErrorCode.MISSING_REQUIRED_HEADER, exception.errorCode)
        assertFalse(exception.message.contains("%s"))
        assertTrue(exception.message.contains(TerminalContext.HEADER_TERMINAL_ID))
    }

    @Test
    @DisplayName("내부 로깅용 detail은 외부 노출용 message에 섞이지 않는다")
    fun shouldSeparateDetailFromMessage() {
        val internalDetail = "JWT 서명 불일치 (kid=key-2026-10)"

        val exception = BusinessException(
            CommonErrorCode.BAD_REQUEST,
            detail = internalDetail,
        )

        assertEquals(internalDetail, exception.detail)
        assertFalse(exception.message.contains(internalDetail))
    }

}