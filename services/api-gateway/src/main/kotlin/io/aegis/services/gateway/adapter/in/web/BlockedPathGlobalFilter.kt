package io.aegis.services.gateway.adapter.`in`.web

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.core.exception.CommonErrorCode
import io.aegis.services.gateway.application.exception.GatewayErrorMessage
import io.aegis.services.gateway.config.GatewayAuthProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono

@Component
class BlockedPathGlobalFilter(
    properties: GatewayAuthProperties,
) : GlobalFilter, Ordered {

    private val blockedPatterns = properties.blockedPaths.map { pattern ->
        PathPatternParser.defaultInstance.parse(pattern)
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val requestPath = exchange.request.path.pathWithinApplication()
        if (blockedPatterns.any { it.matches(requestPath) }) {
            return Mono.error(
                BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    detail = "${GatewayErrorMessage.REASON_BLOCKED_PATH} (path=${requestPath.value()})",
                ),
            )
        }
        return chain.filter(exchange)
    }

    companion object {
        const val FILTER_ORDER = -300
    }
}
