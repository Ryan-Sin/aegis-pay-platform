package io.aegis.services.auth.application.exception

object AuthErrorMessage {
    const val INVALID_CHALLENGE = "인증 요청이 만료되었거나 이미 사용되었습니다. 다시 요청해 주세요."
    const val TERMINAL_AUTHENTICATION_FAILED = "단말 인증에 실패했습니다."
    const val CHALLENGE_STORE_UNAVAILABLE = "인증 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."
    const val TOKEN_SIGNING_UNAVAILABLE = "토큰을 발급할 수 없습니다. 잠시 후 다시 시도해 주세요."

    const val REASON_KMS_SIGN_FAILED = "KMS Sign 호출 실패"
    const val REASON_CHALLENGE_NOT_FOUND = "nonce 없음(만료 또는 재사용)"
    const val REASON_CHALLENGE_TERMINAL_MISMATCH = "nonce를 발급받은 단말과 요청 단말이 다름"
    const val REASON_TERMINAL_NOT_FOUND = "등록되지 않은 단말"
    const val REASON_TERMINAL_NOT_ACTIVE = "활성 상태가 아닌 단말"
    const val REASON_INVALID_PUBLIC_KEY = "저장된 단말 공개키를 읽을 수 없음"
    const val REASON_INVALID_SIGNATURE = "challenge 서명 검증 실패"
    const val REASON_CHALLENGE_STORE_FAILED = "challenge 저장소(Redis) 호출 실패"
}
