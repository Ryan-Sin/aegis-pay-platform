package io.aegis.platform.webflux.filter

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

        log.debug(
            "Incoming Reactive Request: method={}, uri={}, authorization={}",
            request.method,
            request.uri.path,
            maskedAuth,
        )

        return chain.filter(exchange)
    }
}