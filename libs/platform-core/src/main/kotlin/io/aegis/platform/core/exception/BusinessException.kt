package io.aegis.platform.core.exception

/**
 * @property message 클라이언트 응답에 노출되는 문구 (errorCode.message + args 포맷팅)
 * @property detail 운영 로그 전용 내부 원인. 응답 body에는 절대 포함하지 않는다.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    vararg args: Any?,
    val detail: String? = null,
    override val cause: Throwable? = null
): RuntimeException(null, cause) {
    override val message: String = ErrorMessage.format(errorCode.message, *args)
}
