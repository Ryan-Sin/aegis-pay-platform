package io.aegis.platform.core.exception

open class BusinessException(
    val errorCode: ErrorCode,
    vararg args: Any?,
    override val cause: Throwable? = null
): RuntimeException(null, cause) {
    override val message: String = ErrorMessage.format(errorCode.message, *args)
}