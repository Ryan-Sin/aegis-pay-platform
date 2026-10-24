package io.aegis.platform.httpclient.config

/**
 * 호출 대상의 신뢰 경계(ARCHITECTURE 5장).
 */
enum class HttpTarget {
    /** 플랫폼 내부 서비스. `X-Request-Id`, `X-Terminal-Id`, `X-Store-Id`, baggage를 전파한다. */
    INTERNAL,

    /** VAN 같은 외부 대상. 내부 식별자와 baggage를 보내지 않는다. trace context(`traceparent`)는 유지한다. */
    EXTERNAL,
}
