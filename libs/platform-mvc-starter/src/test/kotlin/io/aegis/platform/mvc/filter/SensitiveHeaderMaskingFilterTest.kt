package io.aegis.platform.mvc.filter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class SensitiveHeaderMaskingFilterTest {

    private val filter = SensitiveHeaderMaskingFilter()

    @BeforeEach
    fun setUp() {
        val logger = LoggerFactory.getLogger(SensitiveHeaderMaskingFilter::class.java) as Logger
        logger.level = Level.DEBUG
    }

    @Test
    @DisplayName("Authorization 헤더가 있으면 로그에는 [MASKED]로 기록하고 하위 체인에는 원본 헤더를 그대로 전달한다")
    fun masksAuthorizationInLogWhilePreservingOriginalRequestHeader(output: CapturedOutput) {
        val rawToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.secret"
        val request = MockHttpServletRequest("POST", "/api/v1/payments").apply {
            addHeader(HttpHeaders.AUTHORIZATION, rawToken)
        }
        val response = MockHttpServletResponse()

        var downstreamAuthHeader: String? = null
        val chain = FilterChain { req, _ ->
            downstreamAuthHeader = (req as HttpServletRequest).getHeader(HttpHeaders.AUTHORIZATION)
        }

        filter.doFilter(request, response, chain)

        assertEquals(rawToken, downstreamAuthHeader)
        assertFalse(output.out.contains(rawToken))
        assertTrue(output.out.contains("authorization=[MASKED]"))
    }

    @Test
    @DisplayName("Authorization이 공백이면 로그에 NONE으로 기록한다")
    fun logsNoneAndAnonymousWhenHeadersAreBlankOrAbsent(output: CapturedOutput) {
        val request = MockHttpServletRequest("GET", "/api/v1/health").apply {
            addHeader(HttpHeaders.AUTHORIZATION, "   ")
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false

        filter.doFilter(request, response) { _, _ ->
            chainInvoked = true
        }

        assertTrue(chainInvoked)
        assertTrue(output.out.contains("authorization=NONE"))
    }
}