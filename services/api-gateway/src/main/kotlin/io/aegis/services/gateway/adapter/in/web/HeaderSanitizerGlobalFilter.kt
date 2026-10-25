package io.aegis.services.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class HeaderSanitizerGlobalFilter : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val sanitizedRequest = exchange.request.mutate()
            .headers { httpHeaders ->
                val headersToRemove = httpHeaders.headerNames()
                    .filter { headerName -> isInternalTrustHeader(headerName) }
                    .toList()

                if (headersToRemove.isNotEmpty()) {
                    log.debug(
                        "Stripping spoofed internal headers from external request: path={}, headers={}",
                        exchange.request.path.pathWithinApplication().value(),
                        headersToRemove,
                    )
                    headersToRemove.forEach(httpHeaders::remove)
                }
            }
            .build()

        return chain.filter(exchange.mutate().request(sanitizedRequest).build())
    }

    private fun isInternalTrustHeader(headerName: String): Boolean {
        return SPOOFED_HEADER_PREFIXES.any { prefix ->
            headerName.startsWith(prefix, ignoreCase = true)
        } || SPOOFED_EXACT_HEADERS.any { exact ->
            headerName.equals(exact, ignoreCase = true)
        }
    }

    companion object {
        const val FILTER_ORDER = -200

        private val SPOOFED_HEADER_PREFIXES = listOf(
            "X-Terminal-",
            "X-Store-",
        )

        private val SPOOFED_EXACT_HEADERS = listOf(
            "baggage"
        )
    }
}
