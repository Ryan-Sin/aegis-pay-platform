package io.aegis.platform.webflux.filter

import io.aegis.platform.core.context.TerminalContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class ReactiveSensitiveHeaderMaskingFilter : WebFilter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val request = exchange.request
        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        val maskedAuth = if (authHeader.isNullOrBlank()) "NONE" else "[MASKED]"
        val terminalId = request.headers.getFirst(TerminalContext.HEADER_TERMINAL_ID) ?: "ANONYMOUS"

        log.debug(
            "Incoming Reactive Request: method={}, uri={}, terminalId={}, authorization={}",
            request.method,
            request.uri.path,
            terminalId,
            maskedAuth,
        )

        return chain.filter(exchange)
    }
}