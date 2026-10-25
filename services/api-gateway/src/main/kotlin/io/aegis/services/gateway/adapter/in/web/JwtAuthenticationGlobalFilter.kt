package io.aegis.services.gateway.adapter.`in`.web

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.core.exception.BusinessException
import io.aegis.services.gateway.application.exception.GatewayErrorCode
import io.aegis.services.gateway.application.exception.GatewayErrorMessage
import io.aegis.services.gateway.application.port.`in`.AuthenticateTokenUseCase
import io.aegis.services.gateway.config.GatewayAuthProperties
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
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
    private val tracer: Tracer,
) : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(javaClass)

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
            injectTerminalContext(exchange, claims.terminalContext)
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

    private fun injectTerminalContext(
        exchange: ServerWebExchange,
        terminalContext: TerminalContext,
    ): ServerWebExchange {
        attachTerminalBaggage(terminalContext)

        val mutatedRequest = exchange.request.mutate()
            .headers { httpHeaders ->
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

    private fun attachTerminalBaggage(terminalContext: TerminalContext) {
        val traceContext = tracer.currentTraceContext().context()
        if (traceContext == null) {
            log.error("No trace context to carry terminal baggage: terminalId={}", terminalContext.terminalId)
            return
        }
        tracer.createBaggage(TerminalContext.BAGGAGE_TERMINAL_ID).set(traceContext, terminalContext.terminalId)
        tracer.createBaggage(TerminalContext.BAGGAGE_STORE_ID).set(traceContext, terminalContext.storeId)
    }

    companion object {
        const val FILTER_ORDER = -100
        private const val BEARER_PREFIX = "Bearer "
    }
}
