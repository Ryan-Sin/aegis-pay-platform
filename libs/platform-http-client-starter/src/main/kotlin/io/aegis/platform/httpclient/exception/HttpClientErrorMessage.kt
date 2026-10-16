package io.aegis.platform.httpclient.exception

object HttpClientErrorMessage {
    const val EXTERNAL_API_TIMEOUT = "외부 시스템 응답 시간이 초과되었습니다. (target=%s, uri=%s)"
    const val EXTERNAL_API_CIRCUIT_OPEN = "외부 시스템 장애로 인해 호출이 일시 차단되었습니다. (target=%s)"
    const val EXTERNAL_API_CLIENT_ERROR = "외부 시스템 요청 처리 중 클라이언트 오류가 발생했습니다. (target=%s, status=%s, code=%s, reason=%s)"
    const val EXTERNAL_API_SERVER_ERROR = "외부 시스템 내부 오류가 발생했습니다. (target=%s, status=%s, code=%s, reason=%s)"
    const val EXTERNAL_API_EMPTY_RESPONSE = "외부 시스템으로부터 빈 응답을 수신했습니다. (target=%s, uri=%s)"

    const val INVALID_BLANK_PROPERTY = "HTTP 클라이언트 설정값(%s)은 비어있을 수 없습니다."
    const val INVALID_TIMEOUT_PROPERTY = "HTTP 클라이언트 타임아웃 설정값(%s)은 0보다 커야 합니다."
}