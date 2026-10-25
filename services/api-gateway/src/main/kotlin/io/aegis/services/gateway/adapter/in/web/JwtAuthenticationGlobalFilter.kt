package io.aegis.services.gateway.adapter.`in`.web

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.application.exception.GatewayErrorCode
import io.aegis.services.gateway.application.exception.GatewayErrorMessage
import io.aegis.services.gateway.application.port.`in`.AuthenticateTokenUseCase
import io.aegis.services.gateway.config.GatewayAuthProperties
import kotlinx.coroutines.reactor.mono
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.PropagationContextElement
import org.springframework.http.HttpHeaders
import org.springframework.http.server.PathContainer
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationGlobalFilter(
    properties: GatewayAuthProperties,
    private val authenticateTokenUseCase: AuthenticateTokenUseCase,
) : GlobalFilter, Ordered {

    private val permitAllPatterns = properties.permitAllPaths.map { pattern ->
        PathPatternParser.defaultInstance.parse(pattern)
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val requestPath = exchange.request.path.pathWithinApplication()
        if (isPermitAllPath(requestPath)) {
            return chain.filter(exchange)
        }

        // 인증 실패 로그는 ReactiveGlobalExceptionHandler가 code/uri/detail로 단일 기록한다.
        // mono {}는 Dispatchers.Default에서 실행되므로 PropagationContextElement로 MDC·observation을 복원한다.
        return mono(PropagationContextElement()) {
            val token = extractBearerToken(exchange)
                ?: throw BusinessException(
                    GatewayErrorCode.MISSING_AUTH_TOKEN,
                    detail = GatewayErrorMessage.REASON_MISSING_BEARER_TOKEN,
                )
            val claims = authenticateTokenUseCase.authenticate(token)
            injectTerminalHeaders(exchange, claims.terminalContext)
        }.flatMap { authenticatedExchange ->
            chain.filter(authenticatedExchange)
        }
    }

    private fun isPermitAllPath(pathContainer: PathContainer): Boolean {
        return permitAllPatterns.any { pattern -> pattern.matches(pathContainer) }
    }

    private fun extractBearerToken(exchange: ServerWebExchange): String? {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return null
        if (!authHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            return null
        }
        return authHeader.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() }
    }

    private fun injectTerminalHeaders(
        exchange: ServerWebExchange,
        terminalContext: TerminalContext,
    ): ServerWebExchange {
        val mutatedRequest = exchange.request.mutate()
            .headers { httpHeaders ->
                httpHeaders.set(TerminalContext.HEADER_TERMINAL_ID, terminalContext.terminalId)
                httpHeaders.set(TerminalContext.HEADER_STORE_ID, terminalContext.storeId)
                if (terminalContext.scopes.isNotEmpty()) {
                    httpHeaders.set(
                        TerminalContext.HEADER_TERMINAL_SCOPES,
                        terminalContext.scopes.joinToString(","),
                    )
                }
            }
            .build()

        return exchange.mutate().request(mutatedRequest).build()
    }

    companion object {
        const val FILTER_ORDER = -100
        private const val BEARER_PREFIX = "Bearer "
    }
}
