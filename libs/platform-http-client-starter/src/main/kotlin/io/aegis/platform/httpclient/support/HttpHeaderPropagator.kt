package io.aegis.platform.httpclient.support

import io.aegis.platform.core.context.TerminalContext
import io.aegis.platform.observability.TraceContextConstants
import org.slf4j.MDC
import org.springframework.http.HttpHeaders

internal object HttpHeaderPropagator {

    fun applyHeaders(
        targetHeaders: HttpHeaders,
        customHeaders: Map<String, String>
    ) {
        propagateMdcHeader(targetHeaders, TraceContextConstants.MDC_REQUEST_ID, TraceContextConstants.HEADER_REQUEST_ID)
        propagateMdcHeader(targetHeaders, TraceContextConstants.MDC_TERMINAL_ID, TerminalContext.HEADER_TERMINAL_ID)
        propagateMdcHeader(targetHeaders, TraceContextConstants.MDC_STORE_ID, TerminalContext.HEADER_STORE_ID)

        customHeaders.forEach(targetHeaders::set)
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