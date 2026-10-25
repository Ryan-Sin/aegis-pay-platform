package io.aegis.platform.mvc.filter

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

        log.debug(
            "Incoming Request: method={}, uri={}, authorization={}",
            request.method,
            request.requestURI,
            maskedAuth,
        )

        filterChain.doFilter(request, response)
    }
}