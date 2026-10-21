package io.aegis.platform.core.exception

object ErrorMessage {
    const val MISSING_REQUIRED_HEADER = "필수 요청 헤더(%s)가 누락되었습니다."
    const val INVALID_REQUEST_BODY = "요청값 검증에 실패했습니다."
    const val BAD_REQUEST = "잘못된 요청입니다."
    const val SERVLET_REQUEST_NOT_FOUND = "HttpServletRequest 컨텍스트를 찾을 수 없습니다."
    const val INTERNAL_SERVER_ERROR = "서버 내부 오류가 발생했습니다."

    fun format(template: String, vararg args: Any?): String {
        val placeholderCount = Regex("%s").findAll(template).count()

        /** %도 없고 인자도 없을 때 -> 쓸데없는 포맷팅 연산 없이 즉시 원본 변환! **/
        if (placeholderCount == 0 && args.isEmpty()) return template

        /** %s 개수와 인자 개수가 안 맞을 때 (0개, 부족, 초과) -> 정보 유실 없이 폴백 반환 **/
        if (placeholderCount != args.size) {
            return "$template (args=${args.joinToString()})"
        }

        /** 개수가 맞을 때 -> 정상 치환(null 인자 포함) **/
        return runCatching {
            template.format(*args)
        }.getOrElse {
            "$template (args=${args.joinToString()})"
        }
    }
}