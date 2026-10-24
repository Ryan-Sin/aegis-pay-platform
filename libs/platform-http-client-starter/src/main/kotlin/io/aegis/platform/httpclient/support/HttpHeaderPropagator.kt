package io.aegis.platform.httpclient.support

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.httpclient.config.HttpTarget
import io.aegis.platform.observability.TraceContextConstants
import org.slf4j.MDC
import org.springframework.http.HttpHeaders

internal object HttpHeaderPropagator {

    const val HEADER_BAGGAGE = "baggage"

    fun applyHeaders(
        targetHeaders: HttpHeaders,
        customHeaders: Map<String, String>,
        target: HttpTarget,
    ) {
        if (target == HttpTarget.INTERNAL) {
            propagateMdcHeader(targetHeaders, TraceContextConstants.MDC_REQUEST_ID, TraceContextConstants.HEADER_REQUEST_ID)
            propagateMdcHeader(targetHeaders, TraceContextConstants.MDC_TERMINAL_ID, TerminalContext.HEADER_TERMINAL_ID)
            propagateMdcHeader(targetHeaders, TraceContextConstants.MDC_STORE_ID, TerminalContext.HEADER_STORE_ID)
        }

        customHeaders.forEach(targetHeaders::set)
    }

    /**
     * 외부 대상 요청에서 tracing instrumentation이 주입한 baggage를 지운다.
     * 주입은 요청 헤더를 설정한 뒤(observation 시작 시점)에 일어나므로 filter/interceptor 단계에서 호출한다.
     */
    fun stripBaggage(targetHeaders: HttpHeaders) {
        targetHeaders.remove(HEADER_BAGGAGE)
    }

    private fun propagateMdcHeader(
        targetHeaders: HttpHeaders,
        mdKey: String,
        headerName: String,
    ) {
        MDC.get(mdKey)?.takeIf { it.isNotBlank() }?.let {
            targetHeaders.putIfAbsent(headerName, listOf(it))
        }
    }
}