package io.aegis.platform.core.exception

object ErrorMessage {
    const val MISSING_REQUIRED_HEADER = "필수 요청 헤더(%s)가 누락되었습니다."
    const val INVALID_REQUEST_BODY = "요청값 검증에 실패했습니다."
    const val BAD_REQUEST = "잘못된 요청입니다."
    const val SERVLET_REQUEST_NOT_FOUND = "HttpServletRequest 컨텍스트를 찾을 수 없습니다."
    const val INTERNAL_SERVER_ERROR = "서버 내부 오류가 발생했습니다."

    fun format(template: String, vararg args: Any?): String =
        if (args.isEmpty()) template else template.format(*args)
}