package io.aegis.platform.web.filter

import io.aegis.platform.web.context.TerminalContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

class SensitiveHeaderMaskingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        val maskedAuth = if (authHeader.isNullOrBlank()) "NONE" else "[MASKED]"
        val terminalId = request.getHeader(TerminalContext.HEADER_TERMINAL_ID) ?: "ANONYMOUS"

        log.debug(
            "Incoming Request: method={}, uri={}, terminalId={}, authorization={}",
            request.method,
            request.requestURI,
            terminalId,
            maskedAuth,
        )

        filterChain.doFilter(request, response)
    }
}