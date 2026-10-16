package io.aegis.platform.observability

object TraceContextConstants {
    const val HEADER_REQUEST_ID = "X-Request-Id"
    const val HEADER_TRACE_ID = "X-Trace-Id"

    const val MDC_REQUEST_ID = "requestId"
    const val MDC_TRACE_ID = "traceId"
    const val MDC_SPAN_ID = "spanId"
    const val MDC_TERMINAL_ID = "terminalId"
    const val MDC_STORE_ID = "storeId"
}