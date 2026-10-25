package io.aegis.services.gateway.exception

object GatewayErrorMessage {
    const val MISSING_AUTH_TOKEN = "인증 토큰(Authorization Bearer)이 누락되었거나 형식이 올바르지 않습니다."
    const val INVALID_AUTH_TOKEN = "유효하지 않은 인증 토큰입니다."
    const val EXPIRED_AUTH_TOKEN = "만료된 인증 토큰입니다."
    const val REVOKED_AUTH_TOKEN = "폐기되었거나 로그아웃된 인증 토큰입니다."
    const val JWKS_FETCH_FAILED = "인증 서버로부터 JWKS 공개키 목록을 가져오지 못했습니다."
    const val TOKEN_STORE_UNAVAILABLE = "토큰 상태 저장소를 일시적으로 사용할 수 없습니다."

    const val REASON_MISSING_BEARER_TOKEN = "Bearer 토큰 누락 또는 형식 오류"
    const val REASON_TOKEN_REVOKED = "블랙리스트에 등록된 jti"
    const val REASON_TOKEN_EXPIRED = "exp 클레임 누락 또는 만료"
    const val REASON_JWT_PARSE_FAILED = "JWT 파싱 실패"
    const val REASON_UNSUPPORTED_ALGORITHM = "지원하지 않는 서명 알고리즘"
    const val REASON_MISSING_KID_HEADER = "kid 헤더 누락"
    const val REASON_PUBLIC_KEY_NOT_FOUND = "일치하는 서명 공개키를 찾을 수 없습니다"
    const val REASON_INVALID_SIGNATURE = "JWT 서명 불일치"
    const val REASON_CLAIMS_PARSE_FAILED = "JWT 클레임 파싱 실패"
    const val REASON_MISSING_SUB_CLAIM = "sub(terminalId) 클레임 누락"
    const val REASON_MISSING_STORE_ID_CLAIM = "storeId 클레임 누락"
    const val REASON_MISSING_JTI_CLAIM = "jti 클레임 누락"
}
